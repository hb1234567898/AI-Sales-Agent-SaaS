package com.yourcompany.salesagent.customer.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.customer.api.CustomerMetricsResponse;
import com.yourcompany.salesagent.customer.api.CustomerResponse;
import com.yourcompany.salesagent.customer.api.CustomerResponse.PrimaryContactResponse;
import com.yourcompany.salesagent.customer.api.CustomerUpsertRequest;
import com.yourcompany.salesagent.customer.api.OwnerOptionResponse;
import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.domain.CustomerContact;
import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;
import com.yourcompany.salesagent.customer.infrastructure.CustomerContactMapper;
import com.yourcompany.salesagent.customer.infrastructure.CustomerMapper;

@Service
public class CustomerService {

	private final CustomerMapper customerMapper;
	private final CustomerContactMapper contactMapper;
	private final Clock clock;
	private final UUID organizationId;

	public CustomerService(
			CustomerMapper customerMapper,
			CustomerContactMapper contactMapper,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.customerMapper = customerMapper;
		this.contactMapper = contactMapper;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(readOnly = true)
	public IPage<CustomerResponse> findCustomers(
			String keyword,
			CustomerStage stage,
			CustomerStatus status,
			int page,
			int size) {
		var customerPage = customerMapper.selectPage(
				Page.of(page + 1L, size),
				buildQuery(keyword, stage, status));
		var contacts = loadPrimaryContacts(customerPage.getRecords());
		var owners = loadOwners().stream().collect(Collectors.toMap(OwnerOptionResponse::id, OwnerOptionResponse::name));
		var records = customerPage.getRecords().stream()
				.map(customer -> toResponse(customer, contacts.get(customer.getId()), owners))
				.toList();

		return new Page<CustomerResponse>(customerPage.getCurrent(), customerPage.getSize(), customerPage.getTotal())
				.setRecords(records);
	}

	@Transactional(readOnly = true)
	public CustomerResponse findCustomer(UUID customerId) {
		var customer = findEntity(customerId);
		var contact = contactMapper.selectOne(primaryContactQuery(customerId).last("LIMIT 1"));
		var owners = loadOwners().stream().collect(Collectors.toMap(OwnerOptionResponse::id, OwnerOptionResponse::name));
		return toResponse(customer, contact, owners);
	}

	@Transactional
	public CustomerResponse createCustomer(CustomerUpsertRequest request) {
		validateOwner(request.ownerMemberId());
		var now = clock.instant();
		var customer = Customer.create(organizationId, request.name().trim(), now);
		apply(customer, request, now);
		customerMapper.insert(customer);
		var contact = updatePrimaryContact(customer, request, now);
		var owners = loadOwners().stream().collect(Collectors.toMap(OwnerOptionResponse::id, OwnerOptionResponse::name));
		return toResponse(customer, contact, owners);
	}

	@Transactional
	public CustomerResponse updateCustomer(UUID customerId, CustomerUpsertRequest request) {
		validateOwner(request.ownerMemberId());
		var customer = findEntity(customerId);
		var now = clock.instant();
		apply(customer, request, now);
		if (customerMapper.updateById(customer) == 0) {
			throw new CustomerValidationException("客户资料已被其他成员更新，请刷新后重试");
		}
		var contact = updatePrimaryContact(customer, request, now);
		var owners = loadOwners().stream().collect(Collectors.toMap(OwnerOptionResponse::id, OwnerOptionResponse::name));
		return toResponse(customer, contact, owners);
	}

	@Transactional(readOnly = true)
	public CustomerMetricsResponse getMetrics() {
		return customerMapper.selectMetrics(organizationId);
	}

	@Transactional(readOnly = true)
	public List<OwnerOptionResponse> loadOwners() {
		return customerMapper.selectOwners(organizationId);
	}

	private LambdaQueryWrapper<Customer> buildQuery(
			String keyword,
			CustomerStage stage,
			CustomerStatus status) {
		var query = Wrappers.<Customer>lambdaQuery()
				.eq(Customer::getOrganizationId, organizationId)
				.isNull(Customer::getDeletedAt)
				.eq(stage != null, Customer::getStage, stage)
				.eq(status != null, Customer::getStatus, status)
				.orderByDesc(Customer::getUpdatedAt);

		if (StringUtils.hasText(keyword)) {
			var normalized = keyword.trim();
			query.and(group -> group
					.like(Customer::getName, normalized)
					.or()
					.like(Customer::getIndustry, normalized)
					.or()
					.like(Customer::getWebsite, normalized));
		}
		return query;
	}

	private Customer findEntity(UUID customerId) {
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

	private void apply(Customer customer, CustomerUpsertRequest request, Instant now) {
		var attributes = new HashMap<>(customer.getAttributes());
		putOrRemove(attributes, "score", request.score());
		putOrRemove(attributes, "estimatedValue", request.estimatedValue());
		putOrRemove(attributes, "nextAction", trimToNull(request.nextAction()));

		customer.update(
				request.name().trim(),
				trimToNull(request.website()),
				trimToNull(request.industry()),
				trimToNull(request.employeeRange()),
				Objects.requireNonNullElse(request.stage(), CustomerStage.LEAD),
				Objects.requireNonNullElse(request.status(), CustomerStatus.ACTIVE),
				Objects.requireNonNullElse(request.source(), CustomerSource.MANUAL),
				request.ownerMemberId(),
				request.nextFollowUpAt(),
				attributes,
				now);
	}

	private CustomerContact updatePrimaryContact(
			Customer customer,
			CustomerUpsertRequest request,
			Instant now) {
		var contact = contactMapper.selectOne(primaryContactQuery(customer.getId()).last("LIMIT 1"));
		var contactName = trimToNull(request.primaryContactName());
		if (contactName == null) {
			if (contact != null) {
				contact.archive(now);
				contactMapper.updateById(contact);
			}
			return null;
		}

		if (contact == null) {
			contact = CustomerContact.primary(
					organizationId,
					customer.getId(),
					contactName,
					trimToNull(request.primaryContactEmail()),
					trimToNull(request.primaryContactPhone()),
					now);
			contactMapper.insert(contact);
		}
		else {
			contact.update(
					contactName,
					trimToNull(request.primaryContactEmail()),
					trimToNull(request.primaryContactPhone()),
					now);
			contactMapper.updateById(contact);
		}
		return contact;
	}

	private LambdaQueryWrapper<CustomerContact> primaryContactQuery(UUID customerId) {
		return Wrappers.<CustomerContact>lambdaQuery()
				.eq(CustomerContact::getOrganizationId, organizationId)
				.eq(CustomerContact::getCustomerId, customerId)
				.eq(CustomerContact::isPrimary, true)
				.isNull(CustomerContact::getDeletedAt);
	}

	private Map<UUID, CustomerContact> loadPrimaryContacts(Collection<Customer> customers) {
		if (customers.isEmpty()) {
			return Map.of();
		}
		var customerIds = customers.stream().map(Customer::getId).toList();
		return contactMapper.selectList(Wrappers.<CustomerContact>lambdaQuery()
				.eq(CustomerContact::getOrganizationId, organizationId)
				.in(CustomerContact::getCustomerId, customerIds)
				.eq(CustomerContact::isPrimary, true)
				.isNull(CustomerContact::getDeletedAt))
				.stream()
				.collect(Collectors.toMap(CustomerContact::getCustomerId, Function.identity(), (left, right) -> left));
	}

	private CustomerResponse toResponse(
			Customer customer,
			CustomerContact contact,
			Map<UUID, String> owners) {
		var attributes = customer.getAttributes();
		var ownerName = customer.getOwnerMemberId() == null ? null : owners.get(customer.getOwnerMemberId());
		return new CustomerResponse(
				customer.getId(),
				customer.getName(),
				customer.getWebsite(),
				customer.getIndustry(),
				customer.getEmployeeRange(),
				customer.getStage(),
				customer.getStatus(),
				customer.getSource(),
				customer.getOwnerMemberId(),
				ownerName,
				toInteger(attributes.get("score")),
				toBigDecimal(attributes.get("estimatedValue")),
				toStringValue(attributes.get("nextAction")),
				customer.getLastInteractionAt(),
				customer.getNextFollowUpAt(),
				contact == null ? null : new PrimaryContactResponse(contact.getFullName(), contact.getEmail(), contact.getPhone()),
				customer.getCreatedAt(),
				customer.getUpdatedAt(),
				customer.getVersion());
	}

	private void validateOwner(UUID ownerMemberId) {
		if (ownerMemberId != null && loadOwners().stream().noneMatch(owner -> owner.id().equals(ownerMemberId))) {
			throw new CustomerValidationException("负责人不属于当前组织或账号已停用");
		}
	}

	private static void putOrRemove(Map<String, Object> attributes, String key, Object value) {
		if (value == null) attributes.remove(key);
		else attributes.put(key, value);
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private static Integer toInteger(Object value) {
		if (value instanceof Number number) return number.intValue();
		if (value instanceof String text && StringUtils.hasText(text)) return Integer.valueOf(text);
		return null;
	}

	private static BigDecimal toBigDecimal(Object value) {
		if (value instanceof BigDecimal decimal) return decimal;
		if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
		if (value instanceof String text && StringUtils.hasText(text)) return new BigDecimal(text);
		return null;
	}

	private static String toStringValue(Object value) {
		return value == null ? null : value.toString();
	}
}
