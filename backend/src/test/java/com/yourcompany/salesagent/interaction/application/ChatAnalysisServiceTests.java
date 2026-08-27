package com.yourcompany.salesagent.interaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.yourcompany.salesagent.ai.infrastructure.QwenModelClient;
import com.yourcompany.salesagent.ai.application.AiModelRuntimeConfiguration;
import com.yourcompany.salesagent.ai.application.AiModelService;
import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.infrastructure.CustomerMapper;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysis;
import com.yourcompany.salesagent.interaction.domain.ChatAnalysisStatus;
import com.yourcompany.salesagent.interaction.domain.Interaction;
import com.yourcompany.salesagent.interaction.domain.InteractionDirection;
import com.yourcompany.salesagent.interaction.domain.InteractionType;
import com.yourcompany.salesagent.interaction.infrastructure.ChatAnalysisMapper;
import com.yourcompany.salesagent.interaction.infrastructure.InteractionMapper;

import tools.jackson.databind.ObjectMapper;

class ChatAnalysisServiceTests {

	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CUSTOMER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final Instant NOW = Instant.parse("2026-08-26T02:00:00Z");

	@Test
	void analyzesImportedChatAndPersistsDraftResult() {
		var analysisMapper = mock(ChatAnalysisMapper.class);
		var interactionMapper = mock(InteractionMapper.class);
		var customerMapper = mock(CustomerMapper.class);
		var modelClient = mock(QwenModelClient.class);
		var modelService = mock(AiModelService.class);
		var customer = Customer.create(ORGANIZATION_ID, "云岚科技", NOW);
		var interaction = Interaction.create(
				ORGANIZATION_ID,
				CUSTOMER_ID,
				InteractionType.CHAT_IMPORT,
				InteractionDirection.NONE,
				NOW,
				"微信聊天记录",
				"客户：方案能否补充部署周期？销售：今天下午补充。",
				List.of("林婉清"),
				"WECHAT",
				Map.of(),
				NOW);

		when(customerMapper.selectOne(any())).thenReturn(customer);
		when(interactionMapper.selectOne(any())).thenReturn(interaction);
		when(analysisMapper.selectOne(any())).thenReturn(null);
		when(modelService.requireRuntimeConfiguration(ORGANIZATION_ID)).thenReturn(
				new AiModelRuntimeConfiguration("QWEN", "qwen-test", "https://example.invalid", "sk-test"));
		when(modelClient.analyzeChat(any(), any(), any())).thenReturn("""
				{
				  "summary":"客户关注部署周期，销售已承诺补充。",
				  "intentScore":82,
				  "intentLevel":"HIGH",
				  "sentiment":"POSITIVE",
				  "needs":["明确部署周期"],
				  "painPoints":[],
				  "objections":[],
				  "risks":["承诺内容尚未发送"],
				  "recommendedActions":["当天下午发送部署周期说明"],
				  "suggestedNextAction":"发送部署周期说明并确认收到",
				  "budgetSignal":"",
				  "timelineSignal":"当天下午",
				  "decisionMakerSignal":"",
				  "evidence":["方案能否补充部署周期"]
				}
				""");

		var service = new ChatAnalysisService(
				analysisMapper,
				interactionMapper,
				customerMapper,
				modelClient,
				modelService,
				new ObjectMapper(),
				Clock.fixed(NOW, ZoneOffset.UTC),
				ORGANIZATION_ID);

		var response = service.analyze(CUSTOMER_ID, interaction.getId());

		var captor = ArgumentCaptor.forClass(ChatAnalysis.class);
		verify(analysisMapper).insert(captor.capture());
		assertThat(response.intentScore()).isEqualTo(82);
		assertThat(response.status()).isEqualTo(ChatAnalysisStatus.DRAFT);
		assertThat(response.suggestedNextAction()).contains("部署周期");
		assertThat(captor.getValue().getPromptVersion()).isEqualTo("chat-analysis-v1");
	}
}
