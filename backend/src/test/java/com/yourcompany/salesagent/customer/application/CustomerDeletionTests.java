package com.yourcompany.salesagent.customer.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.infrastructure.CustomerMapper;
import com.yourcompany.salesagent.customer.infrastructure.CustomerContactMapper;

class CustomerDeletionTests {
    @org.junit.jupiter.api.BeforeAll
    static void initializeCustomerMetadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, com.yourcompany.salesagent.shared.persistence.UuidTypeHandler.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "test"), Customer.class);
    }

    final UUID org = UUID.randomUUID();
    final CustomerMapper mapper = mock(CustomerMapper.class);
    final CustomerService service = new CustomerService(mapper, mock(CustomerContactMapper.class), Clock.systemUTC());
    AuthPrincipal principal(String role) { return new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), org, UUID.randomUUID(), "admin@example.com", "Admin", "Team", role, Instant.now().plusSeconds(3600)); }
    @Test void rejectsSalesBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.deleteCustomer(principal("SALES"), UUID.randomUUID(), 0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(mapper);
    }
    @Test void blocksCustomersWithQuotes() {
        var customer = Customer.create(org, "Customer", Instant.now());
        when(mapper.selectOne(any())).thenReturn(customer);
        when(mapper.countCustomerQuotes(org, customer.getId())).thenReturn(1L);
        assertThatThrownBy(() -> service.deleteCustomer(principal("ADMIN"), customer.getId(), 0)).isInstanceOf(CustomerValidationException.class).hasMessageContaining("报价");
        verify(mapper, never()).update(isNull(Customer.class), any());
    }
    @Test void checksVersionBeforeDeleting() {
        var customer = Customer.create(org, "Customer", Instant.now());
        when(mapper.selectOne(any())).thenReturn(customer);
        assertThatThrownBy(() -> service.deleteCustomer(principal("ADMIN"), customer.getId(), 7)).isInstanceOf(CustomerValidationException.class);
        verify(mapper, never()).update(isNull(Customer.class), any());
    }
    @Test void softDeletesWithoutRemovingHistory() {
        var customer = Customer.create(org, "Customer", Instant.now());
        when(mapper.selectOne(any())).thenReturn(customer);
        when(mapper.update(isNull(Customer.class), any())).thenReturn(1);
        service.deleteCustomer(principal("ADMIN"), customer.getId(), 0);
        verify(mapper).update(isNull(Customer.class), any());
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
    }
    @Test void rejectsMissingOrOtherOrganizationCustomer() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.deleteCustomer(principal("OWNER"), UUID.randomUUID(), 0)).isInstanceOf(CustomerNotFoundException.class);
        verify(mapper, never()).update(isNull(Customer.class), any());
    }
}
