package com.yourcompany.salesagent.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.yourcompany.salesagent.interaction.api.ChatAnalysisResponse;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;

@Service
public class SalesFollowUpAgentService {

	private static final String AGENT_TYPE = "SALES_FOLLOW_UP";
	private static final String SCORE_VERSION = "sales-follow-up-v1";
	private static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private final AgentWorkflowMapper mapper;
	private final ChatAnalysisService chatAnalysisService;
	private final Clock clock;
	private final UUID organizationId;

	public SalesFollowUpAgentService(
			AgentWorkflowMapper mapper,
			ChatAnalysisService chatAnalysisService,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.mapper = mapper;
		this.chatAnalysisService = chatAnalysisService;
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
		var now = clock.instant();
		var configId = ensureDefaultConfig(now);
		var maxCustomers = request.maxCustomers() == null ? 5 : request.maxCustomers();
		var recentDays = request.recentDays() == null ? 30 : request.recentDays();
		var businessDate = LocalDate.ofInstant(now, DEFAULT_BUSINESS_ZONE);
		var runId = UUID.randomUUID();
		var scope = scope(request, maxCustomers, recentDays, businessDate);

		mapper.insertRun(runId, organizationId, configId, principal.memberId(), "MANUAL", "RUNNING",
				businessDate, null, scope, Map.of("requestedBy", principal.email()), now, now);
		var sequence = new Sequence();
		insertStep(runId, null, sequence.next(), "SYSTEM", "启动客户跟进建议 Agent", "SUCCEEDED",
				Map.of("triggerType", "MANUAL"), Map.of("message", "开始扫描最近客户互动"), now, now, null);

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
				var analysis = chatAnalysisService.analyze(candidate.getCustomerId(), candidate.getInteractionId());
				var priority = priority(analysis);
				var dueAt = now.plus(Duration.ofDays("HIGH".equals(analysis.intentLevel()) ? 1 : 3));
				var actionPlan = resolveActionPlan(analysis);
				var payload = followUpPayload(candidate, analysis, priority, dueAt, actionPlan.suggestedNextAction());
				if ("SEND_EMAIL".equals(actionPlan.actionType())) {
					var emailTo = mapper.selectNotificationEmail(organizationId, candidate.getCustomerId(), candidate.getOwnerMemberId());
					var emailSubject = "跟进提醒：" + candidate.getCustomerName();
					var emailBody = buildEmailBody(candidate.getCustomerName(), analysis);
					payload.put("email", Map.of("to", emailTo, "subject", emailSubject, "body", emailBody));
					payload.put("subject", emailSubject);
					payload.put("body", emailBody);
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
						Map.of("customerName", candidate.getCustomerName(), "action", actionPlan.suggestedNextAction(), "priority", priority),
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

	private static Map<String, Object> scope(AgentRunCreateRequest request, int maxCustomers, int recentDays, LocalDate businessDate) {
		var scope = new LinkedHashMap<String, Object>();
		scope.put("businessDate", businessDate.toString());
		scope.put("recentDays", recentDays);
		scope.put("maxCustomers", maxCustomers);
		if (!CollectionUtils.isEmpty(request.customerIds())) {
			scope.put("customerIds", request.customerIds().stream().map(UUID::toString).toList());
		}
		return scope;
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
