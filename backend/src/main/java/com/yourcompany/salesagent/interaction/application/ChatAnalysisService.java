package com.yourcompany.salesagent.interaction.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yourcompany.salesagent.ai.application.AiModelConnectionException;
import com.yourcompany.salesagent.ai.application.AiModelService;
import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.customer.application.CustomerNotFoundException;
import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.infrastructure.CustomerMapper;
import com.yourcompany.salesagent.interaction.api.ChatAnalysisResponse;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysis;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysisModelOutput;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysisStatus;
import com.yourcompany.salesagent.interaction.domain.Interaction;
import com.yourcompany.salesagent.interaction.domain.InteractionType;
import com.yourcompany.salesagent.interaction.infrastructure.ChatAnalysisMapper;
import com.yourcompany.salesagent.interaction.infrastructure.InteractionMapper;

import tools.jackson.databind.ObjectMapper;

@Service
public class ChatAnalysisService {

	private static final String PROMPT_VERSION = "chat-analysis-v1";
	private static final int MAX_MODEL_INPUT_CHARS = 40_000;
	private static final List<String> INTENT_LEVELS = List.of("LOW", "MEDIUM", "HIGH");
	private static final List<String> SENTIMENTS = List.of("NEGATIVE", "NEUTRAL", "POSITIVE", "MIXED");

	private final ChatAnalysisMapper analysisMapper;
	private final InteractionMapper interactionMapper;
	private final CustomerMapper customerMapper;
	private final QwenModelClient modelClient;
	private final AiModelService modelService;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final UUID organizationId;

	public ChatAnalysisService(
			ChatAnalysisMapper analysisMapper,
			InteractionMapper interactionMapper,
			CustomerMapper customerMapper,
			QwenModelClient modelClient,
			AiModelService modelService,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.analysisMapper = analysisMapper;
		this.interactionMapper = interactionMapper;
		this.customerMapper = customerMapper;
		this.modelClient = modelClient;
		this.modelService = modelService;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(readOnly = true)
	public List<ChatAnalysisResponse> findLatestForCustomer(UUID customerId) {
		requireCustomer(customerId);
		var analyses = analysisMapper.selectList(Wrappers.<ChatAnalysis>lambdaQuery()
				.eq(ChatAnalysis::getOrganizationId, organizationId)
				.eq(ChatAnalysis::getCustomerId, customerId)
				.orderByDesc(ChatAnalysis::getAnalysisVersion)
				.orderByDesc(ChatAnalysis::getAnalyzedAt));
		var latestByInteraction = new LinkedHashMap<UUID, ChatAnalysis>();
		analyses.forEach(analysis -> latestByInteraction.putIfAbsent(analysis.getInteractionId(), analysis));
		return latestByInteraction.values().stream().map(ChatAnalysisResponse::from).toList();
	}

	@Transactional
	public ChatAnalysisResponse analyze(UUID customerId, UUID interactionId) {
		var customer = requireCustomer(customerId);
		var interaction = requireChatInteraction(customerId, interactionId);
		var modelConfiguration = modelService.requireRuntimeConfiguration(organizationId);

		String rawOutput;
		try {
			rawOutput = modelClient.analyzeChat(
					modelConfiguration,
					customerContext(customer),
					limitChatContent(interaction.getBodyText()));
		}
		catch (RuntimeException exception) {
			throw new AiModelConnectionException("千问聊天分析失败，请检查模型配置和服务器网络", exception);
		}

		var output = parseAndValidate(rawOutput);
		var previous = latestAnalysis(interactionId);
		var version = previous == null ? 1 : previous.getAnalysisVersion() + 1;
		var now = clock.instant();
		var analysis = ChatAnalysis.create(
				organizationId,
				customerId,
				interactionId,
				version,
				modelConfiguration.model(),
				PROMPT_VERSION,
				output,
				now);
		analysisMapper.insert(analysis);
		return ChatAnalysisResponse.from(analysis);
	}

	@Transactional
	public ChatAnalysisResponse apply(UUID customerId, UUID interactionId, UUID analysisId) {
		var customer = requireCustomer(customerId);
		requireChatInteraction(customerId, interactionId);
		var analysis = analysisMapper.selectOne(Wrappers.<ChatAnalysis>lambdaQuery()
				.eq(ChatAnalysis::getId, analysisId)
				.eq(ChatAnalysis::getOrganizationId, organizationId)
				.eq(ChatAnalysis::getCustomerId, customerId)
				.eq(ChatAnalysis::getInteractionId, interactionId)
				.last("LIMIT 1"));
		if (analysis == null) {
			throw new InteractionValidationException("聊天分析结果不存在或不属于当前客户");
		}
		var latest = latestAnalysis(interactionId);
		if (latest == null || !latest.getId().equals(analysisId)) {
			throw new InteractionValidationException("该分析已不是最新版本，请刷新后再确认");
		}
		if (analysis.getStatus() == ChatAnalysisStatus.APPLIED) {
			return ChatAnalysisResponse.from(analysis);
		}

		var now = clock.instant();
		customer.applyAiSuggestion(analysis.getIntentScore(), analysis.getSuggestedNextAction(), now);
		if (customerMapper.updateById(customer) == 0) {
			throw new InteractionValidationException("客户资料已变化，请刷新后重新确认 AI 建议");
		}
		analysis.markApplied(now);
		analysisMapper.updateById(analysis);
		return ChatAnalysisResponse.from(analysis);
	}

	private Customer requireCustomer(UUID customerId) {
		var customer = customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
				.eq(Customer::getId, customerId)
				.eq(Customer::getOrganizationId, organizationId)
				.isNull(Customer::getDeletedAt)
				.last("LIMIT 1"));
		if (customer == null) {
			throw new CustomerNotFoundException(customerId);
		}
		return customer;
	}

