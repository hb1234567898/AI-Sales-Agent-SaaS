package com.yourcompany.salesagent.auth.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.yourcompany.salesagent.customer.api.CustomerUpsertRequest;
import com.yourcompany.salesagent.customer.application.*;
import com.yourcompany.salesagent.customer.domain.*;
import com.yourcompany.salesagent.customer.infrastructure.*;
import com.yourcompany.salesagent.interaction.api.*;
import com.yourcompany.salesagent.interaction.application.*;
import com.yourcompany.salesagent.interaction.domain.*;
import com.yourcompany.salesagent.interaction.infrastructure.*;
import com.yourcompany.salesagent.lead.application.LeadService;
import com.yourcompany.salesagent.lead.infrastructure.LeadMapper;
import com.yourcompany.salesagent.ai.application.*;
import com.yourcompany.salesagent.ai.infrastructure.AiOrchestrationClient;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings({"rawtypes", "unchecked"})
class TenantDataAccessTests {
    static final UUID ORG_A = UUID.randomUUID(), ORG_B = UUID.randomUUID(), CUSTOMER_ID = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-12T06:00:00Z");
    final CustomerMapper customers = mock(CustomerMapper.class);
    final CustomerContactMapper contacts = mock(CustomerContactMapper.class);
    final InteractionMapper interactions = mock(InteractionMapper.class);
    final ChatAnalysisMapper analyses = mock(ChatAnalysisMapper.class);
    final AiOrchestrationClient model = mock(AiOrchestrationClient.class);
    final AiModelService modelSettings = mock(AiModelService.class);
    final ModelCallRecorder usage = mock(ModelCallRecorder.class);
    final LeadMapper leads = mock(LeadMapper.class);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final CustomerService customerService = new CustomerService(customers, contacts, clock);
    final InteractionService interactionService = new InteractionService(interactions, customers, clock);
    final ChatAnalysisService analysisService = new ChatAnalysisService(analyses, interactions, customers, model, modelSettings, usage, JsonMapper.builder().build(), clock);

