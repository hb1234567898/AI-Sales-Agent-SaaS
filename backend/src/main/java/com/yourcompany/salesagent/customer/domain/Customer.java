package com.yourcompany.salesagent.customer.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.yourcompany.salesagent.shared.persistence.JsonbMapTypeHandler;

@TableName(value = "customer", autoResultMap = true)
public class Customer {

	@TableId(type = IdType.INPUT)
	private UUID id;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("owner_member_id")
	private UUID ownerMemberId;

	private String name;

	private String website;

	private String industry;

	@TableField("employee_range")
	private String employeeRange;

	private CustomerStage stage;

	private CustomerStatus status;

	private CustomerSource source;

	@TableField("external_id")
	private String externalId;

	@TableField("last_interaction_at")
	private Instant lastInteractionAt;

	@TableField("next_follow_up_at")
	private Instant nextFollowUpAt;

	@TableField(typeHandler = JsonbMapTypeHandler.class)
	private Map<String, Object> attributes = new HashMap<>();

	@TableField("created_at")
	private Instant createdAt;

	@TableField("updated_at")
	private Instant updatedAt;

	@TableField("deleted_at")
	private Instant deletedAt;

	@Version
	private long version;

	protected Customer() {
	}

	public static Customer create(UUID organizationId, String name, Instant now) {
		var customer = new Customer();
		customer.id = UUID.randomUUID();
		customer.organizationId = organizationId;
		customer.name = name;
		customer.stage = CustomerStage.LEAD;
		customer.status = CustomerStatus.ACTIVE;
		customer.source = CustomerSource.MANUAL;
		customer.createdAt = now;
		customer.updatedAt = now;
		return customer;
	}

	public void update(
			String name,
			String website,
			String industry,
			String employeeRange,
			CustomerStage stage,
			CustomerStatus status,
			CustomerSource source,
			UUID ownerMemberId,
			Instant nextFollowUpAt,
			Map<String, Object> attributes,
			Instant now) {
		this.name = name;
		this.website = website;
		this.industry = industry;
		this.employeeRange = employeeRange;
		this.stage = stage;
		this.status = status;
		this.source = source;
		this.ownerMemberId = ownerMemberId;
		this.nextFollowUpAt = nextFollowUpAt;
		this.attributes = new HashMap<>(attributes);
		this.updatedAt = now;
	}

	public void recordInteractionAt(Instant interactionAt) {
		this.lastInteractionAt = interactionAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getOwnerMemberId() {
		return ownerMemberId;
	}

	public String getName() {
		return name;
	}

	public String getWebsite() {
		return website;
	}

	public String getIndustry() {
		return industry;
	}

	public String getEmployeeRange() {
		return employeeRange;
	}

	public CustomerStage getStage() {
		return stage;
	}

	public CustomerStatus getStatus() {
		return status;
	}

	public CustomerSource getSource() {
		return source;
	}

	public Instant getLastInteractionAt() {
		return lastInteractionAt;
	}

	public Instant getNextFollowUpAt() {
		return nextFollowUpAt;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public long getVersion() {
		return version;
	}
}
