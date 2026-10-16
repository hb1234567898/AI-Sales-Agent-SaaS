package com.yourcompany.salesagent.assistant.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.ai.application.AiModelService;
import com.yourcompany.salesagent.ai.application.AiModelNotConfiguredException;
import com.yourcompany.salesagent.ai.application.ModelCallRecordRequest;
import com.yourcompany.salesagent.ai.application.ModelCallRecorder;
import com.yourcompany.salesagent.ai.application.ModelUsage;
import com.yourcompany.salesagent.ai.infrastructure.AiOrchestrationClient;
import com.yourcompany.salesagent.ai.infrastructure.AiOrchestrationClient.AssistantPlan;
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
import com.yourcompany.salesagent.file.application.FileStorageException;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.followup.application.FollowUpService;
import com.yourcompany.salesagent.interaction.api.ChatImportRequest;
import com.yourcompany.salesagent.interaction.application.InteractionService;
import com.yourcompany.salesagent.interaction.domain.ChatPlatform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantChatService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AssistantChatService.class);
	private static final TypeReference<List<AssistantToolTrace>> TOOL_TRACE_LIST_TYPE = new TypeReference<>() {
	};
	private static final List<Map<String, Object>> ASSISTANT_TOOLS = List.of(
			assistantTool("customer.create", "新增客户和主要联系人。参数键：name、industry、employeeRange、website、contactName、contactEmail、contactPhone", "LOW", List.of("name")),
			assistantTool("interaction.chat_import", "给客户导入聊天记录并运行跟进 Agent。参数键：customerName、chatContent、createCustomer", "LOW", List.of("customerName", "chatContent")),
			assistantTool("email.send.prepare", "校验附件并创建待审批的客户邮件。参数键：customerName、documentType", "HIGH", List.of("customerName", "documentType")),
			assistantTool("approval.list", "查看当前组织的待审批建议", "LOW", List.of()),
			assistantTool("approval.approve", "批准指定审批并执行已批准动作。参数键：approvalId", "HIGH", List.of("approvalId")),
			assistantTool("agent.sales_follow_up.run", "运行客户跟进分析 Agent。参数键：customerName，可省略", "LOW", List.of()),
			assistantTool("follow_up.list", "查看跟进任务", "LOW", List.of()));

	private final AssistantConversationMapper conversationMapper;
	private final CustomerService customerService;
	private final InteractionService interactionService;
	private final SalesFollowUpAgentService agentService;
	private final ApprovalService approvalService;
	private final FollowUpService followUpService;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final AiModelService aiModelService;
	private final AiOrchestrationClient modelClient;
	private final ModelCallRecorder modelCallRecorder;
	private final FileStorageService fileStorageService;
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
			AiOrchestrationClient modelClient,
			ModelCallRecorder modelCallRecorder,
			FileStorageService fileStorageService,
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
		this.modelCallRecorder = modelCallRecorder;
		this.fileStorageService = fileStorageService;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** 在请求线程校验会话并提交用户消息，不让长时间模型请求占住数据库事务。 */
	public UUID beginStream(AuthPrincipal principal, AssistantChatRequest request) {
		aiModelService.requireAvailableTokenQuota(principal.organizationId(), principal.memberId());
		return transactions.execute(status -> {
			var conversation = resolveConversation(principal, request.conversationId(), request.channel(), request.message().strip());
			var attachments = resolveAttachmentContext(principal, request.attachmentIds());
			conversationMapper.insertMessage(UUID.randomUUID(), principal.organizationId(), conversation.id(),
					"USER", request.message().strip(), null, "[]", attachments.data(), clock.instant());
			return conversation.id();
		});
	}

	/** 工具完成即发事件；模型增量即发事件；done 仅在最终消息提交后发送。 */
	public void streamChat(AuthPrincipal principal, UUID conversationId, String message, List<UUID> attachmentIds, BiConsumer<String, Object> events) {
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
			var attachments = resolveAttachmentContext(principal, attachmentIds);
			if (!attachments.isEmpty()) {
				data.putAll(attachments.data());
			}
			events.accept("progress", Map.of("text", "正在识别业务指令并执行工具；分析客户时需要等待模型返回。"));
			var result = route(principal, conversationId, message.strip(), traces, attachments);
			summary = result.reasoningSummary();
			data.putAll(result.data());
			events.accept("summary", Map.of("text", summary));
			events.accept("result", result);
			try {
				var configuration = aiModelService.requireRuntimeConfiguration(principal.organizationId(), principal.memberId());
				var verifiedResult = objectMapper.writeValueAsString(result);
				var startedAt = clock.instant();
				var streamUsage = new AtomicReference<ModelUsage>();
				var providerRequestId = new AtomicReference<String>();
				var responseModel = new AtomicReference<String>();
				events.accept("progress", Map.of("text", "业务执行已结束，模型正在生成回答。"));
				modelClient.streamAssistantReplyWithUsage(configuration, message, verifiedResult)
						.timeout(Duration.ofSeconds(45))
						.doOnNext(chunk -> {
							if (chunk.usage() != null) streamUsage.set(chunk.usage());
							if (StringUtils.hasText(chunk.providerRequestId())) providerRequestId.set(chunk.providerRequestId());
							if (StringUtils.hasText(chunk.model())) responseModel.set(chunk.model());
							if (StringUtils.hasText(chunk.text())) {
								content.append(chunk.text());
								events.accept("delta", Map.of("text", chunk.text()));
							}
						}).blockLast(Duration.ofMinutes(2));
				if (content.isEmpty()) throw new IllegalStateException("Empty model response");
				var completedAt = clock.instant();
				var usage = streamUsage.get();
				if (usage != null) {
					data.put("modelUsage", usageSnapshot(usage));
				}
				recordAssistantModelCall(
						principal,
						configuration.model(),
						responseModel.get(),
						providerRequestId.get(),
						usage,
						startedAt,
						completedAt,
						message,
						verifiedResult,
						content.toString());
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
		aiModelService.requireAvailableTokenQuota(principal.organizationId(), principal.memberId());
		var rawMessage = request.message();
		var message = rawMessage == null ? "" : rawMessage.strip();
		if (!StringUtils.hasText(message)) {
			throw new AssistantWorkflowException("请输入要自动化处理的业务指令");
		}
		var conversation = resolveConversation(principal, request.conversationId(), request.channel(), message);
		var attachments = resolveAttachmentContext(principal, request.attachmentIds());
		var userMessageTime = clock.instant();
		conversationMapper.insertMessage(
				UUID.randomUUID(),
				principal.organizationId(),
				conversation.id(),
				"USER",
				message,
				null,
				"[]",
				attachments.data(),
				userMessageTime);

		var response = route(principal, conversation.id(), message, attachments);
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
		return route(principal, null, message, new ArrayList<>(), AttachmentContext.empty());
	}

	private AssistantChatResponse route(AuthPrincipal principal, UUID conversationId, String message) {
		return route(principal, conversationId, message, new ArrayList<>(), AttachmentContext.empty());
	}

	private AssistantChatResponse route(AuthPrincipal principal, UUID conversationId, String message, AttachmentContext attachments) {
		return route(principal, conversationId, message, new ArrayList<>(), attachments);
	}

	private AssistantChatResponse route(AuthPrincipal principal, UUID conversationId, String message, List<AssistantToolTrace> traces, AttachmentContext attachments) {
		com.yourcompany.salesagent.ai.application.AiModelRuntimeConfiguration configuration;
		try {
			configuration = aiModelService.requireRuntimeConfiguration(principal.organizationId(), principal.memberId());
		}
		catch (AiModelNotConfiguredException exception) {
			throw new AssistantWorkflowException("请先在设置中配置并启用 AI 模型，再使用 AI 助手。");
		}
		var context = new LinkedHashMap<String, Object>();
		context.put("attachmentCount", attachments.ids().size());
		context.put("attachments", attachments.preview());
		var startedAt = clock.instant();
		var plan = modelClient.planAssistantAction(configuration, message, context, ASSISTANT_TOOLS);
		var completedAt = clock.instant();
		recordPlannerModelCall(principal, configuration.model(), plan, startedAt, completedAt, message);
		traces.add(new AssistantToolTrace("assistant.langgraph.plan", "SUCCEEDED",
				"已识别意图：" + plan.intent() + (StringUtils.hasText(plan.toolName()) ? "，候选工具：" + plan.toolName() : "")));

		if (!plan.validationErrors().isEmpty()) {
			return reply(
					StringUtils.hasText(plan.response()) ? plan.response() : "还缺少执行所需的信息，请补充后重试。",
					"LangGraph 已生成候选计划，但 Java 执行前校验未通过：" + String.join("；", plan.validationErrors()),
					traces,
					planData(plan));
		}

		var arguments = plan.arguments();
		AssistantChatResponse result = switch (plan.toolName() == null ? "" : plan.toolName()) {
			case "customer.create" -> createCustomer(customerDraft(arguments), traces);
			case "interaction.chat_import" -> importChatAndRunAgent(
					principal, conversationId, importCommand(arguments), traces, attachments);
			case "email.send.prepare" -> proposeDocumentEmail(
					principal, conversationId, documentSendCommand(arguments), traces, attachments);
			case "approval.list" -> listPendingApprovals(traces);
			case "approval.approve" -> approvePlanned(principal, message, stringArgument(arguments, "approvalId"), traces);
			case "agent.sales_follow_up.run" -> runAgent(
					principal, conversationId, stringArgument(arguments, "customerName"), traces, attachments);
			case "follow_up.list" -> listFollowUps(traces);
			case "" -> reply(
					StringUtils.hasText(plan.response()) ? plan.response() : "请补充你希望完成的销售工作。",
					"LangGraph 将本次请求识别为 " + plan.intent() + "，未提出业务工具调用。",
					traces,
					planData(plan));
			default -> throw new AssistantWorkflowException("AI 计划引用了未授权工具，已阻止执行: " + plan.toolName());
		};
		var resultData = new LinkedHashMap<>(result.data());
		resultData.putAll(planData(plan));
		return new AssistantChatResponse(result.conversationId(), result.messageId(), result.role(), result.content(),
				result.reasoningSummary(), result.toolTraces(), resultData, result.createdAt());
	}

	private AssistantChatResponse proposeDocumentEmail(AuthPrincipal principal, UUID conversationId, DocumentSendCommand command,
			List<AssistantToolTrace> traces, AttachmentContext attachments) {
		if (command == null || !StringUtils.hasText(command.customerName())) {
			return reply("我还缺准确的客户名称。可以这样发：给和成科技发送方案。",
					"识别为文件邮件发送意图，但未解析到客户名称，因此没有创建发送审批。", traces,
					Map.of("intent", "SEND_DOCUMENT_EMAIL"));
		}
		if (attachments.isEmpty()) {
			traces.add(new AssistantToolTrace("email.send.prepare", "FAILED", "发送" + command.documentType() + "需要先选择附件"));
			return reply(
					"发送" + command.documentType() + "需要带上文件。请先点击输入框旁的附件按钮选择方案文件，再发送同一句指令；文件会在你点击发送后上传，不会在选择时提前上传。",
					"识别到给客户发送文件的意图，但本次消息没有附件。为避免发错或漏发文件，未创建审批，也未发送邮件。",
					traces,
					Map.of("intent", "SEND_DOCUMENT_EMAIL", "customerName", command.customerName(), "attachmentRequired", true));
		}

		var customer = resolveCustomer(command.customerName());
		traces.add(new AssistantToolTrace("customer.search", "SUCCEEDED", "已匹配客户：" + customer.name()));
		traces.add(new AssistantToolTrace("email.send.prepare", "RUNNING", "正在生成带附件的邮件发送审批"));
		var proposal = agentService.proposeDocumentEmailFromMcpAssistant(
				principal, customer, conversationId, command.documentType(), attachments.ids());
		traces.add(new AssistantToolTrace("email.send.prepare", "SUCCEEDED", "已生成待审批邮件：" + proposal.approvalId()));
		return reply(
				"已为客户「" + customer.name() + "」生成一封带 " + attachments.ids().size() + " 个附件的"
						+ command.documentType() + "邮件，当前尚未发送。请在审批中心核对收件人、主题、正文和附件，审批通过后系统才会发送。",
				"识别文件发送指令 → 校验客户与附件 → 生成邮件预览 → 创建高风险待审批动作；未直接发送邮件。",
				traces,
				Map.of(
						"customerId", customer.id(),
						"customerName", customer.name(),
						"agentRunId", proposal.runId(),
						"approvalId", proposal.approvalId(),
						"to", proposal.to(),
						"subject", proposal.subject(),
						"attachments", attachments.preview()));
	}

	private AssistantChatResponse importChatAndRunAgent(AuthPrincipal principal, UUID conversationId, ImportCommand command, List<AssistantToolTrace> traces, AttachmentContext attachments) {
		if (command == null || !StringUtils.hasText(command.customerName()) || !StringUtils.hasText(command.content())) {
			return reply("我还缺客户名或聊天内容。可以这样发：\n\n给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。", "识别为聊天导入意图，但缺少客户名或聊天正文，因此没有调用业务写入工具。", traces, Map.of("intent", "CHAT_IMPORT"));
		}
		var customer = resolveOrCreateCustomer(command.customerName(), command.createCustomer(), traces);
		traces.add(new AssistantToolTrace("interaction.chat_import", "RUNNING", "正在保存聊天记录"));
		var interaction = interactionService.importChat(
				customer.id(),
				new ChatImportRequest(ChatPlatform.OTHER, clock.instant(), "MCP 助手导入聊天", command.content().strip(), null));
		traces.add(new AssistantToolTrace("interaction.chat_import", "SUCCEEDED", "已导入聊天记录：" + interaction.id()));
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "RUNNING", "正在分析客户并生成待审批建议"));
		var run = agentService.runFromMcpAssistant(principal, new AgentRunCreateRequest(5, 30, List.of(customer.id())), conversationId, attachments.ids());
		var approvals = pendingApprovalsForRun(run.id());
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "SUCCEEDED", "已触发客户跟进建议 Agent：" + run.id()));
		return reply(
				"已完成自动化处理：我先找到客户「" + customer.name() + "」，导入聊天记录，然后只针对这个客户跑了一次跟进建议 Agent。"
						+ nextRunHint(run) + attachmentHint(attachments),
				"识别聊天导入指令 → 匹配或创建客户 → 写入互动记录 → 触发客户跟进建议 Agent → 返回待审批数量。",
				traces,
				Map.of(
						"customerId", customer.id(),
						"interactionId", interaction.id(),
						"agentRunId", run.id(),
						"agentRunStatus", run.status(),
						"pendingApprovalCount", run.pendingApprovalCount(),
						"approvals", approvals,
						"attachments", attachments.preview()));
	}

	private AssistantChatResponse runAgent(AuthPrincipal principal, UUID conversationId, String customerName, List<AssistantToolTrace> traces, AttachmentContext attachments) {
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "RUNNING", "正在读取互动并调用模型分析客户"));
		AgentRunResponse run;
		if (StringUtils.hasText(customerName)) {
			var customer = resolveCustomer(customerName);
			traces.add(new AssistantToolTrace("customer.search", "SUCCEEDED", "已匹配客户：" + customer.name()));
			run = agentService.runFromMcpAssistant(principal, new AgentRunCreateRequest(5, 30, List.of(customer.id())), conversationId, attachments.ids());
		}
		else {
			if (!attachments.isEmpty()) {
				return reply("我已收到附件，但为了避免把文件带给错误客户，请在指令里写清楚客户名，例如：运行 Agent 分析云岚科技。",
						"识别到 Agent 运行意图和附件，但未指定客户；附件类动作必须绑定明确客户，未执行 Agent。",
						traces,
						attachments.data());
			}
			run = agentService.runFromMcpAssistant(principal, new AgentRunCreateRequest(5, 30, null), conversationId);
		}
		traces.add(new AssistantToolTrace("agent.sales_follow_up.run", "SUCCEEDED", "已触发客户跟进建议 Agent：" + run.id()));
		var approvals = pendingApprovalsForRun(run.id());
		return reply("Agent 已运行完成。" + nextRunHint(run) + attachmentHint(attachments), "识别 Agent 运行指令 → 判断是否指定客户 → 触发客户跟进建议 Agent → 汇总运行结果。", traces, Map.of(
				"agentRunId", run.id(),
				"agentRunStatus", run.status(),
				"processedCount", run.processedCount(),
				"pendingApprovalCount", run.pendingApprovalCount(),
				"approvals", approvals,
				"attachments", attachments.preview()));
	}

	private List<Map<String, Object>> pendingApprovalsForRun(UUID runId) {
		return approvalService.findApprovals("PENDING", 0, 50).getRecords().stream()
				.filter(approval -> runId.equals(approval.runId()))
				.map(approval -> compactMap(
						"id", approval.id(),
						"customerName", approval.customerName(),
						"actionType", approval.actionType(),
						"riskLevel", approval.riskLevel(),
						"version", approval.version()))
				.toList();
	}

	private AssistantChatResponse createCustomer(CustomerDraft draft, List<AssistantToolTrace> traces) {
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
						"version", approval.version(),
						"reason", approval.reason()))
				.toList();
		var content = approvals.isEmpty()
				? "现在没有待审批建议。"
				: "当前有 " + page.getTotal() + " 条待审批建议。你可以去审批中心处理，也可以输入：批准 <审批ID>。";
		return reply(content, "识别查询审批指令 → 读取待审批列表 → 返回最近 10 条建议摘要。", traces, Map.of("approvals", approvals));
	}

	private AssistantChatResponse approveById(AuthPrincipal principal, UUID approvalId, List<AssistantToolTrace> traces) {
		traces.add(new AssistantToolTrace("approval.approve", "RUNNING", "正在审批并执行对应动作"));
		var approval = approvalService.approve(principal, approvalId, new ApprovalDecisionRequest(null, "由 MCP 助手聊天入口批准"));
		var actionStatus = approval.actionStatus();
		var failureMessage = approval.failureMessage();
		var actionMessage = "FAILED".equals(actionStatus)
				? "已批准，但工具执行失败：" + (failureMessage == null ? "请查看审批记录" : failureMessage)
				: "SUCCEEDED".equals(actionStatus)
						? "已批准并执行成功"
						: "已批准，工具正在执行";
		traces.add(new AssistantToolTrace("approval.approve", "FAILED".equals(actionStatus) ? "FAILED" : "SUCCEEDED", actionMessage));
		return reply("已审批通过「" + approval.customerName() + "」的建议。" + actionMessage + "。", "识别审批通过指令 → 校验审批 ID → 调用审批通过接口 → 返回审批后的业务状态。", traces, Map.of(
				"approvalId", approval.id(),
				"status", approval.status(),
				"actionStatus", actionStatus,
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

				8. 给客户发送方案、报价或合同附件
				示例：选择文件后输入：给和成科技发送方案
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
			var customer = createCustomerFromDraft(new CustomerDraft(keyword, null, null, null, null, null, null));
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

	private static CustomerDraft customerDraft(Map<String, Object> arguments) {
		return new CustomerDraft(
				stringArgument(arguments, "name"),
				stringArgument(arguments, "website"),
				stringArgument(arguments, "industry"),
				stringArgument(arguments, "employeeRange"),
				stringArgument(arguments, "contactName"),
				stringArgument(arguments, "contactEmail"),
				stringArgument(arguments, "contactPhone"));
	}

	private static ImportCommand importCommand(Map<String, Object> arguments) {
		return new ImportCommand(
				stringArgument(arguments, "customerName"),
				stringArgument(arguments, "chatContent"),
				booleanArgument(arguments, "createCustomer"));
	}

	private static DocumentSendCommand documentSendCommand(Map<String, Object> arguments) {
		return new DocumentSendCommand(
				stringArgument(arguments, "customerName"),
				stringArgument(arguments, "documentType"));
	}

	private static Map<String, Object> assistantTool(String name, String description, String riskLevel, List<String> requiredFields) {
		return Map.of(
				"name", name,
				"description", description,
				"risk_level", riskLevel,
				"required_fields", requiredFields);
	}

	private static String stringArgument(Map<String, Object> arguments, String name) {
		var value = arguments == null ? null : arguments.get(name);
		return value == null ? null : value.toString().strip();
	}

	private static boolean booleanArgument(Map<String, Object> arguments, String name) {
		var value = arguments == null ? null : arguments.get(name);
		return value instanceof Boolean booleanValue
				? booleanValue
				: value != null && Boolean.parseBoolean(value.toString());
	}

	private AssistantChatResponse approvePlanned(
			AuthPrincipal principal, String originalMessage, String approvalId, List<AssistantToolTrace> traces) {
		if ((!originalMessage.contains("审批通过") && !originalMessage.contains("批准")) || !StringUtils.hasText(approvalId)) {
			return reply(
					"审批属于高风险动作，请明确输入“批准 <审批ID>”。",
					"LangGraph 提出了审批动作，但 Java 未检测到明确确认语句或完整审批 ID，因此阻止执行。",
					traces,
					Map.of("intent", "APPROVE", "requiresApproval", true));
		}
		try {
			return approveById(principal, UUID.fromString(approvalId), traces);
		}
		catch (IllegalArgumentException exception) {
			return reply("审批 ID 格式不正确，请提供完整 UUID。",
					"Java 在执行高风险审批前校验审批 ID 失败，因此阻止执行。", traces,
					Map.of("intent", "APPROVE", "requiresApproval", true));
		}
	}

	private static Map<String, Object> planData(AssistantPlan plan) {
		return compactMap(
				"assistantPlanIntent", plan.intent(),
				"assistantPlanTool", plan.toolName(),
				"assistantPlanConfidence", plan.confidence(),
				"assistantPlanRequiresApproval", plan.requiresApproval(),
				"assistantPlanValidationErrors", plan.validationErrors(),
				"assistantPlanUsage", plan.usage() == null ? null : usageSnapshot(plan.usage()));
	}

	private void recordPlannerModelCall(
			AuthPrincipal principal,
			String configuredModel,
			AssistantPlan plan,
			java.time.Instant startedAt,
			java.time.Instant completedAt,
			String userMessage) {
		try {
			modelCallRecorder.record(new ModelCallRecordRequest(
					principal.organizationId(),
					principal.memberId(),
					null,
					null,
					null,
					"PLANNING",
					"QWEN",
					StringUtils.hasText(plan.model()) ? plan.model() : configuredModel,
					plan.providerRequestId(),
					"mcp-assistant-langgraph-plan-v1",
					null,
					"SUCCEEDED",
					1,
					plan.usage(),
					Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli()),
					sha256(userMessage),
					sha256(plan.toString()),
					Map.of("channel", "MCP_ASSISTANT", "userMessageChars", userMessage.length()),
					compactMap("intent", plan.intent(), "tool", plan.toolName(), "confidence", plan.confidence()),
					null,
					null,
					startedAt,
					completedAt));
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Failed to record LangGraph planner model usage", exception);
		}
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

	private AttachmentContext resolveAttachmentContext(AuthPrincipal principal, List<UUID> attachmentIds) {
		if (CollectionUtils.isEmpty(attachmentIds)) {
			return AttachmentContext.empty();
		}
		var ids = attachmentIds.stream()
				.filter(Objects::nonNull)
				.distinct()
				.limit(10)
				.toList();
		if (ids.isEmpty()) {
			return AttachmentContext.empty();
		}
		try {
			var files = ids.stream()
					.map(id -> fileStorageService.requireActive(principal.organizationId(), id))
					.toList();
			return new AttachmentContext(ids, fileStorageService.preview(files));
		}
		catch (FileStorageException exception) {
			throw new AssistantWorkflowException(exception.getMessage(), exception);
		}
	}

	private static String attachmentHint(AttachmentContext attachments) {
		if (attachments.isEmpty()) {
			return "";
		}
		return " 本次已带上 " + attachments.ids().size() + " 个附件；如果后续建议是发送邮件，审批和发送时会优先使用这些附件。";
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

	private void recordAssistantModelCall(
			AuthPrincipal principal,
			String configuredModel,
			String responseModel,
			String providerRequestId,
			ModelUsage usage,
			java.time.Instant startedAt,
			java.time.Instant completedAt,
			String userMessage,
			String verifiedResult,
			String output) {
		try {
			modelCallRecorder.record(new ModelCallRecordRequest(
					principal.organizationId(),
					principal.memberId(),
					null,
					null,
					null,
					"SUMMARY",
					"QWEN",
					StringUtils.hasText(responseModel) ? responseModel : configuredModel,
					providerRequestId,
					"mcp-assistant-summary-v1",
					null,
					"SUCCEEDED",
					1,
					usage,
					Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli()),
					sha256(userMessage + "\n\n" + verifiedResult),
					sha256(output == null ? "" : output),
					Map.of(
							"channel", "MCP_ASSISTANT",
							"userMessageChars", userMessage == null ? 0 : userMessage.length(),
							"verifiedResultChars", verifiedResult == null ? 0 : verifiedResult.length()),
					Map.of("outputChars", output == null ? 0 : output.length()),
					null,
					null,
					startedAt,
					completedAt));
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Failed to record Qwen model usage for MCP assistant summary", exception);
		}
	}

	private static Map<String, Object> usageSnapshot(ModelUsage usage) {
		var snapshot = new LinkedHashMap<String, Object>();
		snapshot.put("promptTokens", usage.promptTokens());
		snapshot.put("completionTokens", usage.completionTokens());
		snapshot.put("totalTokens", usage.totalTokens());
		snapshot.put("cachedInputTokens", usage.cachedInputTokens());
		return snapshot;
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

	private static String sha256(String value) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 算法不可用", exception);
		}
	}

	private record AttachmentContext(List<UUID> ids, List<Map<String, Object>> preview) {

		static AttachmentContext empty() {
			return new AttachmentContext(List.of(), List.of());
		}

		boolean isEmpty() {
			return ids.isEmpty();
		}

		Map<String, Object> data() {
			if (isEmpty()) {
				return Map.of();
			}
			return Map.of("attachments", preview);
		}
	}

	private record ImportCommand(String customerName, String content, boolean createCustomer) {
	}

	private record DocumentSendCommand(String customerName, String documentType) {
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
