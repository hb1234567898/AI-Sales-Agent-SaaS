package com.yourcompany.salesagent.followup.application;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.api.*;
import com.yourcompany.salesagent.followup.infrastructure.*;

@Service
public class FollowUpService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> OPEN = Set.of("OPEN", "IN_PROGRESS", "SNOOZED");
    private final FollowUpMapper mapper;
    private final Clock clock;
    public FollowUpService(FollowUpMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    @Transactional(readOnly = true)
    public IPage<FollowUpResponse> findFollowUps(AuthPrincipal actor, String filter, String scope, int page, int size) {
        var normalized = filter == null ? "ALL" : filter.strip().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("ALL", "TODAY", "OVERDUE", "CLOSED").contains(normalized)) throw new FollowUpWorkflowException("不支持的任务筛选");
        var now = clock.instant();
        var day = now.atZone(BUSINESS_ZONE).toLocalDate();
        return mapper.selectFollowUps(Page.of(page + 1L, size), actor.organizationId(), scopedOwner(actor, scope), normalized,
                now, day.atStartOfDay(BUSINESS_ZONE).toInstant(), day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant())
                .convert(FollowUpResponse::from);
    }

    public FollowUpMetricsResponse metrics(AuthPrincipal actor, String scope) {
        var now = clock.instant();
        var day = now.atZone(BUSINESS_ZONE).toLocalDate();
        return mapper.selectMetrics(actor.organizationId(), scopedOwner(actor, scope), now,
                day.atStartOfDay(BUSINESS_ZONE).toInstant(), day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    public FollowUpOptionsResponse options(AuthPrincipal actor, String query) {
        var owner = managesTeam(actor) ? null : actor.memberId();
        return new FollowUpOptionsResponse(mapper.selectCustomers(actor.organizationId(), query, owner),
                mapper.selectOwners(actor.organizationId(), owner));
    }

    public FollowUpResponse get(AuthPrincipal actor, UUID id) { return FollowUpResponse.from(require(actor, id)); }

    @Transactional
    public FollowUpResponse create(AuthPrincipal actor, FollowUpUpsertRequest input) {
        validate(actor, input);
        var id = UUID.randomUUID();
        mapper.insert(actor.organizationId(), id, input, clock.instant());
        return get(actor, id);
    }

    @Transactional
    public FollowUpResponse update(AuthPrincipal actor, UUID id, FollowUpUpsertRequest input) {
        var row = requireWritable(actor, id);
        if (!row.getCustomerId().equals(input.customerId())) throw new FollowUpWorkflowException("任务关联客户不能更换");
        if (input.version() == null) throw new FollowUpWorkflowException("请刷新任务后重试");
        validate(actor, input);
        changed(mapper.update(actor.organizationId(), id, input, clock.instant()));
        return get(actor, id);
    }

    @Transactional
    public FollowUpResponse complete(AuthPrincipal actor, UUID id, Long version) {
        var row = requireWritable(actor, id);
        changed(mapper.changeStatus(actor.organizationId(), id, version == null ? row.getVersion() : version, "COMPLETED", clock.instant()));
        return get(actor, id);
    }

    @Transactional
    public FollowUpResponse cancel(AuthPrincipal actor, UUID id, long version) {
        requireWritable(actor, id);
        changed(mapper.changeStatus(actor.organizationId(), id, version, "CANCELLED", clock.instant()));
        return get(actor, id);
    }

    @Transactional
    public FollowUpResponse snooze(AuthPrincipal actor, UUID id, FollowUpSnoozeRequest input) {
        requireWritable(actor, id);
        if (!input.dueAt().isAfter(clock.instant())) throw new FollowUpWorkflowException("延期时间必须晚于当前时间");
        changed(mapper.snooze(actor.organizationId(), id, input, clock.instant()));
        return get(actor, id);
    }

    private FollowUpRow require(AuthPrincipal actor, UUID id) {
        var row = mapper.selectFollowUp(actor.organizationId(), id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "跟进任务不存在或已被删除");
        if (!managesTeam(actor) && row.getOwnerMemberId() != null && !actor.memberId().equals(row.getOwnerMemberId()))
            throw new AccessDeniedException("只能查看自己的跟进任务");
        return row;
    }

    private FollowUpRow requireWritable(AuthPrincipal actor, UUID id) {
        requireWriter(actor);
        var row = require(actor, id);
        if (!OPEN.contains(row.getStatus())) throw new FollowUpWorkflowException("任务已结束，不能再次修改");
        return row;
    }

    private void validate(AuthPrincipal actor, FollowUpUpsertRequest input) {
        requireWriter(actor);
        if (!managesTeam(actor) && !actor.memberId().equals(input.ownerMemberId())) throw new AccessDeniedException("只能为自己分配跟进任务");
        if (mapper.customerExists(actor.organizationId(), input.customerId(), managesTeam(actor) ? null : actor.memberId()) != 1)
            throw new FollowUpWorkflowException("客户不存在、已停用或无权跟进");
        if (mapper.ownerExists(actor.organizationId(), input.ownerMemberId()) != 1) throw new FollowUpWorkflowException("负责人不存在或不可分配任务");
    }

    public static void requireWriter(AuthPrincipal actor) {
        if (!Set.of("OWNER", "ADMIN", "MANAGER", "SALES").contains(actor.role())) throw new AccessDeniedException("当前账号只能查看跟进任务");
    }
    public static boolean managesTeam(AuthPrincipal actor) { return Set.of("OWNER", "ADMIN", "MANAGER").contains(actor.role()); }
    private static UUID scopedOwner(AuthPrincipal actor, String scope) {
        if ("TEAM".equals(scope)) {
            if (!managesTeam(actor)) throw new AccessDeniedException("无权查看团队任务");
            return null;
        }
        if (!"MINE".equals(scope)) throw new FollowUpWorkflowException("不支持的任务范围");
        return actor.memberId();
    }
    private static void changed(int count) {
        if (count != 1) throw new OptimisticLockingFailureException("任务已被更新，请刷新后重试");
    }
}
