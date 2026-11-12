package com.yourcompany.salesagent.customer.api;

import java.net.URI;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
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
@PreAuthorize("isAuthenticated()")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping
	public PageResponse<CustomerResponse> findCustomers(
			@AuthenticationPrincipal AuthPrincipal actor,
			@RequestParam(defaultValue = "") String query,
			@RequestParam(required = false) CustomerStage stage,
			@RequestParam(required = false) CustomerStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(customerService.findCustomers(actor, query, stage, status, page, size));
	}

	@GetMapping("/{customerId}")
	public CustomerResponse findCustomer(@AuthenticationPrincipal AuthPrincipal actor, @PathVariable UUID customerId) {
		return customerService.findCustomer(actor, customerId);
	}

	@GetMapping("/metrics")
	public CustomerMetricsResponse getMetrics(@AuthenticationPrincipal AuthPrincipal actor) {
		return customerService.getMetrics(actor);
	}

	@GetMapping("/owners")
	public List<OwnerOptionResponse> getOwners(@AuthenticationPrincipal AuthPrincipal actor) {
		return customerService.loadOwners(actor);
	}

	@PostMapping
	public ResponseEntity<CustomerResponse> createCustomer(@AuthenticationPrincipal AuthPrincipal actor, @Valid @RequestBody CustomerUpsertRequest request) {
		var customer = customerService.createCustomer(actor, request);
		return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
	}

	@PutMapping("/{customerId}")
	public CustomerResponse updateCustomer(
			@AuthenticationPrincipal AuthPrincipal actor,
			@PathVariable UUID customerId,
			@Valid @RequestBody CustomerUpsertRequest request) {
		return customerService.updateCustomer(actor, customerId, request);
	}

    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> deleteCustomer(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID customerId, @RequestParam @Min(0) long version) {
        customerService.deleteCustomer(principal, customerId, version);
        return ResponseEntity.noContent().build();
    }
}
