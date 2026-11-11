package com.yourcompany.salesagent.mail.application;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.mail.api.*;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.mail.transport.ImapMailboxClient;
import com.yourcompany.salesagent.shared.security.SecretCipher;
import com.yourcompany.salesagent.tool.email.EmailConnectionException;

@Service
public class MailAccountService {
    private final MailMapper mapper; private final SecretCipher cipher; private final ImapMailboxClient client; private final Clock clock;
    public MailAccountService(MailMapper mapper, SecretCipher cipher, ImapMailboxClient client, Clock clock) { this.mapper=mapper; this.cipher=cipher; this.client=client; this.clock=clock; }
    public MailAccountResponse status(UUID org) {
        var row = mapper.account(org);
        return row == null ? new MailAccountResponse("", 993, "", true, "INBOX", false, false, -1, null, null, false)
            : new MailAccountResponse(row.getHost(), row.getPort(), row.getUsername(), row.isSsl(), row.getFolder(), row.isEnabled(),
                row.getEncryptedPassword() != null, row.getVersion(), row.getLastSyncAt(), row.getLastError(), row.getLeaseUntil() != null && row.getLeaseUntil().isAfter(clock.instant()));
    }
    @Transactional
    public MailAccountResponse save(AuthPrincipal actor, MailAccountRequest input) {
        MailAccess.admin(actor); var old = mapper.account(actor.organizationId());
        var host = input.host().strip().toLowerCase(Locale.ROOT); var username = input.username().strip().toLowerCase(Locale.ROOT);
        boolean sameIdentity = old != null && old.getHost().equals(host) && old.getUsername().equals(username);
        var password = input.password();
        if ((password == null || password.isBlank()) && !sameIdentity) throw new MailWorkflowException("首次配置或更换邮箱服务器/账号时必须重新输入授权码");
        if (input.folder().chars().anyMatch(Character::isISOControl)) throw new MailWorkflowException("收信文件夹格式不正确");
        var row = new MailAccountRow(); row.setOrganizationId(actor.organizationId()); row.setHost(host); row.setUsername(username);
        row.setEncryptedPassword(password != null && !password.isBlank() ? cipher.encrypt(actor.organizationId(), password.strip()) : old.getEncryptedPassword());
        row.setPort(input.port()); row.setSsl(input.ssl()); row.setFolder(input.folder().strip()); row.setEnabled(input.enabled());
        int changed = input.version() == -1 ? mapper.insertAccount(row, clock.instant()) : mapper.updateAccount(row, input.version(), clock.instant());
        if (changed != 1) throw new OptimisticLockingFailureException("邮箱配置已变化或正在同步，请稍后刷新重试");
        return status(actor.organizationId());
    }
    public void test(AuthPrincipal actor) {
        MailAccess.admin(actor); var row = require(actor.organizationId());
        try { client.test(row, password(row)); } catch (Exception error) { throw new EmailConnectionException("IMAP 连接失败，请检查地址、端口、授权码与 TLS 设置", error); }
    }
    public MailAccountRow require(UUID org) { var row=mapper.account(org); if(row==null) throw new MailWorkflowException("请先配置收信邮箱"); return row; }
    public String password(MailAccountRow row) { return cipher.decrypt(row.getOrganizationId(), row.getEncryptedPassword()); }
}
