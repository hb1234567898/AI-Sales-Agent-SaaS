package com.yourcompany.salesagent.interaction.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yourcompany.salesagent.shared.persistence.JsonbStringListTypeHandler;

@TableName(value = "interaction_ai_analysis", autoResultMap = true)
public class ChatAnalysis {

	@TableId(type = IdType.INPUT)
	private UUID id;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("customer_id")
	private UUID customerId;

	@TableField("interaction_id")
	private UUID interactionId;

	@TableField("analysis_version")
	private int analysisVersion;

	private ChatAnalysisStatus status;

	@TableField("model_provider")
	private String modelProvider;

	@TableField("model_name")
	private String modelName;

	@TableField("prompt_version")
	private String promptVersion;

	private String summary;

	@TableField("intent_score")
	private int intentScore;

	@TableField("intent_level")
	private String intentLevel;

	private String sentiment;

	@TableField(typeHandler = JsonbStringListTypeHandler.class)
	private List<String> needs = new ArrayList<>();

	@TableField(value = "pain_points", typeHandler = JsonbStringListTypeHandler.class)
	private List<String> painPoints = new ArrayList<>();

	@TableField(typeHandler = JsonbStringListTypeHandler.class)
	private List<String> objections = new ArrayList<>();

	@TableField(typeHandler = JsonbStringListTypeHandler.class)
	private List<String> risks = new ArrayList<>();

	@TableField(value = "recommended_actions", typeHandler = JsonbStringListTypeHandler.class)
	private List<String> recommendedActions = new ArrayList<>();

	@TableField("suggested_next_action")
	private String suggestedNextAction;

	@TableField("budget_signal")
	private String budgetSignal;

	@TableField("timeline_signal")
	private String timelineSignal;

	@TableField("decision_maker_signal")
	private String decisionMakerSignal;

	@TableField(typeHandler = JsonbStringListTypeHandler.class)
	private List<String> evidence = new ArrayList<>();

	@TableField("analyzed_at")
	private Instant analyzedAt;

	@TableField("applied_at")
	private Instant appliedAt;

	@TableField("created_at")
	private Instant createdAt;

	@TableField("updated_at")
	private Instant updatedAt;

	protected ChatAnalysis() {
	}

	public static ChatAnalysis create(
			UUID organizationId,
			UUID customerId,
			UUID interactionId,
			int analysisVersion,
			String modelName,
			String promptVersion,
			ChatAnalysisModelOutput output,
			Instant now) {
		var analysis = new ChatAnalysis();
		analysis.id = UUID.randomUUID();
		analysis.organizationId = organizationId;
		analysis.customerId = customerId;
		analysis.interactionId = interactionId;
		analysis.analysisVersion = analysisVersion;
		analysis.status = ChatAnalysisStatus.DRAFT;
		analysis.modelProvider = "QWEN";
		analysis.modelName = modelName;
		analysis.promptVersion = promptVersion;
		analysis.summary = output.summary();
		analysis.intentScore = output.intentScore();
		analysis.intentLevel = output.intentLevel();
		analysis.sentiment = output.sentiment();
		analysis.needs = new ArrayList<>(output.needs());
		analysis.painPoints = new ArrayList<>(output.painPoints());
		analysis.objections = new ArrayList<>(output.objections());
		analysis.risks = new ArrayList<>(output.risks());
		analysis.recommendedActions = new ArrayList<>(output.recommendedActions());
		analysis.suggestedNextAction = output.suggestedNextAction();
		analysis.budgetSignal = output.budgetSignal();
		analysis.timelineSignal = output.timelineSignal();
		analysis.decisionMakerSignal = output.decisionMakerSignal();
		analysis.evidence = new ArrayList<>(output.evidence());
		analysis.analyzedAt = now;
		analysis.createdAt = now;
		analysis.updatedAt = now;
		return analysis;
	}

	public void markApplied(Instant now) {
		status = ChatAnalysisStatus.APPLIED;
		appliedAt = now;
		updatedAt = now;
	}

	public UUID getId() { return id; }
	public UUID getOrganizationId() { return organizationId; }
	public UUID getCustomerId() { return customerId; }
	public UUID getInteractionId() { return interactionId; }
	public int getAnalysisVersion() { return analysisVersion; }
	public ChatAnalysisStatus getStatus() { return status; }
	public String getModelProvider() { return modelProvider; }
	public String getModelName() { return modelName; }
	public String getPromptVersion() { return promptVersion; }
	public String getSummary() { return summary; }
	public int getIntentScore() { return intentScore; }
	public String getIntentLevel() { return intentLevel; }
	public String getSentiment() { return sentiment; }
	public List<String> getNeeds() { return needs; }
	public List<String> getPainPoints() { return painPoints; }
	public List<String> getObjections() { return objections; }
	public List<String> getRisks() { return risks; }
	public List<String> getRecommendedActions() { return recommendedActions; }
	public String getSuggestedNextAction() { return suggestedNextAction; }
	public String getBudgetSignal() { return budgetSignal; }
	public String getTimelineSignal() { return timelineSignal; }
	public String getDecisionMakerSignal() { return decisionMakerSignal; }
	public List<String> getEvidence() { return evidence; }
	public Instant getAnalyzedAt() { return analyzedAt; }
	public Instant getAppliedAt() { return appliedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
