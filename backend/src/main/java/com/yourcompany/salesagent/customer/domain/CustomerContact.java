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

@TableName(value = "customer_contact", autoResultMap = true)
public class CustomerContact {

	@TableId(type = IdType.INPUT)
	private UUID id;

	@TableField("organization_id")
	private UUID organizationId;

	@TableField("customer_id")
	private UUID customerId;

	@TableField("full_name")
	private String fullName;

	private String email;

	private String phone;

	@TableField("is_primary")
	private boolean primary;

	private String source;

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

	protected CustomerContact() {
	}

	public static CustomerContact primary(
			UUID organizationId,
			UUID customerId,
			String fullName,
			String email,
			String phone,
			Instant now) {
		var contact = new CustomerContact();
		contact.id = UUID.randomUUID();
		contact.organizationId = organizationId;
		contact.customerId = customerId;
		contact.primary = true;
		contact.source = "MANUAL";
		contact.createdAt = now;
		contact.update(fullName, email, phone, now);
		return contact;
	}

	public void update(String fullName, String email, String phone, Instant now) {
		this.fullName = fullName;
		this.email = email;
		this.phone = phone;
		this.deletedAt = null;
		this.updatedAt = now;
	}

	public void archive(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public boolean isPrimary() {
		return primary;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}
}
