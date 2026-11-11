package com.yourcompany.salesagent.followup.api;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.application.FollowUpAutomationService;
import com.yourcompany.salesagent.followup.infrastructure.FollowUpAutomationRunRow;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/follow-ups")
public class FollowUpAutomationController {
    private final FollowUpAutomationService service;
    public FollowUpAutomationController(FollowUpAutomationService service) { this.service = service; }
    @GetMapping("/automation")
    public FollowUpRuleResponse rule(Authentication auth) { return service.rule(actor(auth)); }
    @PutMapping("/automation")
    public FollowUpRuleResponse save(Authentication auth, @Valid @RequestBody FollowUpRuleRequest input) { return service.saveRule(actor(auth), input); }
    @PostMapping("/automation/run")
    public ResponseEntity<FollowUpAutomationRunRow> run(Authentication auth) { return ResponseEntity.accepted().body(service.runNow(actor(auth))); }
    @GetMapping("/reminders")
    public FollowUpRemindersResponse reminders(Authentication auth) { return service.reminders(actor(auth)); }
    @PostMapping("/reminders/{id}/read")
    public ResponseEntity<Void> read(Authentication auth, @PathVariable UUID id) { service.read(actor(auth), id); return ResponseEntity.noContent().build(); }
    @PostMapping("/reminders/read-all")
    public ResponseEntity<Void> readAll(Authentication auth) { service.read(actor(auth), null); return ResponseEntity.noContent().build(); }
    private static AuthPrincipal actor(Authentication auth) { return (AuthPrincipal) auth.getPrincipal(); }
}