	private Interaction requireChatInteraction(UUID customerId, UUID interactionId) {
		var interaction = interactionMapper.selectOne(Wrappers.<Interaction>lambdaQuery()
				.eq(Interaction::getId, interactionId)
				.eq(Interaction::getOrganizationId, organizationId)
				.eq(Interaction::getCustomerId, customerId)
				.last("LIMIT 1"));
		if (interaction == null) {
			throw new InteractionValidationException("聊天记录不存在或不属于当前客户");
		}
		if (interaction.getType() != InteractionType.CHAT_IMPORT) {
			throw new InteractionValidationException("只有导入的聊天记录可以执行 AI 分析");
		}
		return interaction;
	}

	private ChatAnalysis latestAnalysis(UUID interactionId) {
		return analysisMapper.selectOne(Wrappers.<ChatAnalysis>lambdaQuery()
				.eq(ChatAnalysis::getOrganizationId, organizationId)
				.eq(ChatAnalysis::getInteractionId, interactionId)
				.orderByDesc(ChatAnalysis::getAnalysisVersion)
				.last("LIMIT 1"));
	}

	private ChatAnalysisModelOutput parseAndValidate(String rawOutput) {
		try {
			var json = extractJson(rawOutput);
			var parsed = objectMapper.readValue(json, ChatAnalysisModelOutput.class);
			var intentLevel = normalizeEnum(parsed.intentLevel(), INTENT_LEVELS, "意向等级");
			var sentiment = normalizeEnum(parsed.sentiment(), SENTIMENTS, "客户情绪");
			if (!StringUtils.hasText(parsed.summary()) || parsed.intentScore() == null
					|| parsed.intentScore() < 0 || parsed.intentScore() > 100) {
				throw new IllegalArgumentException("摘要或意向评分不符合约束");
			}
			var actions = normalizeList(parsed.recommendedActions(), 8, 300);
			var nextAction = limitText(parsed.suggestedNextAction(), 500);
			if (nextAction == null && !actions.isEmpty()) nextAction = actions.get(0);
			return new ChatAnalysisModelOutput(
					limitRequiredText(parsed.summary(), 1_500),
					parsed.intentScore(),
					intentLevel,
					sentiment,
					normalizeList(parsed.needs(), 8, 300),
					normalizeList(parsed.painPoints(), 8, 300),
					normalizeList(parsed.objections(), 8, 300),
					normalizeList(parsed.risks(), 8, 300),
					actions,
					nextAction,
					limitText(parsed.budgetSignal(), 500),
					limitText(parsed.timelineSignal(), 500),
					limitText(parsed.decisionMakerSignal(), 500),
					normalizeList(parsed.evidence(), 6, 500));
		}
		catch (Exception exception) {
			throw new AiModelConnectionException("千问返回的聊天分析格式不正确，请重试", exception);
		}
	}

	private static String customerContext(Customer customer) {
		return "客户名称：" + customer.getName()
				+ "\n行业：" + valueOrUnknown(customer.getIndustry())
				+ "\n当前商机阶段：" + customer.getStage();
	}

	private static String limitChatContent(String content) {
		if (content.length() <= MAX_MODEL_INPUT_CHARS) return content;
		return content.substring(0, 15_000)
				+ "\n\n[中间过长内容已省略]\n\n"
				+ content.substring(content.length() - 25_000);
	}

	private static String extractJson(String value) {
		if (!StringUtils.hasText(value)) throw new IllegalArgumentException("模型返回为空");
		var start = value.indexOf('{');
		var end = value.lastIndexOf('}');
		if (start < 0 || end <= start) throw new IllegalArgumentException("模型未返回 JSON 对象");
		return value.substring(start, end + 1);
	}

	private static String normalizeEnum(String value, List<String> allowed, String field) {
		var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + "不符合约束");
		return normalized;
	}

	private static List<String> normalizeList(List<String> values, int maxItems, int maxLength) {
		if (values == null) return List.of();
		var normalized = new ArrayList<String>();
		for (var value : values) {
			var text = limitText(value, maxLength);
			if (text != null && !normalized.contains(text)) normalized.add(text);
			if (normalized.size() == maxItems) break;
		}
		return List.copyOf(normalized);
	}

	private static String limitRequiredText(String value, int maxLength) {
		var limited = limitText(value, maxLength);
		if (limited == null) throw new IllegalArgumentException("必填文本为空");
		return limited;
	}

	private static String limitText(String value, int maxLength) {
		if (!StringUtils.hasText(value)) return null;
		var stripped = value.strip();
		return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
	}

	private static String valueOrUnknown(String value) {
		return StringUtils.hasText(value) ? value : "未知";
	}
}
