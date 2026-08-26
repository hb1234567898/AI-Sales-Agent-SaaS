package com.yourcompany.salesagent.customer.application;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {

	public CustomerNotFoundException(UUID customerId) {
		super("客户不存在或已被删除：" + customerId);
	}
}
