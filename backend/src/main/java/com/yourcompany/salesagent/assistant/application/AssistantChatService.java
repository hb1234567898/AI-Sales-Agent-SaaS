package com.yourcompany.salesagent.assistant.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.agent.api.AgentRunResponse;
import com.yourcompany.salesagent.agent.application.SalesFollowUpAgentService;
import com.yourcompany.salesagent.approval.api.ApprovalDecisionRequest;
import com.yourcompany.salesagent.approval.application.ApprovalService;
import com.yourcompany.salesagent.assistant.api.AssistantChatResponse;
import com.yourcompany.salesagent.assistant.api.AssistantChatResponse.AssistantToolTrace;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.customer.api.CustomerResponse;
import com.yourcompany.salesagent.customer.application.CustomerService;
import com.yourcompany.salesagent.followup.application.FollowUpService;
import com.yourcompany.salesagent.interaction.api.ChatImportRequest;
import com.yourcompany.salesagent.interaction.application.InteractionService;
import com.yourcompany.salesagent.interaction.domain.ChatPlatform;

@Service
public class AssistantChatService {

	private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	private static final Pattern IMPORT_PATTERN = Pattern.compile("给\\s*(?<customer>[^，,：:\\n]{2,40})\\s*(导入|添加|记录).*?聊天(?:记录)?[：:](?<content>[\\s\\S]+)");
	private static final Pattern IMPORT_ALT_PATTERN = Pattern.compile("导入\\s*(?<customer>[^，,：:\\n]{2,40})(?:的)?聊天(?:记录)?[：:](?<content>[\\s\\S]+)");

	private final CustomerService customerService;
	private final InteractionService interactionService;
	private final SalesFollowUpAgentService agentService;
	private final ApprovalService approvalService;
	private final FollowUpService followUpService;
	private final Clock clock;

	public AssistantChatService(
			CustomerService customerService,
			InteractionService interactionService,
			SalesFollowUpAgentService agentService,
			ApprovalService approvalService,
			FollowUpService followUpService,
			Clock clock) {
		this.customerService = customerService;
		this.interactionService = interactionService;
		this.agentService = agentService;
		this.approvalService = approvalService;
		this.followUpService = followUpService;
		this.clock = clock;
	}

	@Transactional
	public AssistantChatResponse chat(AuthPrincipal principal, String rawMessage) {
		var message = rawMessage == null ? "" : rawMessage.strip();
		if (!StringUtils.hasText(message)) {
			throw new AssistantWorkflowException("请输入要自动化处理的业务指令");
		}
		var traces = new ArrayList<AssistantToolTrace>();
		var normalized = message.toLowerCase();

		if (looksLikeChatImport(message)) {
			return importChatAndRunAgent(principal, message, traces);
		}
		if (containsAny(message, "审批通过", "批准")) {
			return approveById(principal, message, traces);
		}
		if (containsAny(message, "待审批", "审批列表", "查看审批", "有哪些审批")) {
			return listPendingApprovals(traces);
		}
		if (containsAny(normalized, "agent", "分析客户", "运行分析", "跑一下")) {
			return runAgent(principal, message, traces);
		}
		if (containsAny(message, "跟进任务", "待跟进", "查看跟进")) {
			return listFollowUps(traces);
		}
		return help(traces);
	}

