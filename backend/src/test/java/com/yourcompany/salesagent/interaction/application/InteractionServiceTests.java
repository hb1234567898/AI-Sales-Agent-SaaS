package com.yourcompany.salesagent.interaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.infrastructure.CustomerMapper;
import com.yourcompany.salesagent.interaction.api.ChatImportRequest;
import com.yourcompany.salesagent.interaction.api.InteractionCreateRequest;
import com.yourcompany.salesagent.interaction.domain.ChatPlatform;
import com.yourcompany.salesagent.interaction.domain.Interaction;
import com.yourcompany.salesagent.interaction.domain.InteractionDirection;
import com.yourcompany.salesagent.interaction.domain.InteractionType;
import com.yourcompany.salesagent.interaction.infrastructure.InteractionMapper;

class InteractionServiceTests {
    @org.junit.jupiter.api.BeforeAll
    static void initializeCustomerMetadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, com.yourcompany.salesagent.shared.persistence.UuidTypeHandler.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "test"), Customer.class);
    }


	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CUSTOMER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC);

	@Test
	void importsChatAndUpdatesCustomerTimeline() {
		var interactionMapper = mock(InteractionMapper.class);
		var customerMapper = mock(CustomerMapper.class);
		when(customerMapper.selectCount(any())).thenReturn(1L);
		var service = new InteractionService(interactionMapper, customerMapper, clock);

		var response = service.importChat(actor(), CUSTOMER_ID, new ChatImportRequest(
				ChatPlatform.WECHAT,
				Instant.parse("2026-08-25T07:30:00Z"),
				"部署周期确认",
				"客户：能否补充部署周期？\n销售：今天下午补充。",
				"林婉清"));

		var captor = ArgumentCaptor.forClass(Interaction.class);
		verify(interactionMapper).insert(captor.capture());
		verify(customerMapper).update(isNull(Customer.class), any());
		assertThat(captor.getValue().getType()).isEqualTo(InteractionType.CHAT_IMPORT);
		assertThat(captor.getValue().getSource()).isEqualTo("WECHAT");
		assertThat(response.participants()).containsExactly("林婉清");
		assertThat(response.bodyText()).contains("部署周期");
	}

	@Test
	void rejectsSystemGeneratedInteractionType() {
		var interactionMapper = mock(InteractionMapper.class);
		var customerMapper = mock(CustomerMapper.class);
		when(customerMapper.selectCount(any())).thenReturn(1L);
		var service = new InteractionService(interactionMapper, customerMapper, clock);

		var request = new InteractionCreateRequest(
				InteractionType.CRM_UPDATE,
				InteractionDirection.NONE,
				Instant.parse("2026-08-25T07:30:00Z"),
				null,
				"更新商机阶段",
				null);

		assertThatThrownBy(() -> service.create(actor(), CUSTOMER_ID, request))
				.isInstanceOf(InteractionValidationException.class);
	}

    private com.yourcompany.salesagent.auth.security.AuthPrincipal actor() {
        return new com.yourcompany.salesagent.auth.security.AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), ORGANIZATION_ID, UUID.randomUUID(), "sales@example.com", "Sales", "Team", "SALES", clock.instant().plusSeconds(3600));
    }
}