    @BeforeAll static void metadata() {
        var config = new MybatisConfiguration();
        config.getTypeHandlerRegistry().register(UUID.class, com.yourcompany.salesagent.shared.persistence.UuidTypeHandler.class);
        for (var type : List.of(Customer.class, CustomerContact.class, Interaction.class, ChatAnalysis.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config, "test"), type);
        }
    }

    AuthPrincipal actor(UUID org, String role) {
        return new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), org, UUID.randomUUID(), "sales@example.com", "Sales", "Team", role, NOW.plusSeconds(3600));
    }
    CustomerUpsertRequest customerInput() {
        return new CustomerUpsertRequest("新客户", null, null, null, CustomerStage.LEAD, CustomerStatus.ACTIVE, CustomerSource.MANUAL, null, 50, null, null, null, "联系人", null, null);
    }
    static void assertOrganization(AbstractWrapper query, UUID org) {
        assertThat(query.getSqlSegment()).contains("organization_id");
        assertThat(query.getParamNameValuePairs()).containsValue(org).doesNotContainValue(org.equals(ORG_A) ? ORG_B : ORG_A);
    }

    @Test void customerListMetricsAndOwnersUseTheSignedInOrganization() {
        when(customers.selectPage(any(), any())).thenAnswer(inv -> new Page<Customer>(1, 20, 0));
        when(customers.selectOwners(any())).thenReturn(List.of());
        var actor = actor(ORG_B, "SALES");
        customerService.findCustomers(actor, "", null, null, 0, 20);
        customerService.getMetrics(actor);
        customerService.loadOwners(actor);
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customers).selectPage(any(), query.capture());
        assertOrganization(query.getValue(), ORG_B);
        verify(customers).selectMetrics(ORG_B);
        verify(customers, atLeastOnce()).selectOwners(ORG_B);
    }

    @Test void foreignCustomerCannotBeReadOrUpdated() {
        when(customers.selectOne(any())).thenReturn(null);
        var actor = actor(ORG_B, "SALES");
        assertThatThrownBy(() -> customerService.findCustomer(actor, CUSTOMER_ID)).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customerService.updateCustomer(actor, CUSTOMER_ID, customerInput())).isInstanceOf(CustomerNotFoundException.class);
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customers, times(2)).selectOne(query.capture());
        query.getAllValues().forEach(q -> assertOrganization((AbstractWrapper) q, ORG_B));
        verify(customers, never()).updateById(any(Customer.class));
        verifyNoInteractions(contacts);
    }

    @Test void newCustomerAndContactAreStoredInTheCallersOrganization() {
        when(customers.selectOwners(ORG_B)).thenReturn(List.of());
        customerService.createCustomer(actor(ORG_B, "SALES"), customerInput());
        var customer = ArgumentCaptor.forClass(Customer.class);
        var contact = ArgumentCaptor.forClass(CustomerContact.class);
        verify(customers).insert(customer.capture());
        verify(contacts).insert(contact.capture());
        assertThat(customer.getValue().getOrganizationId()).isEqualTo(ORG_B);
        assertThat(contact.getValue().getOrganizationId()).isEqualTo(ORG_B);
    }

    @Test void interactionHistoryAndChatAnalysisRejectForeignCustomerIds() {
        when(customers.selectCount(any())).thenReturn(0L);
        when(customers.selectOne(any())).thenReturn(null);
        var actor = actor(ORG_B, "SALES");
        assertThatThrownBy(() -> interactionService.findTimeline(actor, CUSTOMER_ID, 0, 20)).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> analysisService.findLatestForCustomer(actor, CUSTOMER_ID)).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> analysisService.analyze(actor, CUSTOMER_ID, UUID.randomUUID())).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> analysisService.apply(actor, CUSTOMER_ID, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(CustomerNotFoundException.class);
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(customers).selectCount(query.capture());
        assertOrganization(query.getValue(), ORG_B);
        verify(customers, times(3)).selectOne(query.capture());
        query.getAllValues().forEach(q -> assertOrganization((AbstractWrapper) q, ORG_B));
        verifyNoInteractions(interactions, analyses, model);
    }

    @Test void importedChatIsStoredInTheCallersOrganization() {
        when(customers.selectCount(any())).thenReturn(1L);
        interactionService.importChat(actor(ORG_B, "SALES"), CUSTOMER_ID, new ChatImportRequest(ChatPlatform.WECHAT, NOW, "报价沟通", "客户希望报价", null));
        var interaction = ArgumentCaptor.forClass(Interaction.class);
        verify(interactions).insert(interaction.capture());
        assertThat(interaction.getValue().getOrganizationId()).isEqualTo(ORG_B);
        var query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(customers).update(isNull(Customer.class), query.capture());
        assertOrganization(query.getValue(), ORG_B);
    }

    @ParameterizedTest @ValueSource(strings = {"VIEWER", "GUEST", "UNKNOWN"})
    void readOnlyAndUnknownRolesCannotMutateCrmOrConsumeAnalysisTokens(String role) {
        var actor = actor(ORG_A, role);
        assertThatThrownBy(() -> customerService.createCustomer(actor, customerInput())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> customerService.updateCustomer(actor, CUSTOMER_ID, customerInput())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interactionService.create(actor, CUSTOMER_ID, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interactionService.importChat(actor, CUSTOMER_ID, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> analysisService.analyze(actor, CUSTOMER_ID, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> analysisService.apply(actor, CUSTOMER_ID, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        var leadService = new LeadService(leads, clock);
        assertThatThrownBy(() -> leadService.createLead(actor, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leadService.updateLead(actor, CUSTOMER_ID, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leadService.assign(actor, CUSTOMER_ID, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leadService.convert(actor, CUSTOMER_ID, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leadService.importLeads(actor, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customers, contacts, interactions, analyses, leads, model, modelSettings, usage);
    }

    @Test void emptyHistoricalContentReturnsValidationErrorInsteadOfNullPointerException() {
        var customer = Customer.create(ORG_B, "Customer", NOW);
        var interaction = Interaction.create(ORG_B, customer.getId(), InteractionType.CHAT_IMPORT, InteractionDirection.NONE, NOW, null, "body", List.of(), "WECHAT", Map.of(), NOW);
        ReflectionTestUtils.setField(interaction, "bodyText", null);
        when(customers.selectOne(any())).thenReturn(customer);
        when(interactions.selectOne(any())).thenReturn(interaction);
        assertThatThrownBy(() -> analysisService.analyze(actor(ORG_B, "SALES"), customer.getId(), interaction.getId()))
            .isInstanceOf(InteractionValidationException.class).hasMessageContaining("没有可分析的正文");
        verifyNoInteractions(model);
    }
}
