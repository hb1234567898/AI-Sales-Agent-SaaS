package com.yourcompany.salesagent.auth.security;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

public final class BusinessAccess {
    private static final Set<String> WRITERS = Set.of("OWNER", "ADMIN", "MANAGER", "SALES");
    private BusinessAccess() { }

    public static UUID organizationId(AuthPrincipal actor) {
        if (actor == null || actor.organizationId() == null) throw new AccessDeniedException("请先登录");
        return actor.organizationId();
    }

    public static void requireWriter(AuthPrincipal actor) {
        organizationId(actor);
        if (actor.role() == null || !WRITERS.contains(actor.role())) throw new AccessDeniedException("当前账号仅可查看业务资料");
    }
}
