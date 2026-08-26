package com.yourcompany.salesagent.customer.infrastructure;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yourcompany.salesagent.customer.api.CustomerMetricsResponse;
import com.yourcompany.salesagent.customer.api.OwnerOptionResponse;
import com.yourcompany.salesagent.customer.domain.Customer;

public interface CustomerMapper extends BaseMapper<Customer> {

	CustomerMetricsResponse selectMetrics(@Param("organizationId") UUID organizationId);

	List<OwnerOptionResponse> selectOwners(@Param("organizationId") UUID organizationId);
}
