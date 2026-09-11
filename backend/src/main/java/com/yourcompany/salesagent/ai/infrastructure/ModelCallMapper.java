package com.yourcompany.salesagent.ai.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ModelCallMapper {

	@Select("""
			SELECT COALESCE(SUM(input_tokens), 0)::bigint AS input_tokens,
			       COALESCE(SUM(output_tokens), 0)::bigint AS output_tokens,
			       COALESCE(SUM(cached_input_tokens), 0)::bigint AS cached_input_tokens,
			       COUNT(*)::bigint AS successful_calls,
			       MAX(completed_at) AS last_called_at
			FROM model_call
			WHERE organization_id = #{organizationId}
			  AND provider = #{provider}
			  AND status = 'SUCCEEDED'
			""")
	ModelUsageSummaryRow selectUsageSummary(
			@Param("organizationId") UUID organizationId,
			@Param("provider") String provider);

	@Insert("""
			INSERT INTO model_call (
			    id, organization_id, run_id, step_id, customer_id, purpose, provider, model,
			    provider_request_id, prompt_version, schema_version, status, attempt_no,
			    input_tokens, output_tokens, cached_input_tokens, latency_ms,
			    input_hash, output_hash, input_snapshot, output_snapshot,
			    error_code, error_message, started_at, completed_at
			) VALUES (
			    #{id}, #{organizationId}, #{runId}, #{stepId}, #{customerId}, #{purpose}, #{provider}, #{model},
			    #{providerRequestId}, #{promptVersion}, #{schemaVersion}, #{status}, #{attemptNo},
			    #{inputTokens}, #{outputTokens}, #{cachedInputTokens}, #{latencyMs},
			    #{inputHash}, #{outputHash}, #{inputSnapshot,typeHandler=com.yourcompany.salesagent.shared.persistence.JsonbMapTypeHandler},
			    #{outputSnapshot,typeHandler=com.yourcompany.salesagent.shared.persistence.JsonbMapTypeHandler},
			    #{errorCode}, #{errorMessage}, #{startedAt}, #{completedAt}
			)
			""")
	void insertModelCall(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("runId") UUID runId,
			@Param("stepId") UUID stepId,
			@Param("customerId") UUID customerId,
			@Param("purpose") String purpose,
			@Param("provider") String provider,
			@Param("model") String model,
			@Param("providerRequestId") String providerRequestId,
			@Param("promptVersion") String promptVersion,
			@Param("schemaVersion") String schemaVersion,
			@Param("status") String status,
			@Param("attemptNo") int attemptNo,
			@Param("inputTokens") Long inputTokens,
			@Param("outputTokens") Long outputTokens,
			@Param("cachedInputTokens") Long cachedInputTokens,
			@Param("latencyMs") Long latencyMs,
			@Param("inputHash") String inputHash,
			@Param("outputHash") String outputHash,
			@Param("inputSnapshot") Map<String, Object> inputSnapshot,
			@Param("outputSnapshot") Map<String, Object> outputSnapshot,
			@Param("errorCode") String errorCode,
			@Param("errorMessage") String errorMessage,
			@Param("startedAt") Instant startedAt,
			@Param("completedAt") Instant completedAt);

	@Insert("""
			INSERT INTO usage_ledger (
			    id, organization_id, run_id, model_call_id, usage_type, provider, resource_name,
			    quantity, unit, unit_cost, total_cost, currency, pricing_version, occurred_at, metadata
			) VALUES (
			    #{id}, #{organizationId}, #{runId}, #{modelCallId}, #{usageType}, #{provider}, #{resourceName},
			    #{quantity}, #{unit}, #{unitCost}, #{totalCost}, #{currency}, #{pricingVersion}, #{occurredAt},
			    #{metadata,typeHandler=com.yourcompany.salesagent.shared.persistence.JsonbMapTypeHandler}
			)
			""")
	void insertUsageLedger(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("runId") UUID runId,
			@Param("modelCallId") UUID modelCallId,
			@Param("usageType") String usageType,
			@Param("provider") String provider,
			@Param("resourceName") String resourceName,
			@Param("quantity") BigDecimal quantity,
			@Param("unit") String unit,
			@Param("unitCost") BigDecimal unitCost,
			@Param("totalCost") BigDecimal totalCost,
			@Param("currency") String currency,
			@Param("pricingVersion") String pricingVersion,
			@Param("occurredAt") Instant occurredAt,
			@Param("metadata") Map<String, Object> metadata);
}
