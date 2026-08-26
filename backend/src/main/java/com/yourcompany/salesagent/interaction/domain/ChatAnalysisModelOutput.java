package com.yourcompany.salesagent.interaction.domain;

import java.util.List;

public record ChatAnalysisModelOutput(
		String summary,
		Integer intentScore,
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
		List<String> evidence) {
}
