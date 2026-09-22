package com.yourcompany.salesagent.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.agent.api.AgentRunResponse;
import com.yourcompany.salesagent.agent.api.AgentStepResponse;
import com.yourcompany.salesagent.agent.infrastructure.AgentCandidateRow;
import com.yourcompany.salesagent.agent.infrastructure.AgentRunRow;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.customer.api.CustomerResponse;
import com.yourcompany.salesagent.file.application.FileStorageException;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.interaction.api.ChatAnalysisResponse;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;

@Service
public class SalesFollowUpAgentService {

	private static final String AGENT_TYPE = "SALES_FOLLOW_UP";
	private static final String SCORE_VERSION = "sales-follow-up-v1";
	private static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private final AgentWorkflowMapper mapper;
	private final ChatAnalysisService chatAnalysisService;
	private final FileStorageService fileStorageService;
	private final Clock clock;
	private final UUID organizationId;

	public SalesFollowUpAgentService(
			AgentWorkflowMapper mapper,
			ChatAnalysisService chatAnalysisService,
			FileStorageService fileStorageService,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.mapper = mapper;
		this.chatAnalysisService = chatAnalysisService;
		this.fileStorageService = fileStorageService;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(readOnly = true)
	public IPage<AgentRunResponse> findRuns(int page, int size) {
		var rows = mapper.selectRuns(Page.of(page + 1L, size), organizationId);
		return new Page<AgentRunResponse>(rows.getCurrent(), rows.getSize(), rows.getTotal())
				.setRecords(rows.getRecords().stream().map(AgentRunResponse::from).toList());
	}

	@Transactional(readOnly = true)
	public AgentRunResponse findRun(UUID runId) {
		return AgentRunResponse.from(requireRun(runId));
	}

	@Transactional(readOnly = true)
	public IPage<AgentStepResponse> findSteps(UUID runId, int page, int size) {
		requireRun(runId);
		var rows = mapper.selectSteps(Page.of(page + 1L, size), organizationId, runId);
		return new Page<AgentStepResponse>(rows.getCurrent(), rows.getSize(), rows.getTotal())
				.setRecords(rows.getRecords().stream().map(AgentStepResponse::from).toList());
	}

	@Transactional
	public AgentRunResponse runNow(AuthPrincipal principal, AgentRunCreateRequest request) {
		return runNow(principal, request, "MANUAL", Map.of());
	}

	@Transactional
	public AgentRunResponse runFromMcpAssistant(AuthPrincipal principal, AgentRunCreateRequest request, UUID conversationId) {
		return runFromMcpAssistant(principal, request, conversationId, List.of());
	}

	@Transactional
	public AgentRunResponse runFromMcpAssistant(AuthPrincipal principal, AgentRunCreateRequest request, UUID conversationId, List<UUID> attachmentIds) {
		var triggerContext = conversationId == null
				? new LinkedHashMap<String, Object>()
				: new LinkedHashMap<String, Object>(Map.of("conversationId", conversationId.toString()));
		if (!CollectionUtils.isEmpty(attachmentIds)) {
			triggerContext.put("attachmentIds", attachmentIds.stream().map(UUID::toString).toList());
			triggerContext.put("attachmentSource", "MCP_ASSISTANT_UPLOAD");
		}
		return runNow(principal, request, "MCP_ASSISTANT", triggerContext);
	}

	@Transactional
	public McpDocumentEmailResult proposeDocumentEmailFromMcpAssistant(
			AuthPrincipal principal,
			CustomerResponse customer,
			UUID conversationId,
			String documentType,
			List<UUID> attachmentIds) {
		if (CollectionUtils.isEmpty(attachmentIds)) {
			throw new AgentWorkflowException("发送" + documentType + "需要至少一个附件");
		}
		var now = clock.instant();
		var configId = ensureDefaultConfig(now);
		var businessDate = LocalDate.ofInstant(now, DEFAULT_BUSINESS_ZONE);
		var runId = UUID.randomUUID();
		var actionRequestId = UUID.randomUUID();
		var approvalId = UUID.randomUUID();
		var triggerContext = new LinkedHashMap<String, Object>();
		if (conversationId != null) {
			triggerContext.put("conversationId", conversationId.toString());
		}
		triggerContext.put("customerIds", List.of(customer.id().toString()));
		triggerContext.put("attachmentIds", attachmentIds.stream().map(UUID::toString).toList());
		triggerContext.put("attachmentSource", "MCP_ASSISTANT_UPLOAD");
		triggerContext.put("intent", "SEND_DOCUMENT_EMAIL");

		mapper.insertRun(runId, organizationId, configId, principal.memberId(), "MCP_ASSISTANT", "RUNNING",
				businessDate, null, triggerContext, inputSnapshot(principal, "MCP_ASSISTANT", triggerContext), now, now);
		insertStep(runId, customer.id(), 1, "SYSTEM", "准备客户文件发送邮件", "SUCCEEDED",
				Map.of("documentType", documentType), Map.of("attachmentCount", attachmentIds.size()), now, now, null);

		var files = resolveMcpAttachments(triggerContext, customer.id());
		var attachmentPreview = fileStorageService.preview(files);
		var to = customer.primaryContact() == null ? null : customer.primaryContact().email();
		if (!StringUtils.hasText(to)) {
			to = mapper.selectNotificationEmail(organizationId, customer.id(), customer.ownerMemberId());
		}
		if (!StringUtils.hasText(to)) {
			throw new AgentWorkflowException("客户「" + customer.name() + "」没有可用邮箱，请先补充主要联系人邮箱");
		}
		var normalizedType = StringUtils.hasText(documentType) ? documentType.strip() : "文件";
		var subject = customer.name() + normalizedType;
		var contactName = customer.primaryContact() == null ? null : customer.primaryContact().name();
		var greeting = StringUtils.hasText(contactName) ? contactName.strip() + "，您好：" : "您好：";
		var body = greeting + "\n\n请查收本次沟通的" + normalizedType + "，如有问题或需要调整，欢迎随时联系。\n\n谢谢。";
		var email = new LinkedHashMap<String, Object>();
		email.put("to", to);
		email.put("subject", subject);
		email.put("body", body);
		var payload = new LinkedHashMap<String, Object>();
		payload.put("customerId", customer.id().toString());
		payload.put("customerName", customer.name());
		payload.put("email", email);
		payload.put("to", to);
		payload.put("subject", subject);
		payload.put("body", body);
		payload.put("attachmentRequired", true);
		payload.put("attachments", attachmentPreview);
		var contentHash = sha256(payload.toString());
		var reason = "用户通过 MCP 助手请求给客户发送" + normalizedType + "，需人工核对后发送";
		var actionStepId = insertStep(runId, customer.id(), 2, "ACTION_PROPOSED", "生成待审批文件发送邮件", "SUCCEEDED",
				Map.of("attachmentCount", attachmentIds.size()),
				Map.of("to", to, "subject", subject, "actionRequestId", actionRequestId.toString()), now, now, null);
		mapper.insertActionRequest(
				actionRequestId, organizationId, runId, actionStepId, customer.id(), principal.memberId(),
				"SEND_EMAIL", "HIGH", "AWAITING_APPROVAL", "email.send", "v1", true, "REQUIRE_APPROVAL",
				reason, payload, contentHash,
				preview(customer.name(), "发送" + normalizedType, 100, payload, "SEND_EMAIL"),
				"mcp-document-email:" + runId + ":" + customer.id(), now.plus(Duration.ofDays(7)));
		mapper.insertApproval(approvalId, organizationId, actionRequestId, principal.memberId(), reason, contentHash,
				now, now.plus(Duration.ofDays(7)));
		insertStep(runId, customer.id(), 3, "APPROVAL_WAIT", "等待人工确认邮件发送", "SUCCEEDED",
				Map.of("actionRequestId", actionRequestId.toString()), Map.of("approvalId", approvalId.toString()),
				now, now, null);
		var summary = new LinkedHashMap<String, Object>();
		summary.put("message", "已生成待审批文件发送邮件");
		summary.put("pendingApprovals", 1);
		summary.put("agentType", AGENT_TYPE);
		summary.put("triggerType", "MCP_ASSISTANT");
		mapper.completeRun(runId, organizationId, "WAITING_APPROVAL", 1, 1, 1, 0, 0, 1, summary, null, now);
		return new McpDocumentEmailResult(runId, approvalId, actionRequestId, to, subject);
	}

	public record McpDocumentEmailResult(UUID runId, UUID approvalId, UUID actionRequestId, String to, String subject) {
	}

	private AgentRunResponse runNow(AuthPrincipal principal, AgentRunCreateRequest request, String triggerType, Map<String, Object> triggerContext) {
		var now = clock.instant();
		var configId = ensureDefaultConfig(now);
		var maxCustomers = request.maxCustomers() == null ? 5 : request.maxCustomers();
		var recentDays = request.recentDays() == null ? 30 : request.recentDays();
		var businessDate = LocalDate.ofInstant(now, DEFAULT_BUSINESS_ZONE);
		var runId = UUID.randomUUID();
		var scope = scope(request, maxCustomers, recentDays, businessDate, triggerType, triggerContext);

		mapper.insertRun(runId, organizationId, configId, principal.memberId(), triggerType, "RUNNING",
				businessDate, null, scope, inputSnapshot(principal, triggerType, triggerContext), now, now);
		var sequence = new Sequence();
		insertStep(runId, null, sequence.next(), "SYSTEM", "启动客户跟进建议 Agent", "SUCCEEDED",
				Map.of("triggerType", triggerType), Map.of("message", "开始扫描最近客户互动"), now, now, null);

		var candidates = mapper.selectCandidates(
				organizationId,
				now.minus(Duration.ofDays(recentDays)),
				CollectionUtils.isEmpty(request.customerIds()) ? null : request.customerIds(),
				maxCustomers);
		insertStep(runId, null, sequence.next(), "LOAD_DATA", "读取最近客户互动", "SUCCEEDED",
				Map.of("recentDays", recentDays, "maxCustomers", maxCustomers),
				Map.of("candidateCount", candidates.size()), now, now, null);

		var succeeded = 0;
		var failed = 0;
		var pendingApprovals = 0;
		for (var candidate : candidates) {
			try {
				var analysis = chatAnalysisService.analyze(
						candidate.getCustomerId(), candidate.getInteractionId(), principal.memberId());
				var priority = priority(analysis);
				var dueAt = now.plus(Duration.ofDays("HIGH".equals(analysis.intentLevel()) ? 1 : 3));
				var actionPlan = resolveActionPlan(analysis);
				var payload = followUpPayload(candidate, analysis, priority, dueAt, actionPlan.suggestedNextAction());
				if ("SEND_EMAIL".equals(actionPlan.actionType())) {
					var emailTo = mapper.selectNotificationEmail(organizationId, candidate.getCustomerId(), candidate.getOwnerMemberId());
					var emailSubject = "跟进提醒：" + candidate.getCustomerName();
					var emailBody = buildEmailBody(candidate.getCustomerName(), analysis);
					var explicitAttachments = resolveMcpAttachments(triggerContext, candidate.getCustomerId());
					var attachmentRequired = emailAttachmentRequired(actionPlan.suggestedNextAction()) || !explicitAttachments.isEmpty();
					var attachments = !explicitAttachments.isEmpty()
							? fileStorageService.preview(explicitAttachments)
							: attachmentRequired
							? fileStorageService.preview(fileStorageService.findRecentForCustomer(organizationId, candidate.getCustomerId(), 3))
							: List.<Map<String, Object>>of();
					var email = new LinkedHashMap<String, Object>();
					email.put("to", emailTo);
					email.put("subject", emailSubject);
					email.put("body", emailBody);
					payload.put("email", email);
					payload.put("to", emailTo);
					payload.put("subject", emailSubject);
					payload.put("body", emailBody);
					payload.put("attachmentRequired", attachmentRequired);
					payload.put("attachments", attachments);
				}
				var contentHash = sha256(payload.toString());
				var actionRequestId = UUID.randomUUID();
				var approvalId = UUID.randomUUID();
				var stepId = insertStep(runId, candidate.getCustomerId(), sequence.next(), "ACTION_PROPOSED",
						"生成待审批跟进建议", "SUCCEEDED",
						Map.of("interactionId", candidate.getInteractionId().toString()),
						Map.of("intentScore", analysis.intentScore(), "priority", priority, "action", actionPlan.suggestedNextAction()),
						now, now, null);
				mapper.insertActionRequest(
						actionRequestId,
						organizationId,
						runId,
						stepId,
						candidate.getCustomerId(),
						principal.memberId(),
						actionPlan.actionType(),
						riskLevel(analysis),
						"AWAITING_APPROVAL",
						actionPlan.toolName(),
						actionPlan.toolVersion(),
						actionPlan.requiresApproval(),
						actionPlan.policyDecision(),
						reason(analysis),
						payload,
						contentHash,
						preview(candidate.getCustomerName(), actionPlan.suggestedNextAction(), priority, payload, actionPlan.actionType()),
						"agent-follow-up:" + runId + ":" + candidate.getCustomerId(),
						now.plus(Duration.ofDays(7)));
				mapper.insertApproval(
						approvalId,
						organizationId,
						actionRequestId,
						principal.memberId(),
						reason(analysis),
						contentHash,
						now,
						now.plus(Duration.ofDays(7)));
				insertStep(runId, candidate.getCustomerId(), sequence.next(), "APPROVAL_WAIT",
						"等待人工审批", "SUCCEEDED",
						Map.of("actionRequestId", actionRequestId.toString()),
						Map.of("approvalId", approvalId.toString()), now, now, null);
				succeeded++;
				pendingApprovals++;
			}
			catch (RuntimeException exception) {
				failed++;
				insertStep(runId, candidate.getCustomerId(), sequence.next(), "ERROR",
						"客户分析失败", "FAILED",
						Map.of("interactionId", candidate.getInteractionId().toString()),
						Map.of(), now, now, exception.getMessage());
			}
		}

		var status = failed > 0 && succeeded > 0 ? "PARTIALLY_COMPLETED"
				: failed > 0 ? "FAILED"
				: pendingApprovals > 0 ? "WAITING_APPROVAL" : "COMPLETED";
		var summary = new LinkedHashMap<String, Object>();
		summary.put("message", pendingApprovals > 0 ? "已生成待审批跟进建议" : "没有需要审批的建议");
		summary.put("pendingApprovals", pendingApprovals);
		summary.put("agentType", AGENT_TYPE);
		summary.put("triggerType", triggerType);
		mapper.completeRun(runId, organizationId, status, candidates.size(), candidates.size(), succeeded,
				0, failed, pendingApprovals, summary, failed > 0 ? "部分客户分析失败" : null, now);
		return findRun(runId);
	}

	private UUID ensureDefaultConfig(java.time.Instant now) {
		mapper.insertDefaultConfigIfAbsent(organizationId, now);
		var configId = mapper.selectDefaultConfigId(organizationId);
		if (configId == null) {
			throw new AgentWorkflowException("未能初始化客户跟进 Agent 配置");
		}
		return configId;
	}

	private AgentRunRow requireRun(UUID runId) {
		var run = mapper.selectRun(organizationId, runId);
		if (run == null) {
			throw new AgentWorkflowException("Agent 运行记录不存在");
		}
		return run;
	}

	private UUID insertStep(UUID runId, UUID customerId, long sequenceNo, String stepType, String name, String status,
			Map<String, Object> input, Map<String, Object> output, java.time.Instant startedAt,
			java.time.Instant completedAt, String errorMessage) {
		var stepId = UUID.randomUUID();
		mapper.insertStep(stepId, organizationId, runId, customerId, sequenceNo, stepType, name, status,
				input, output, errorMessage, startedAt, completedAt, 0L);
		return stepId;
	}

	private static Map<String, Object> scope(
			AgentRunCreateRequest request,
			int maxCustomers,
			int recentDays,
			LocalDate businessDate,
			String triggerType,
			Map<String, Object> triggerContext) {
		var scope = new LinkedHashMap<String, Object>();
		scope.put("businessDate", businessDate.toString());
		scope.put("recentDays", recentDays);
		scope.put("maxCustomers", maxCustomers);
		scope.put("triggerType", triggerType);
		scope.putAll(triggerContext);
		if (!CollectionUtils.isEmpty(request.customerIds())) {
			scope.put("customerIds", request.customerIds().stream().map(UUID::toString).toList());
		}
		return scope;
	}

	private static Map<String, Object> inputSnapshot(AuthPrincipal principal, String triggerType, Map<String, Object> triggerContext) {
		var snapshot = new LinkedHashMap<String, Object>();
		snapshot.put("requestedBy", principal.email());
		snapshot.put("triggerType", triggerType);
		snapshot.putAll(triggerContext);
		return snapshot;
	}

	private List<UploadedFile> resolveMcpAttachments(Map<String, Object> triggerContext, UUID customerId) {
		var ids = attachmentIds(triggerContext);
		if (ids.isEmpty()) {
			return List.of();
		}
		try {
			var files = new ArrayList<UploadedFile>();
			for (var id : ids) {
				var file = fileStorageService.requireActive(organizationId, id);
				if (file.getCustomerId() != null && !file.getCustomerId().equals(customerId)) {
					throw new AgentWorkflowException("附件不属于当前客户，已阻止发送邮件附件: " + file.getOriginalFilename());
				}
				files.add(file);
			}
			return files;
		}
		catch (FileStorageException exception) {
			throw new AgentWorkflowException("附件不存在或已删除，无法生成邮件发送建议: " + exception.getMessage());
		}
	}

	private static List<UUID> attachmentIds(Map<String, Object> triggerContext) {
		var value = triggerContext.get("attachmentIds");
		if (!(value instanceof Iterable<?> items)) {
			return List.of();
		}
		var ids = new ArrayList<UUID>();
		for (var item : items) {
			try {
				if (item instanceof UUID id) {
					ids.add(id);
				}
				else if (item != null && StringUtils.hasText(String.valueOf(item))) {
					ids.add(UUID.fromString(String.valueOf(item).strip()));
				}
			}
			catch (IllegalArgumentException ignored) {
				// Ignore malformed values from trigger context; upload validation happens earlier.
			}
		}
		return ids.stream().distinct().toList();
	}

	private static Map<String, Object> followUpPayload(AgentCandidateRow candidate, ChatAnalysisResponse analysis,
			int priority, java.time.Instant dueAt, String action) {
		var payload = new LinkedHashMap<String, Object>();
		payload.put("customerId", candidate.getCustomerId().toString());
		payload.put("customerName", candidate.getCustomerName());
		payload.put("ownerMemberId", candidate.getOwnerMemberId() == null ? null : candidate.getOwnerMemberId().toString());
		payload.put("dueAt", dueAt.toString());
		payload.put("priority", priority);
		payload.put("aiScore", analysis.intentScore());
		payload.put("scoreVersion", SCORE_VERSION);
		payload.put("intentLevel", analysis.intentLevel());
		payload.put("riskLevel", riskLevel(analysis));
		payload.put("reason", reason(analysis));
		payload.put("recommendedActionType", "ADD_NOTE");
		payload.put("recommendedAction", Map.of("title", action, "source", "AI_AGENT"));
		payload.put("evidence", analysis.evidence());
		return payload;
	}

	private static Map<String, Object> preview(String customerName, String action, int priority,
			Map<String, Object> payload, String actionType) {
		var preview = new LinkedHashMap<String, Object>();
		preview.put("customerName", customerName);
		preview.put("action", action);
		preview.put("priority", priority);
		if ("SEND_EMAIL".equals(actionType)) {
			preview.put("to", payload.get("to"));
			preview.put("subject", payload.get("subject"));
			preview.put("body", payload.get("body"));
			preview.put("attachmentRequired", payload.get("attachmentRequired"));
			preview.put("attachments", payload.get("attachments"));
		}
		return preview;
	}

	private static boolean emailAttachmentRequired(String action) {
		if (!StringUtils.hasText(action)) {
			return false;
		}
		return List.of("附件", "文件", "报价", "报价单", "方案", "合同", "PDF", "pdf").stream()
				.anyMatch(action::contains);
	}

	private static int priority(ChatAnalysisResponse analysis) {
		var score = analysis.intentScore();
		if ("HIGH".equals(analysis.intentLevel())) score += 10;
		if (!analysis.risks().isEmpty()) score += 5;
		return Math.max(0, Math.min(100, score));
	}

	private static String riskLevel(ChatAnalysisResponse analysis) {
		if (!analysis.risks().isEmpty()) return "MEDIUM";
		return "HIGH".equals(analysis.intentLevel()) ? "MEDIUM" : "LOW";
	}

	private static String recommendedAction(ChatAnalysisResponse analysis) {
		if (StringUtils.hasText(analysis.suggestedNextAction())) return analysis.suggestedNextAction();
		if (!analysis.recommendedActions().isEmpty()) return analysis.recommendedActions().get(0);
		return "安排一次客户跟进，确认需求、预算和下一步时间";
	}

	private static String reason(ChatAnalysisResponse analysis) {
		return StringUtils.hasText(analysis.summary()) ? analysis.summary() : "AI 从最近客户互动中识别到需要跟进";
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

	private record ActionPlan(
			String actionType,
			String toolName,
			String toolVersion,
			boolean requiresApproval,
			String policyDecision,
			String suggestedNextAction) {
	}

	/**
	 * 把模型建议的推荐动作映射到具体的动作类型与执行工具。默认回退到创建内部跟进，
	 * 命中 SEND_EMAIL / CREATE_CRM_TASK / GENERATE_EMAIL_DRAFT 时切换到对应工具。
	 */
	private ActionPlan resolveActionPlan(ChatAnalysisResponse analysis) {
		var actions = analysis.recommendedActions();
		var next = recommendedAction(analysis);
		if (actions != null && actions.stream().anyMatch(a -> a != null && a.contains("SEND_EMAIL"))) {
			return new ActionPlan("SEND_EMAIL", "email.send", "v1", true, "REQUIRE_APPROVAL", next);
		}
		if (actions != null && actions.stream().anyMatch(a -> a != null && (a.contains("CREATE_CRM_TASK") || a.contains("CREATE_TASK")))) {
			return new ActionPlan("CREATE_CRM_TASK", "crm.task.create", "v1", true, "REQUIRE_APPROVAL", next);
		}
		if (actions != null && actions.stream().anyMatch(a -> a != null && a.contains("GENERATE_EMAIL_DRAFT"))) {
			return new ActionPlan("GENERATE_EMAIL_DRAFT", "email.draft.generate", "v1", false, "ALLOW", next);
		}
		return new ActionPlan("CREATE_INTERNAL_FOLLOW_UP", "internal.follow_up.create", "v1", true, "REQUIRE_APPROVAL", next);
	}

	private static String buildEmailBody(String customerName, ChatAnalysisResponse analysis) {
		var suggestion = analysis.suggestedNextAction() == null ? "" : analysis.suggestedNextAction();
		return "您好：\n\n关于与 " + customerName + " 的近期沟通，AI 助手的分析如下：\n"
				+ (analysis.summary() == null ? "" : analysis.summary())
				+ "\n\n建议的下一步：" + suggestion
				+ "\n\n此邮件由 AI 销售跟进助手生成，请人工确认内容后再发送。";
	}

	private static final class Sequence {
		private long value;

		long next() {
			return ++value;
		}
	}
}
