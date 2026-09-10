package com.yourcompany.salesagent.tool.email.api;

import java.util.UUID;

import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.tool.email.EmailConfigurationService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email/settings")
public class EmailSettingsController {

	private final EmailConfigurationService service;
	private final UUID demoOrganizationId;

	public EmailSettingsController(
			EmailConfigurationService service,
			@Value("${app.demo.organization-id}") UUID demoOrganizationId) {
		this.service = service;
		this.demoOrganizationId = demoOrganizationId;
	}

	@GetMapping
	public EmailSettingsStatusResponse status(Authentication authentication) {
		return service.status(organizationId(authentication));
	}

	@PutMapping
	public EmailSettingsStatusResponse update(
			Authentication authentication,
			@Valid @RequestBody EmailSettingsUpdateRequest request) {
		return service.update(organizationId(authentication), request);
	}

	@PostMapping("/test")
	public EmailSettingsTestResponse testConnection(Authentication authentication) {
		return service.testConnection(organizationId(authentication));
	}

	private UUID organizationId(Authentication authentication) {
		return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal
				? principal.organizationId()
				: demoOrganizationId;
	}
}
