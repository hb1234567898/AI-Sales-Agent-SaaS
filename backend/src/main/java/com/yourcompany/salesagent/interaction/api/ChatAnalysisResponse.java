package com.yourcompany.salesagent.interaction.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.yourcompany.salesagent.interaction.domain.ChatAnalysis;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysisStatus;

public record ChatAnalysisResponse(
		UUID id,
		UUID interactionId,
		int version,
		ChatAnalysisStatus status,
		String summary,
		int intentScore,
		String intentLevel,
		String sentiment,
		List<String> needs,
		List<String> painPoints,
		List<String> objections,
		List<String> risks,
		List<String> recommendedActions,
		String suggestedNextAction,
		String budgetSignal,
		String timelineSignal,
		String decisionMakerSignal,
		List<String> evidence,
		String provider,
		String model,
		String promptVersion,
		Instant analyzedAt,
		Instant appliedAt) {

	public static ChatAnalysisResponse from(ChatAnalysis analysis) {
		return new ChatAnalysisResponse(
				analysis.getId(),
				analysis.getInteractionId(),
				analysis.getAnalysisVersion(),
				analysis.getStatus(),
				analysis.getSummary(),
				analysis.getIntentScore(),
				analysis.getIntentLevel(),
				analysis.getSentiment(),
				List.copyOf(analysis.getNeeds()),
				List.copyOf(analysis.getPainPoints()),
				List.copyOf(analysis.getObjections()),
				List.copyOf(analysis.getRisks()),
				List.copyOf(analysis.getRecommendedActions()),
				analysis.getSuggestedNextAction(),
				analysis.getBudgetSignal(),
				analysis.getTimelineSignal(),
				analysis.getDecisionMakerSignal(),
				List.copyOf(analysis.getEvidence()),
				analysis.getModelProvider(),
				analysis.getModelName(),
				analysis.getPromptVersion(),
				analysis.getAnalyzedAt(),
				analysis.getAppliedAt());
	}
}
