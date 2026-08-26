package com.yourcompany.salesagent.interaction.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.customer.application.CustomerNotFoundException;
import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.infrastructure.CustomerMapper;
import com.yourcompany.salesagent.interaction.api.ChatImportRequest;
import com.yourcompany.salesagent.interaction.api.InteractionCreateRequest;
import com.yourcompany.salesagent.interaction.api.InteractionResponse;
import com.yourcompany.salesagent.interaction.domain.Interaction;
import com.yourcompany.salesagent.interaction.domain.InteractionDirection;
import com.yourcompany.salesagent.interaction.domain.InteractionType;
import com.yourcompany.salesagent.interaction.infrastructure.InteractionMapper;

@Service
public class InteractionService {

	private static final EnumSet<InteractionType> MANUAL_TYPES = EnumSet.of(
			InteractionType.EMAIL_SENT,
			InteractionType.EMAIL_RECEIVED,
			InteractionType.CALL,
			InteractionType.MEETING,
			InteractionType.NOTE);

	private final InteractionMapper interactionMapper;
	private final CustomerMapper customerMapper;
	private final Clock clock;
	private final UUID organizationId;

	public InteractionService(
			InteractionMapper interactionMapper,
			CustomerMapper customerMapper,
			Clock clock,
			@Value("${app.demo.organization-id}") UUID organizationId) {
		this.interactionMapper = interactionMapper;
		this.customerMapper = customerMapper;
		this.clock = clock;
		this.organizationId = organizationId;
	}

	@Transactional(readOnly = true)
	public IPage<InteractionResponse> findTimeline(UUID customerId, int page, int size) {
		requireCustomer(customerId);
		var interactionPage = interactionMapper.selectPage(
				Page.of(page + 1L, size),
				Wrappers.<Interaction>lambdaQuery()
						.eq(Interaction::getOrganizationId, organizationId)
						.eq(Interaction::getCustomerId, customerId)
						.orderByDesc(Interaction::getOccurredAt)
						.orderByDesc(Interaction::getId));
		return new Page<InteractionResponse>(
				interactionPage.getCurrent(),
				interactionPage.getSize(),
				interactionPage.getTotal())
						.setRecords(interactionPage.getRecords().stream().map(InteractionResponse::from).toList());
	}

	@Transactional
	public InteractionResponse create(UUID customerId, InteractionCreateRequest request) {
		requireCustomer(customerId);
		if (!MANUAL_TYPES.contains(request.type())) {
			throw new InteractionValidationException("该互动类型不能通过人工记录创建");
		}
		validateOccurredAt(request.occurredAt());
		var now = clock.instant();
		var participant = trimToNull(request.participantName());
		var interaction = Interaction.create(
				organizationId,
				customerId,
				request.type(),
				request.direction(),
				request.occurredAt(),
				trimToNull(request.subject()),
				request.bodyText().strip(),
				participant == null ? List.of() : List.of(participant),
				"INTERNAL",
				Map.of("entryMode", "MANUAL"),
				now);
		interactionMapper.insert(interaction);
		updateCustomerTimeline(customerId, request.occurredAt(), now);
		return InteractionResponse.from(interaction);
	}

	@Transactional
	public InteractionResponse importChat(UUID customerId, ChatImportRequest request) {
		requireCustomer(customerId);
		validateOccurredAt(request.occurredAt());
		var now = clock.instant();
		var participant = trimToNull(request.participantName());
		var subject = trimToNull(request.subject());
		var content = request.content().strip();
		var interaction = Interaction.create(
				organizationId,
				customerId,
				InteractionType.CHAT_IMPORT,
				InteractionDirection.NONE,
				request.occurredAt(),
				subject == null ? request.platform().displayName() + "聊天记录" : subject,
				content,
				participant == null ? List.of() : List.of(participant),
				request.platform().name(),
				Map.of(
						"entryMode", "PASTE_IMPORT",
						"format", "PLAIN_TEXT",
						"lineCount", content.lines().count()),
				now);
		interactionMapper.insert(interaction);
		updateCustomerTimeline(customerId, request.occurredAt(), now);
		return InteractionResponse.from(interaction);
	}

	private void requireCustomer(UUID customerId) {
		var exists = customerMapper.selectCount(Wrappers.<Customer>lambdaQuery()
				.eq(Customer::getId, customerId)
				.eq(Customer::getOrganizationId, organizationId)
				.isNull(Customer::getDeletedAt)) > 0;
		if (!exists) {
			throw new CustomerNotFoundException(customerId);
		}
	}

	private void validateOccurredAt(Instant occurredAt) {
		if (occurredAt.isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
			throw new InteractionValidationException("互动发生时间不能晚于当前时间");
		}
	}

	private void updateCustomerTimeline(UUID customerId, Instant occurredAt, Instant now) {
		customerMapper.update(null, Wrappers.<Customer>lambdaUpdate()
				.eq(Customer::getId, customerId)
				.eq(Customer::getOrganizationId, organizationId)
				.isNull(Customer::getDeletedAt)
				.and(group -> group.isNull(Customer::getLastInteractionAt)
						.or()
						.lt(Customer::getLastInteractionAt, occurredAt))
				.set(Customer::getLastInteractionAt, occurredAt)
				.set(Customer::getUpdatedAt, now));
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
