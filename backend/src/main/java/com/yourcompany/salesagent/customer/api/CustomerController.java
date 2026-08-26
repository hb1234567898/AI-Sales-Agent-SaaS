package com.yourcompany.salesagent.customer.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.salesagent.customer.application.CustomerService;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;
import com.yourcompany.salesagent.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping
	public PageResponse<CustomerResponse> findCustomers(
			@RequestParam(defaultValue = "") String query,
			@RequestParam(required = false) CustomerStage stage,
			@RequestParam(required = false) CustomerStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(customerService.findCustomers(query, stage, status, page, size));
	}

	@GetMapping("/{customerId}")
	public CustomerResponse findCustomer(@PathVariable UUID customerId) {
		return customerService.findCustomer(customerId);
	}

	@GetMapping("/metrics")
	public CustomerMetricsResponse getMetrics() {
		return customerService.getMetrics();
	}

	@GetMapping("/owners")
	public List<OwnerOptionResponse> getOwners() {
		return customerService.loadOwners();
	}

	@PostMapping
	public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerUpsertRequest request) {
		var customer = customerService.createCustomer(request);
		return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
	}

	@PutMapping("/{customerId}")
	public CustomerResponse updateCustomer(
			@PathVariable UUID customerId,
			@Valid @RequestBody CustomerUpsertRequest request) {
		return customerService.updateCustomer(customerId, request);
	}
}
