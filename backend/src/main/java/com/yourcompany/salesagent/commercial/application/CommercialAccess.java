package com.yourcompany.salesagent.commercial.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.infrastructure.*;

@Component
public class CommercialAccess {
    private final CommercialMapper mapper;
    private final Clock clock;
    public CommercialAccess(CommercialMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }
    public static boolean manages(AuthPrincipal actor) { return Set.of("OWNER","ADMIN","MANAGER").contains(actor.role()); }
    public static UUID owner(AuthPrincipal actor) { return manages(actor) ? null : actor.memberId(); }
    public static void writer(AuthPrincipal actor) {
        if (!Set.of("OWNER","ADMIN","MANAGER","SALES").contains(actor.role())) throw new AccessDeniedException("当前账号只能查看销售单据");
    }
    public static void manager(AuthPrincipal actor) {
        if (!manages(actor)) throw new AccessDeniedException("该操作需要团队负责人权限");
    }
    public LocalDate today() { return LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")); }
    public CommercialOpportunityRow opportunity(AuthPrincipal actor, UUID id) {
        var row=mapper.opportunity(actor.organizationId(),id,owner(actor));
        if(row==null) throw missing("商机不存在或无权访问");
        return row;
    }
    public QuoteRow quote(AuthPrincipal actor, UUID id) {
        var row=mapper.quote(actor.organizationId(),id,owner(actor));
        if(row==null) throw missing("报价不存在或无权访问");
        return row;
    }
    public ContractRow contract(AuthPrincipal actor, UUID id) {
        var row=mapper.contract(actor.organizationId(),id,owner(actor));
        if(row==null) throw missing("合同不存在或无权访问");
        return row;
    }
    public void lockQuote(AuthPrincipal actor, UUID id, Long expected) {
        quote(actor,id);
        var actual=mapper.lockQuote(actor.organizationId(),id);
        version(actual,expected);
    }
    public void lockContract(AuthPrincipal actor, UUID id, Long expected) {
        contract(actor,id);
        version(mapper.lockContract(actor.organizationId(),id),expected);
    }
    public static void version(Long actual, Long expected) {
        if(expected==null || !expected.equals(actual)) throw new OptimisticLockingFailureException("单据已更新，请刷新后重试");
    }
    public static void changed(int count) { if(count!=1) throw new OptimisticLockingFailureException("单据已更新，请刷新后重试"); }
    public static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
}
