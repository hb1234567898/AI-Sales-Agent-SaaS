package com.yourcompany.salesagent.mail.application;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
public final class MailAccess {
    private MailAccess() {}
    public static boolean manages(AuthPrincipal actor) { return Set.of("OWNER", "ADMIN", "MANAGER").contains(actor.role()); }
    public static UUID owner(AuthPrincipal actor) { return manages(actor) ? null : actor.memberId(); }
    public static void admin(AuthPrincipal actor) { if (!Set.of("OWNER", "ADMIN").contains(actor.role())) throw new AccessDeniedException("仅管理员可配置和同步团队邮箱"); }
    public static void writer(AuthPrincipal actor) { if (!Set.of("OWNER", "ADMIN", "MANAGER", "SALES").contains(actor.role())) throw new AccessDeniedException("当前账号只能查看邮件"); }
}
