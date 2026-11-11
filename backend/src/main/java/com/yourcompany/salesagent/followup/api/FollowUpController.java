package com.yourcompany.salesagent.followup.api;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.application.FollowUpService;
import com.yourcompany.salesagent.shared.api.PageResponse;

@Validated
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/follow-ups")
public class FollowUpController {
    private final FollowUpService service;
    public FollowUpController(FollowUpService service) { this.service = service; }
    @GetMapping
    public PageResponse<FollowUpResponse> find(Authentication auth,
            @RequestParam(defaultValue = "ALL") String filter, @RequestParam(defaultValue = "MINE") String scope,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.findFollowUps(actor(auth), filter, scope, page, size));
    }
    @GetMapping("/metrics")
    public FollowUpMetricsResponse metrics(Authentication auth, @RequestParam(defaultValue = "MINE") String scope) { return service.metrics(actor(auth), scope); }
    @GetMapping("/options")
    public FollowUpOptionsResponse options(Authentication auth, @RequestParam(defaultValue = "") @Size(max = 200) String query) { return service.options(actor(auth), query); }
    @GetMapping("/{id}")
    public FollowUpResponse get(Authentication auth, @PathVariable UUID id) { return service.get(actor(auth), id); }
    @PostMapping
    public ResponseEntity<FollowUpResponse> create(Authentication auth, @Valid @RequestBody FollowUpUpsertRequest input) {
        var result = service.create(actor(auth), input);
        return ResponseEntity.created(URI.create("/api/v1/follow-ups/" + result.id())).body(result);
    }
    @PutMapping("/{id}")
    public FollowUpResponse update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody FollowUpUpsertRequest input) { return service.update(actor(auth), id, input); }
    @PostMapping("/{id}/complete")
    public FollowUpResponse complete(Authentication auth, @PathVariable UUID id, @Valid @RequestBody(required = false) FollowUpVersionRequest input) {
        return service.complete(actor(auth), id, input == null ? null : input.version());
    }
    @PostMapping("/{id}/cancel")
    public FollowUpResponse cancel(Authentication auth, @PathVariable UUID id, @Valid @RequestBody FollowUpVersionRequest input) { return service.cancel(actor(auth), id, input.version()); }
    @PostMapping("/{id}/snooze")
    public FollowUpResponse snooze(Authentication auth, @PathVariable UUID id, @Valid @RequestBody FollowUpSnoozeRequest input) { return service.snooze(actor(auth), id, input); }
    private static AuthPrincipal actor(Authentication auth) { return (AuthPrincipal) auth.getPrincipal(); }
}
