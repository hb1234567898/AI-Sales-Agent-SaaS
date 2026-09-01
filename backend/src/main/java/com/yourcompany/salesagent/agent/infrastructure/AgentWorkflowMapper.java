package com.yourcompany.salesagent.agent.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface AgentWorkflowMapper {

	void insertDefaultConfigIfAbsent(@Param("organizationId") UUID organizationId, @Param("now") Instant now);

	UUID selectDefaultConfigId(@Param("organizationId") UUID organizationId);

	void insertRun(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("agentConfigId") UUID agentConfigId,
			@Param("initiatedByMemberId") UUID initiatedByMemberId,
			@Param("triggerType") String triggerType,
			@Param("status") String status,
			@Param("businessDate") LocalDate businessDate,
			@Param("deduplicationKey") String deduplicationKey,
			@Param("scope") Map<String, Object> scope,
			@Param("inputSnapshot") Map<String, Object> inputSnapshot,
			@Param("queuedAt") Instant queuedAt,
			@Param("startedAt") Instant startedAt);

	void insertStep(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("runId") UUID runId,
			@Param("customerId") UUID customerId,
			@Param("sequenceNo") long sequenceNo,
			@Param("stepType") String stepType,
			@Param("name") String name,
			@Param("status") String status,
			@Param("inputSnapshot") Map<String, Object> inputSnapshot,
			@Param("outputSnapshot") Map<String, Object> outputSnapshot,
			@Param("errorMessage") String errorMessage,
			@Param("startedAt") Instant startedAt,
			@Param("completedAt") Instant completedAt,
			@Param("durationMs") Long durationMs);

	void completeRun(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("status") String status,
			@Param("totalCandidates") int totalCandidates,
			@Param("processedCount") int processedCount,
			@Param("succeededCount") int succeededCount,
			@Param("skippedCount") int skippedCount,
			@Param("failedCount") int failedCount,
			@Param("pendingApprovalCount") int pendingApprovalCount,
			@Param("outputSummary") Map<String, Object> outputSummary,
			@Param("errorMessage") String errorMessage,
			@Param("completedAt") Instant completedAt);

	List<AgentCandidateRow> selectCandidates(
			@Param("organizationId") UUID organizationId,
			@Param("since") Instant since,
			@Param("customerIds") List<UUID> customerIds,
			@Param("limit") int limit);

	IPage<AgentRunRow> selectRuns(Page<AgentRunRow> page, @Param("organizationId") UUID organizationId);

	AgentRunRow selectRun(@Param("organizationId") UUID organizationId, @Param("runId") UUID runId);

	IPage<AgentStepRow> selectSteps(Page<AgentStepRow> page, @Param("organizationId") UUID organizationId, @Param("runId") UUID runId);

	void insertActionRequest(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("runId") UUID runId,
			@Param("stepId") UUID stepId,
			@Param("customerId") UUID customerId,
			@Param("requestedByMemberId") UUID requestedByMemberId,
			@Param("actionType") String actionType,
			@Param("riskLevel") String riskLevel,
			@Param("status") String status,
			@Param("reason") String reason,
			@Param("payload") Map<String, Object> payload,
			@Param("payloadHash") String payloadHash,
			@Param("preview") Map<String, Object> preview,
			@Param("idempotencyKey") String idempotencyKey,
			@Param("expiresAt") Instant expiresAt);

	void insertApproval(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("requestedByMemberId") UUID requestedByMemberId,
			@Param("requestReason") String requestReason,
			@Param("contentHash") String contentHash,
			@Param("requestedAt") Instant requestedAt,
			@Param("expiresAt") Instant expiresAt);
}
