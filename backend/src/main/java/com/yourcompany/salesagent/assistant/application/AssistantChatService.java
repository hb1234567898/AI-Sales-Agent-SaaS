package com.yourcompany.salesagent.assistant.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.ai.application.AiModelService;
import com.yourcompany.salesagent.ai.application.AiModelNotConfiguredException;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.agent.api.AgentRunResponse;
import com.yourcompany.salesagent.agent.application.SalesFollowUpAgentService;
import com.yourcompany.salesagent.approval.api.ApprovalDecisionRequest;
import com.yourcompany.salesagent.approval.application.ApprovalService;
import com.yourcompany.salesagent.assistant.api.AssistantChatRequest;
import com.yourcompany.salesagent.assistant.api.AssistantChatResponse;
import com.yourcompany.salesagent.assistant.api.AssistantChatResponse.AssistantToolTrace;
import com.yourcompany.salesagent.assistant.api.AssistantConversationResponse;
import com.yourcompany.salesagent.assistant.api.AssistantMessageResponse;
import com.yourcompany.salesagent.assistant.infrastructure.AssistantConversationMapper;
import com.yourcompany.salesagent.assistant.infrastructure.AssistantConversationRow;
import com.yourcompany.salesagent.assistant.infrastructure.AssistantMessageRow;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.customer.api.CustomerResponse;
import com.yourcompany.salesagent.customer.api.CustomerUpsertRequest;
import com.yourcompany.salesagent.customer.application.CustomerService;
import com.yourcompany.salesagent.customer.application.CustomerNotFoundException;
import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;
import com.yourcompany.salesagent.followup.application.FollowUpService;
import com.yourcompany.salesagent.interaction.api.ChatImportRequest;
import com.yourcompany.salesagent.interaction.application.InteractionService;
import com.yourcompany.salesagent.interaction.domain.ChatPlatform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantChatService {

	private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	private static final Pattern CREATE_CUSTOMER_PATTERN = Pattern.compile("(新增|创建|新建)客户[：:\\s]*(?<customer>[^，,：:\\n]{2,80})");
	private static final Pattern CREATE_AND_IMPORT_PATTERN = Pattern.compile("(新增|创建|新建)客户[：:\\s]*(?<customer>[^，,：:\\n]{2,80}).*?聊天(?:记录)?[：:](?<content>[\\s\\S]+)");
	private static final Pattern IMPORT_PATTERN = Pattern.compile("给\\s*(?<customer>[^，,：:\\n]{2,40})\\s*(导入|添加|记录).*?聊天(?:记录)?[：:](?<content>[\\s\\S]+)");
	private static final Pattern IMPORT_ALT_PATTERN = Pattern.compile("导入\\s*(?<customer>[^，,：:\\n]{2,40})(?:的)?聊天(?:记录)?[：:](?<content>[\\s\\S]+)");
	private static final TypeReference<List<AssistantToolTrace>> TOOL_TRACE_LIST_TYPE = new TypeReference<>() {
	};

	private final AssistantConversationMapper conversationMapper;
	private final CustomerService customerService;
	private final InteractionService interactionService;
	private final SalesFollowUpAgentService agentService;
	private final ApprovalService approvalService;
	private final FollowUpService followUpService;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final AiModelService aiModelService;
	private final QwenModelClient modelClient;
	private final TransactionTemplate transactions;

	public AssistantChatService(
			AssistantConversationMapper conversationMapper,
			CustomerService customerService,
			InteractionService interactionService,
			SalesFollowUpAgentService agentService,
			ApprovalService approvalService,
			FollowUpService followUpService,
			ObjectMapper objectMapper,
			Clock clock,
			AiModelService aiModelService,
			QwenModelClient modelClient,
			PlatformTransactionManager transactionManager) {
		this.conversationMapper = conversationMapper;
		this.customerService = customerService;
		this.interactionService = interactionService;
		this.agentService = agentService;
		this.approvalService = approvalService;
		this.followUpService = followUpService;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.aiModelService = aiModelService;
		this.modelClient = modelClient;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** 在请求线程校验会话并提交用户消息，不让长时间模型请求占住数据库事务。 */
	public UUID beginStream(AuthPrincipal principal, AssistantChatRequest request) {
		return transactions.execute(status -> {
			var conversation = resolveConversation(principal, request.conversationId(), request.channel(), request.message().strip());
			conversationMapper.insertMessage(UUID.randomUUID(), principal.organizationId(), conversation.id(),
					"USER", request.message().strip(), null, "[]", Map.of(), clock.instant());
			return conversation.id();
		});
	}

	/** 工具完成即发事件；模型增量即发事件；done 仅在最终消息提交后发送。 */
	public void streamChat(AuthPrincipal principal, UUID conversationId, String message, BiConsumer<String, Object> events) {
		var content = new StringBuilder();
		var traces = new ArrayList<AssistantToolTrace>() {
			@Override
			public boolean add(AssistantToolTrace trace) {
				events.accept("tool", trace);
				removeIf(previous -> previous.name().equals(trace.name()) && previous.status().equals("RUNNING"));
				return super.add(trace);
			}
		};
		String summary = "";
		Map<String, Object> data = new LinkedHashMap<>();
		String failure = null;
		try {
			events.accept("progress", Map.of("text", "正在识别业务指令并执行工具；分析客户时需要等待模型返回。"));
			var result = route(principal, message.strip(), traces);
			summary = result.reasoningSummary();
			data.putAll(result.data());
			events.accept("summary", Map.of("text", summary));
			events.accept("result", result);
			try {
				var configuration = aiModelService.requireRuntimeConfiguration(principal.organizationId());
				events.accept("progress", Map.of("text", "业务执行已结束，模型正在生成回答。"));
				modelClient.streamAssistantReply(configuration, message, objectMapper.writeValueAsString(result))
						.timeout(Duration.ofSeconds(45))
						.doOnNext(delta -> {
							content.append(delta);
							events.accept("delta", Map.of("text", delta));
						}).blockLast(Duration.ofMinutes(2));
				if (content.isEmpty()) throw new IllegalStateException("Empty model response");
			}
			catch (AiModelNotConfiguredException exception) {
				// 没有模型时仍支持业务工具，明确返回原始结果，不模拟打字效果。
				events.accept("progress", Map.of("text", "未配置模型，直接展示业务结果。"));
				content.append(result.content());
				events.accept("delta", Map.of("text", result.content()));
			}
			catch (RuntimeException exception) {
				failure = "模型回答生成中断，业务操作不会重试。请以工具结果为准。";
				var fallback = "\n\n" + failure + "\n" + result.content();
				content.append(fallback);
				events.accept("delta", Map.of("text", fallback));
			}
		}
		catch (RuntimeException exception) {
			var unfinished = traces.stream().filter(trace -> trace.status().equals("RUNNING")).toList();
			unfinished.forEach(trace -> traces.add(new AssistantToolTrace(trace.name(), "FAILED", "执行未完成，请核对业务记录")));
			failure = exception instanceof AssistantWorkflowException ? exception.getMessage()
					: "业务执行未完成，请检查客户、审批或运行记录后再决定是否重试。";
			content.append(failure);
			events.accept("delta", Map.of("text", failure));
		}
		data.put("streamStatus", failure == null ? "COMPLETED" : "INTERRUPTED");
		var response = new AssistantChatResponse(conversationId, UUID.randomUUID(), "assistant", content.toString(),
				summary, List.copyOf(traces), data, clock.instant());
		transactions.executeWithoutResult(status -> {
			conversationMapper.insertMessage(response.messageId(), principal.organizationId(), conversationId,
					"ASSISTANT", response.content(), response.reasoningSummary(), toJson(response.toolTraces()), data, response.createdAt());
			conversationMapper.touchConversation(principal.organizationId(), conversationId, response.createdAt());
		});
		if (failure != null) events.accept("error", Map.of("message", failure));
		events.accept("done", response);
	}

	@Transactional
	public AssistantChatResponse chat(AuthPrincipal principal, AssistantChatRequest request) {
		var rawMessage = request.message();
		var message = rawMessage == null ? "" : rawMessage.strip();
		if (!StringUtils.hasText(message)) {
			throw new AssistantWorkflowException("请输入要自动化处理的业务指令");
		}
		var conversation = resolveConversation(principal, request.conversationId(), request.channel(), message);
		var userMessageTime = clock.instant();
		conversationMapper.insertMessage(
				UUID.randomUUID(),
				principal.organizationId(),
				conversation.id(),
				"USER",
				message,
				null,
				"[]",
				Map.of(),
				userMessageTime);

		var response = route(principal, message);
		var assistantMessageId = UUID.randomUUID();
		conversationMapper.insertMessage(
				assistantMessageId,
				principal.organizationId(),
				conversation.id(),
				"ASSISTANT",
				response.content(),
				response.reasoningSummary(),
				toJson(response.toolTraces()),
				response.data(),
				response.createdAt());
		conversationMapper.touchConversation(principal.organizationId(), conversation.id(), response.createdAt());
		return new AssistantChatResponse(
				conversation.id(),
				assistantMessageId,
				response.role(),
				response.content(),
				response.reasoningSummary(),
				response.toolTraces(),
				response.data(),
				response.createdAt());
	}

	@Transactional(readOnly = true)
	public IPage<AssistantConversationResponse> findConversations(AuthPrincipal principal, int page, int size) {
		var conversations = conversationMapper.selectConversations(
				Page.of(page + 1L, size),
				principal.organizationId(),
				principal.memberId());
		var records = conversations.getRecords().stream().map(this::toConversationResponse).toList();
		return new Page<AssistantConversationResponse>(conversations.getCurrent(), conversations.getSize(), conversations.getTotal())
				.setRecords(records);
	}

	@Transactional(readOnly = true)
	public IPage<AssistantMessageResponse> findMessages(AuthPrincipal principal, UUID conversationId, int page, int size) {
		ensureConversation(principal, conversationId);
		var messages = conversationMapper.selectMessages(
				Page.of(page + 1L, size),
				principal.organizationId(),
				conversationId);
		var records = messages.getRecords().stream().map(this::toMessageResponse).toList();
		return new Page<AssistantMessageResponse>(messages.getCurrent(), messages.getSize(), messages.getTotal())
				.setRecords(records);
	}

	private AssistantChatResponse route(AuthPrincipal principal, String message) {
		return route(principal, message, new ArrayList<>());
	}

	private AssistantChatResponse route(AuthPrincipal principal, String message, List<AssistantToolTrace> traces) {
		var normalized = message.toLowerCase();

		if (looksLikeChatImport(message)) {
			return importChatAndRunAgent(principal, message, traces);
		}
		if (looksLikeCustomerCreate(message)) {
			return createCustomer(message, traces);
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
			return reply("我还缺客户名或聊天内容。可以这样发：\n\n给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。", "识别为聊天导入意图，但缺少客户名或聊天正文，因此没有调用业务写入工具。", traces, Map.of("intent", "CHAT_IMPORT"));
		}
		var customer = resolveOrCreateCustomer(command.customerName(), looksLikeCustomerCreate(message), traces);
		traces.add(new AssistantToolTrace("interaction.chat_import", "RUNNING", "正在保存聊天记录"));
		var interaction = interactionService.importChat(
				customer.id(),
				new ChatImportRequest(ChatPlatform.OTHER, clock.instant(), "MCP 助手导入聊天", command.content().strip(), null));
		traces.add(new AssistantToolTrace("interaction.chat_import", "SUCCEEDED", "已导入聊天记录：" + interaction.id()));
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "RUNNING", "正在分析客户并生成待审批建议"));
		var run = agentService.runNow(principal, new AgentRunCreateRequest(5, 30, List.of(customer.id())));
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "SUCCEEDED", "已触发客户跟进建议 Agent：" + run.id()));
		return reply(
				"已完成自动化处理：我先找到客户「" + customer.name() + "」，导入聊天记录，然后只针对这个客户跑了一次跟进建议 Agent。"
						+ nextRunHint(run),
				"识别聊天导入指令 → 匹配或创建客户 → 写入互动记录 → 触发客户跟进建议 Agent → 返回待审批数量。",
				traces,
				Map.of(
						"customerId", customer.id(),
						"interactionId", interaction.id(),
						"agentRunId", run.id(),
						"agentRunStatus", run.status(),
						"pendingApprovalCount", run.pendingApprovalCount()));
	}

	private AssistantChatResponse runAgent(AuthPrincipal principal, String message, List<AssistantToolTrace> traces) {
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "RUNNING", "正在读取互动并调用模型分析客户"));
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
		return reply("Agent 已运行完成。" + nextRunHint(run), "识别 Agent 运行指令 → 判断是否指定客户 → 触发客户跟进建议 Agent → 汇总运行结果。", traces, Map.of(
				"agentRunId", run.id(),
				"agentRunStatus", run.status(),
				"processedCount", run.processedCount(),
				"pendingApprovalCount", run.pendingApprovalCount()));
	}

	private AssistantChatResponse createCustomer(String message, List<AssistantToolTrace> traces) {
		var draft = parseCustomerDraft(message);
		if (!StringUtils.hasText(draft.name())) {
			return reply("我还缺客户名称。可以这样发：\n\n新增客户：沐光医疗，行业：医疗科技，联系人：苏恬，电话：13800000007，邮箱：su@example.com", "识别为新增客户意图，但缺少客户名称，因此没有创建客户。", traces, Map.of("intent", "CUSTOMER_CREATE"));
		}
		traces.add(new AssistantToolTrace("customer.create", "RUNNING", "正在创建客户档案"));
		var customer = createCustomerFromDraft(draft);
		traces.add(new AssistantToolTrace("customer.create", "SUCCEEDED", "已新增客户：" + customer.name()));
		return reply("客户「" + customer.name() + "」已经创建完成。你可以继续说：给" + customer.name() + "导入聊天：……，我会自动导入并运行 Agent。", "识别新增客户指令 → 抽取客户名称、行业和联系人字段 → 创建客户主档和主要联系人。", traces, Map.of(
				"customerId", customer.id(),
				"customerName", customer.name()));
	}

	private AssistantChatResponse listPendingApprovals(List<AssistantToolTrace> traces) {
		traces.add(new AssistantToolTrace("approval.list", "RUNNING", "正在查询待审批建议"));
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
		return reply(content, "识别查询审批指令 → 读取待审批列表 → 返回最近 10 条建议摘要。", traces, Map.of("approvals", approvals));
	}

	private AssistantChatResponse approveById(AuthPrincipal principal, String message, List<AssistantToolTrace> traces) {
		var matcher = UUID_PATTERN.matcher(message);
		if (!matcher.find()) {
			return reply("为了避免误审批，请带上完整审批 ID，例如：批准 00000000-0000-0000-0000-000000000000", "识别审批通过意图，但缺少完整审批 ID，因此没有执行审批动作。", traces, Map.of("intent", "APPROVE"));
		}
		var approvalId = UUID.fromString(matcher.group());
		traces.add(new AssistantToolTrace("approval.approve", "RUNNING", "正在审批并执行对应动作"));
		var approval = approvalService.approve(principal, approvalId, new ApprovalDecisionRequest(null, "由 MCP 助手聊天入口批准"));
		traces.add(new AssistantToolTrace("approval.approve", "SUCCEEDED", "已批准审批：" + approval.id()));
		return reply("已审批通过「" + approval.customerName() + "」的建议，系统会继续执行对应工具并刷新 Agent 运行状态。", "识别审批通过指令 → 校验审批 ID → 调用审批通过接口 → 返回审批后的业务状态。", traces, Map.of(
				"approvalId", approval.id(),
				"status", approval.status(),
				"customerName", approval.customerName()));
	}

	private AssistantChatResponse listFollowUps(List<AssistantToolTrace> traces) {
		traces.add(new AssistantToolTrace("follow_up.list", "RUNNING", "正在查询跟进任务"));
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
		return reply(tasks.isEmpty() ? "现在没有开放中的跟进任务。" : "当前有 " + page.getTotal() + " 条跟进任务，我列出了最近 10 条。", "识别查询跟进任务指令 → 读取开放和历史跟进任务 → 返回最近 10 条任务摘要。", traces, Map.of("followUps", tasks));
	}

	private AssistantChatResponse help(List<AssistantToolTrace> traces) {
		traces.add(new AssistantToolTrace("assistant.router", "SUCCEEDED", "已返回可用自动化指令"));
		return reply("""
				我现在可以帮你自动化这些事：

				1. 新增客户
				示例：新增客户：沐光医疗，行业：医疗科技，联系人：苏恬，电话：13800000007，邮箱：su@example.com

				2. 新增客户、导入聊天并自动跑 Agent
				示例：新增客户云岚科技并导入聊天：客户说下周想看报价，需要私有化方案。

				3. 给已有客户导入聊天并自动跑 Agent
				示例：给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。

				4. 直接运行客户跟进 Agent
				示例：运行 Agent 分析云岚科技

				5. 查看待审批建议
				示例：查看待审批

				6. 显式批准某条审批
				示例：批准 <审批ID>

				7. 查看跟进任务
				示例：查看跟进任务
				""", "没有匹配到明确业务指令，因此返回当前支持的工具调用方式和示例。", traces, Map.of("intent", "HELP"));
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

	private CustomerResponse resolveOrCreateCustomer(String keyword, boolean allowCreate, List<AssistantToolTrace> traces) {
		try {
			var customer = resolveCustomer(keyword);
			traces.add(new AssistantToolTrace("customer.search", "SUCCEEDED", "已匹配客户：" + customer.name()));
			return customer;
		}
		catch (CustomerNotFoundException | AssistantWorkflowException exception) {
			if (!allowCreate) {
				throw exception;
			}
			var customer = createCustomerFromDraft(parseCustomerDraft("新增客户：" + keyword));
			traces.add(new AssistantToolTrace("customer.create", "SUCCEEDED", "未找到现有客户，已新建：" + customer.name()));
			return customer;
		}
	}

	private CustomerResponse createCustomerFromDraft(CustomerDraft draft) {
		return customerService.createCustomer(new CustomerUpsertRequest(
				draft.name(),
				draft.website(),
				draft.industry(),
				draft.employeeRange(),
				CustomerStage.LEAD,
				CustomerStatus.ACTIVE,
				CustomerSource.CHAT,
				null,
				null,
				null,
				null,
				null,
				draft.contactName(),
				draft.contactEmail(),
				draft.contactPhone()));
	}

	private static CustomerDraft parseCustomerDraft(String message) {
		var matcher = CREATE_CUSTOMER_PATTERN.matcher(message);
		var name = matcher.find() ? cleanCustomerName(matcher.group("customer")) : "";
		return new CustomerDraft(
				name,
				extractField(message, "网站", "官网"),
				extractField(message, "行业"),
				extractField(message, "规模", "人数"),
				extractField(message, "联系人", "客户联系人"),
				extractField(message, "邮箱", "邮件"),
				extractField(message, "电话", "手机号", "手机"));
	}

	private static boolean looksLikeChatImport(String message) {
		return containsAny(message, "导入", "添加", "记录") && containsAny(message, "聊天", "微信", "whatsapp");
	}

	private static boolean looksLikeCustomerCreate(String message) {
		return containsAny(message, "新增客户", "创建客户", "新建客户");
	}

	private static ImportCommand parseImportCommand(String message) {
		var createMatcher = CREATE_AND_IMPORT_PATTERN.matcher(message);
		if (createMatcher.find()) {
			return new ImportCommand(cleanCustomerName(createMatcher.group("customer")), createMatcher.group("content").strip());
		}
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

	private static String extractField(String message, String... labels) {
		for (var label : labels) {
			var pattern = Pattern.compile(label + "[：:\\s]*(?<value>[^，,；;\\n]+)");
			var matcher = pattern.matcher(message);
			if (matcher.find()) {
				var value = matcher.group("value").strip();
				return StringUtils.hasText(value) ? value : null;
			}
		}
		return null;
	}

	private static String cleanCustomerName(String value) {
		if (value == null) {
			return "";
		}
		var cleaned = value
				.replace("并导入聊天", "")
				.replace("然后导入聊天", "")
				.replace("导入聊天", "")
				.strip();
		return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
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

	private AssistantConversationRow resolveConversation(AuthPrincipal principal, UUID conversationId, String rawChannel, String firstMessage) {
		if (conversationId != null) {
			return ensureConversation(principal, conversationId);
		}
		var id = UUID.randomUUID();
		var now = clock.instant();
		conversationMapper.insertConversation(
				id,
				principal.organizationId(),
				principal.userId(),
				principal.memberId(),
				titleFrom(firstMessage),
				normalizeChannel(rawChannel),
				now);
		return conversationMapper.selectConversation(principal.organizationId(), id);
	}

	private AssistantConversationRow ensureConversation(AuthPrincipal principal, UUID conversationId) {
		var conversation = conversationMapper.selectConversation(principal.organizationId(), conversationId);
		if (conversation == null || !principal.memberId().equals(conversation.memberId())) {
			throw new AssistantWorkflowException("没有找到这条 MCP 助手会话，请刷新会话列表后重试");
		}
		return conversation;
	}

	private AssistantConversationResponse toConversationResponse(AssistantConversationRow row) {
		return new AssistantConversationResponse(
				row.id(),
				row.title(),
				row.channel(),
				row.status(),
				row.lastMessageAt(),
				row.createdAt(),
				row.updatedAt());
	}

	private AssistantMessageResponse toMessageResponse(AssistantMessageRow row) {
		return new AssistantMessageResponse(
				row.id(),
				row.conversationId(),
				row.role().toLowerCase(),
				row.content(),
				row.reasoningSummary(),
				parseToolTraces(row.toolTracesJson()),
				row.data(),
				row.createdAt());
	}

	private String toJson(List<AssistantToolTrace> traces) {
		return objectMapper.writeValueAsString(traces == null ? List.of() : traces);
	}

	private List<AssistantToolTrace> parseToolTraces(String json) {
		if (!StringUtils.hasText(json)) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, TOOL_TRACE_LIST_TYPE);
		}
		catch (RuntimeException exception) {
			return List.of(new AssistantToolTrace("assistant.history.parse", "FAILED", "历史工具轨迹解析失败"));
		}
	}

	private AssistantChatResponse reply(String content, String reasoningSummary, List<AssistantToolTrace> traces, Map<String, Object> data) {
		return new AssistantChatResponse(null, null, "assistant", content, reasoningSummary,
				traces.stream().filter(trace -> !trace.status().equals("RUNNING")).toList(), data, clock.instant());
	}

	private static String normalizeChannel(String channel) {
		return "DESKTOP".equals(channel) ? "DESKTOP" : "WEB";
	}

	private static String titleFrom(String message) {
		var text = message == null ? "新的自动化会话" : message.strip().replaceAll("\\s+", " ");
		if (!StringUtils.hasText(text)) {
			return "新的自动化会话";
		}
		return text.length() <= 36 ? text : text.substring(0, 36) + "…";
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

	private record CustomerDraft(
			String name,
			String website,
			String industry,
			String employeeRange,
			String contactName,
			String contactEmail,
			String contactPhone) {
	}
}
