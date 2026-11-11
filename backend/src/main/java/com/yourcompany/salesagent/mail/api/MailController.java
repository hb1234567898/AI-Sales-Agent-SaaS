package com.yourcompany.salesagent.mail.api;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.mail.application.*;
import com.yourcompany.salesagent.mail.infrastructure.MailMessageRow;
import com.yourcompany.salesagent.interaction.api.ChatAnalysisResponse;
import com.yourcompany.salesagent.shared.api.PageResponse;

@RestController @Validated @PreAuthorize("isAuthenticated()") @RequestMapping("/api/v1/mail")
public class MailController {
    private final MailAccountService accounts; private final MailSyncService sync; private final MailConversationService conversations;
    public MailController(MailAccountService accounts,MailSyncService sync,MailConversationService conversations) { this.accounts=accounts; this.sync=sync; this.conversations=conversations; }
    @GetMapping("/account") public MailAccountResponse account(Authentication auth) { return accounts.status(actor(auth).organizationId()); }
    @PutMapping("/account") public MailAccountResponse save(Authentication auth,@Valid @RequestBody MailAccountRequest input) { return accounts.save(actor(auth),input); }
    @PostMapping("/account/test") public Map<String,String> test(Authentication auth) { accounts.test(actor(auth)); return Map.of("message","IMAP 连接成功"); }
    @PostMapping("/sync") public MailSyncService.SyncResult sync(Authentication auth) { var actor=actor(auth); MailAccess.admin(actor); return sync.sync(actor.organizationId()); }
    @GetMapping("/messages") public PageResponse<MailMessageRow> messages(Authentication auth,@RequestParam(defaultValue="PENDING") String filter,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return PageResponse.from(conversations.list(actor(auth),filter,page,size)); }
    @GetMapping("/messages/{id}") public MailMessageRow message(Authentication auth,@PathVariable UUID id) { return conversations.get(actor(auth),id); }
    @GetMapping("/messages/{id}/thread") public List<MailMessageRow> thread(Authentication auth,@PathVariable UUID id) { return conversations.thread(actor(auth),id); }
    @PostMapping("/messages/{id}/link") public MailMessageRow link(Authentication auth,@PathVariable UUID id,@Valid @RequestBody MailLinkRequest input) { return conversations.link(actor(auth),id,input); }
    @PostMapping("/messages/{id}/handle") public ResponseEntity<Void> handle(Authentication auth,@PathVariable UUID id,@Valid @RequestBody MailVersionRequest input) { conversations.handle(actor(auth),id,input.version()); return ResponseEntity.noContent().build(); }
    @PostMapping("/messages/{id}/analysis") public ChatAnalysisResponse analyze(Authentication auth,@PathVariable UUID id) { return conversations.analyze(actor(auth),id); }
    @PostMapping("/messages/{id}/reply") public ResponseEntity<MailConversationService.ReplyResult> reply(Authentication auth,@PathVariable UUID id,@Valid @RequestBody MailReplyRequest input) { return ResponseEntity.accepted().body(conversations.reply(actor(auth),id,input)); }
    private static AuthPrincipal actor(Authentication auth) { return (AuthPrincipal)auth.getPrincipal(); }
}
