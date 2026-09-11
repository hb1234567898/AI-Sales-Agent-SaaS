package com.yourcompany.salesagent.ai.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.yourcompany.salesagent.ai.infrastructure.ModelCallMapper;

@Service
public class ModelCallRecorder {

	private final ModelCallMapper mapper;

	public ModelCallRecorder(ModelCallMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public UUID record(ModelCallRecordRequest request) {
		var id = UUID.randomUUID();
		var usage = request.usage();
		Long inputTokens = usage == null || usage.promptTokens() == null ? null : usage.promptTokens().longValue();
		Long outputTokens = usage == null || usage.completionTokens() == null ? null : usage.completionTokens().longValue();
		Long cachedTokens = usage == null ? null : usage.cachedInputTokens();
		var outputSnapshot = new LinkedHashMap<>(request.outputSnapshot() == null ? Map.<String, Object>of() : request.outputSnapshot());
		if (usage != null) {
			outputSnapshot.put("usage", usageSnapshot(usage));
		}
		mapper.insertModelCall(
				id,
				request.organizationId(),
				request.runId(),
				request.stepId(),
				request.customerId(),
				request.purpose(),
				request.provider(),
				request.model(),
				request.providerRequestId(),
				request.promptVersion(),
				request.schemaVersion(),
				request.status(),
				request.attemptNo(),
				inputTokens,
				outputTokens,
				cachedTokens,
				request.latencyMs(),
				request.inputHash(),
				request.outputHash(),
				request.inputSnapshot() == null ? Map.of() : request.inputSnapshot(),
				outputSnapshot,
				request.errorCode(),
				request.errorMessage(),
				request.startedAt(),
				request.completedAt());
		if (inputTokens != null) {
			insertUsage(id, request, "MODEL_INPUT_TOKEN", BigDecimal.valueOf(inputTokens), usageSnapshot(usage));
		}
		if (outputTokens != null) {
			insertUsage(id, request, "MODEL_OUTPUT_TOKEN", BigDecimal.valueOf(outputTokens), usageSnapshot(usage));
		}
		if (cachedTokens != null && cachedTokens > 0) {
			insertUsage(id, request, "MODEL_CACHED_TOKEN", BigDecimal.valueOf(cachedTokens), usageSnapshot(usage));
		}
		return id;
	}

	private void insertUsage(UUID modelCallId, ModelCallRecordRequest request, String type, BigDecimal quantity,
			Map<String, Object> metadata) {
		mapper.insertUsageLedger(
				UUID.randomUUID(),
				request.organizationId(),
				request.runId(),
				modelCallId,
				type,
				request.provider(),
				request.model(),
				quantity,
				"TOKEN",
				null,
				null,
				null,
				null,
				request.completedAt() == null ? request.startedAt() : request.completedAt(),
				metadata);
	}

	private static Map<String, Object> usageSnapshot(ModelUsage usage) {
		var snapshot = new LinkedHashMap<String, Object>();
		if (usage == null) return snapshot;
		snapshot.put("promptTokens", usage.promptTokens());
		snapshot.put("completionTokens", usage.completionTokens());
		snapshot.put("totalTokens", usage.totalTokens());
		snapshot.put("cachedInputTokens", usage.cachedInputTokens());
		return snapshot;
	}
}