	private AssistantChatResponse importChatAndRunAgent(AuthPrincipal principal, String message, List<AssistantToolTrace> traces) {
		var command = parseImportCommand(message);
		if (command == null || !StringUtils.hasText(command.customerName()) || !StringUtils.hasText(command.content())) {
			return reply("我还缺客户名或聊天内容。可以这样发：\n\n给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。", traces, Map.of("intent", "CHAT_IMPORT"));
		}
		var customer = resolveCustomer(command.customerName());
		traces.add(new AssistantToolTrace("customer.search", "SUCCEEDED", "已匹配客户：" + customer.name()));
		var interaction = interactionService.importChat(
				customer.id(),
				new ChatImportRequest(ChatPlatform.OTHER, clock.instant(), "MCP 助手导入聊天", command.content().strip(), null));
		traces.add(new AssistantToolTrace("interaction.chat_import", "SUCCEEDED", "已导入聊天记录：" + interaction.id()));
		var run = agentService.runNow(principal, new AgentRunCreateRequest(5, 30, List.of(customer.id())));
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "SUCCEEDED", "已触发客户跟进建议 Agent：" + run.id()));
		return reply(
				"已完成自动化处理：我先找到客户「" + customer.name() + "」，导入聊天记录，然后只针对这个客户跑了一次跟进建议 Agent。"
						+ nextRunHint(run),
				traces,
				Map.of(
						"customerId", customer.id(),
						"interactionId", interaction.id(),
						"agentRunId", run.id(),
						"agentRunStatus", run.status(),
						"pendingApprovalCount", run.pendingApprovalCount()));
	}

	private AssistantChatResponse runAgent(AuthPrincipal principal, String message, List<AssistantToolTrace> traces) {
		var customerName = extractCustomerName(message);
		AgentRunResponse run;
		if (StringUtils.hasText(customerName)) {
			var customer = resolveCustomer(customerName);
			traces.add(new AssistantToolTrace("customer.search", "SUCCEEDED", "已匹配客户：" + customer.name()));
			run = agentService.runNow(principal, new AgentRunCreateRequest(5, 30, List.of(customer.id())));
		}
		else {
			run = agentService.runNow(principal, new AgentRunCreateRequest(5, 30, null));
		}
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "SUCCEEDED", "已触发客户跟进建议 Agent：" + run.id()));
		return reply("Agent 已运行完成。" + nextRunHint(run), traces, Map.of(
				"agentRunId", run.id(),
				"agentRunStatus", run.status(),
				"processedCount", run.processedCount(),
				"pendingApprovalCount", run.pendingApprovalCount()));
	}

	private AssistantChatResponse listPendingApprovals(List<AssistantToolTrace> traces) {
		var page = approvalService.findApprovals("PENDING", 0, 10);
		traces.add(new AssistantToolTrace("approval.list", "SUCCEEDED", "读取待审批建议 " + page.getTotal() + " 条"));
		var approvals = page.getRecords().stream()
				.map(approval -> compactMap(
						"id", approval.id(),
						"customerName", approval.customerName(),
						"actionType", approval.actionType(),
						"riskLevel", approval.riskLevel(),
						"reason", approval.reason()))
				.toList();
		var content = approvals.isEmpty()
				? "现在没有待审批建议。"
				: "当前有 " + page.getTotal() + " 条待审批建议。你可以去审批中心处理，也可以输入：批准 <审批ID>。";
		return reply(content, traces, Map.of("approvals", approvals));
	}

	private AssistantChatResponse approveById(AuthPrincipal principal, String message, List<AssistantToolTrace> traces) {
		var matcher = UUID_PATTERN.matcher(message);
		if (!matcher.find()) {
			return reply("为了避免误审批，请带上完整审批 ID，例如：批准 00000000-0000-0000-0000-000000000000", traces, Map.of("intent", "APPROVE"));
		}
		var approvalId = UUID.fromString(matcher.group());
		var approval = approvalService.approve(principal, approvalId, new ApprovalDecisionRequest(null, "由 MCP 助手聊天入口批准"));
		traces.add(new AssistantToolTrace("approval.approve", "SUCCEEDED", "已批准审批：" + approval.id()));
		return reply("已审批通过「" + approval.customerName() + "」的建议，系统会继续执行对应工具并刷新 Agent 运行状态。", traces, Map.of(
				"approvalId", approval.id(),
				"status", approval.status(),
				"customerName", approval.customerName()));
	}

	private AssistantChatResponse listFollowUps(List<AssistantToolTrace> traces) {
		var page = followUpService.findFollowUps("ALL", 0, 10);
		traces.add(new AssistantToolTrace("follow_up.list", "SUCCEEDED", "读取跟进任务 " + page.getTotal() + " 条"));
		var tasks = page.getRecords().stream()
				.map(task -> compactMap(
						"id", task.id(),
						"customerName", task.customerName(),
						"status", task.status(),
						"priority", task.priority(),
						"reason", task.reason()))
				.toList();
		return reply(tasks.isEmpty() ? "现在没有开放中的跟进任务。" : "当前有 " + page.getTotal() + " 条跟进任务，我列出了最近 10 条。", traces, Map.of("followUps", tasks));
	}

	private AssistantChatResponse help(List<AssistantToolTrace> traces) {
		traces.add(new AssistantToolTrace("assistant.router", "SUCCEEDED", "已返回可用自动化指令"));
		return reply("""
				我现在可以帮你自动化这些事：

				1. 给客户导入聊天并自动跑 Agent
				示例：给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。

				2. 直接运行客户跟进 Agent
				示例：运行 Agent 分析云岚科技

				3. 查看待审批建议
				示例：查看待审批

				4. 显式批准某条审批
				示例：批准 <审批ID>

				5. 查看跟进任务
				示例：查看跟进任务
				""", traces, Map.of("intent", "HELP"));
	}

	private CustomerResponse resolveCustomer(String keyword) {
		var customers = customerService.findCustomers(keyword.strip(), null, null, 0, 5).getRecords();
		if (customers.isEmpty()) {
			throw new AssistantWorkflowException("没有找到客户：" + keyword);
		}
		var exact = customers.stream().filter(customer -> customer.name().equalsIgnoreCase(keyword.strip())).findFirst();
		if (exact.isPresent()) {
			return exact.get();
		}
		if (customers.size() > 1) {
			throw new AssistantWorkflowException("找到多个相似客户，请把客户名写准确一点：" + customers.stream().map(CustomerResponse::name).toList());
		}
		return customers.get(0);
	}

	private static boolean looksLikeChatImport(String message) {
		return containsAny(message, "导入", "添加", "记录") && containsAny(message, "聊天", "微信", "whatsapp");
	}

	private static ImportCommand parseImportCommand(String message) {
		var matcher = IMPORT_PATTERN.matcher(message);
		if (matcher.find()) {
			return new ImportCommand(matcher.group("customer").strip(), matcher.group("content").strip());
		}
		matcher = IMPORT_ALT_PATTERN.matcher(message);
		if (!matcher.find()) {
			return null;
		}
		return new ImportCommand(matcher.group("customer").strip(), matcher.group("content").strip());
	}

	private static String extractCustomerName(String message) {
		var cleaned = message
				.replace("运行", "")
				.replace("触发", "")
				.replace("跑一下", "")
				.replace("分析", "")
				.replace("客户", "")
				.replace("Agent", "")
				.replace("agent", "")
				.strip();
		return StringUtils.hasText(cleaned) && cleaned.length() <= 40 ? cleaned : null;
	}

	private static boolean containsAny(String text, String... values) {
		for (var value : values) {
			if (text.contains(value)) {
				return true;
			}
		}
		return false;
	}

	private static String nextRunHint(AgentRunResponse run) {
		if (run.pendingApprovalCount() > 0) {
			return " 已生成 " + run.pendingApprovalCount() + " 条待审批建议，下一步去审批中心批准后会生成跟进任务。";
		}
		if (run.processedCount() == 0) {
			return " 这次没有找到符合条件的近期聊天记录，可以先导入客户聊天再运行。";
		}
		return " 没有产生待审批建议。";
	}

	private AssistantChatResponse reply(String content, List<AssistantToolTrace> traces, Map<String, Object> data) {
		return new AssistantChatResponse("assistant", content, List.copyOf(traces), data, clock.instant());
	}

	private static Map<String, Object> compactMap(Object... values) {
		var map = new LinkedHashMap<String, Object>();
		for (var index = 0; index < values.length - 1; index += 2) {
			var key = values[index];
			var value = values[index + 1];
			if (key != null && value != null) {
				map.put(key.toString(), value);
			}
		}
		return map;
	}

	private record ImportCommand(String customerName, String content) {
	}
}
