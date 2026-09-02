package com.yourcompany.salesagent.tool.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工具执行层的数据访问。所有写操作（锁行、状态流转、tool_invocation、interaction 回写）
 * 由 ToolExecutionService 在独立事务中调用，保证不持有数据库事务等待外部 HTTP/SMTP 响应。
 */
@Mapper
public interface ToolExecutionMapper {

	ActionRequestRow selectActionRequestForExecution(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId);

	/**
	 * 将处于 APPROVED/EXECUTING 的动作抢占为 EXECUTING 并返回影响行数。返回 0 表示动作
	 * 不可执行（已被处理或版本变化），调用方应放弃本次执行。使用乐观版本号避免并发重复执行。
	 */
	int lockActionRequestForExecution(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("now") Instant now);

	void updateActionExecuting(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("now") Instant now);

	/**
	 * 审批通过后把动作从 AWAITING_APPROVAL 推进到 APPROVED。只有处于等待审批状态的动作能被批准，
	 * 与 ToolExecutionService 的抢占锁共同保证并发下只执行一次。
	 */
	void markActionApproved(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("now") Instant now);

	void updateActionSucceeded(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("now") Instant now);

	void updateActionFailed(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("failureCode") String failureCode,
			@Param("failureMessage") String failureMessage,
			@Param("now") Instant now);

	void insertToolInvocation(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("runId") UUID runId,
			@Param("stepId") UUID stepId,
			@Param("toolName") String toolName,
			@Param("toolVersion") String toolVersion,
			@Param("attemptNo") int attemptNo,
			@Param("idempotencyKey") String idempotencyKey,
			@Param("status") String status,
			@Param("requestPayload") Map<String, Object> requestPayload,
			@Param("now") Instant now);

	void completeToolInvocation(
			@Param("organizationId") UUID organizationId,
			@Param("actionRequestId") UUID actionRequestId,
			@Param("attemptNo") int attemptNo,
			@Param("status") String status,
			@Param("responsePayload") Map<String, Object> responsePayload,
			@Param("externalOperationId") String externalOperationId,
			@Param("retryable") boolean retryable,
			@Param("errorCode") String errorCode,
			@Param("errorMessage") String errorMessage,
			@Param("latencyMs") Long latencyMs,
			@Param("now") Instant now);

	/**
	 * 执行成功后回写时间线。type 由调用方根据动作类型决定（EMAIL_SENT / TASK_CREATED / CRM_UPDATE）。
	 */
	void insertInteractionFromExecution(
			@Param("organizationId") UUID organizationId,
			@Param("customerId") UUID customerId,
			@Param("createdByMemberId") UUID createdByMemberId,
			@Param("type") String type,
			@Param("direction") String direction,
			@Param("occurredAt") Instant occurredAt,
			@Param("subject") String subject,
			@Param("bodyText") String bodyText,
			@Param("sourcePayload") Map<String, Object> sourcePayload,
			@Param("now") Instant now);
}
