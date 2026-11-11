package com.yourcompany.salesagent.followup.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.yourcompany.salesagent.agent.api.AgentRunCreateRequest;
import com.yourcompany.salesagent.agent.application.SalesFollowUpAgentService;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.followup.api.*;
import com.yourcompany.salesagent.followup.infrastructure.*;

@Service
public class FollowUpAutomationService {
    private static final Logger LOG = LoggerFactory.getLogger(FollowUpAutomationService.class);
    private final FollowUpAutomationMapper mapper;
    private final SalesFollowUpAgentService agent;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public FollowUpAutomationService(FollowUpAutomationMapper mapper, SalesFollowUpAgentService agent,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.mapper = mapper;
        this.agent = agent;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public FollowUpRuleResponse rule(AuthPrincipal actor) {
        var rule = mapper.selectRule(actor.organizationId(), actor.memberId());
        return FollowUpRuleResponse.from(rule == null ? new FollowUpRuleRow() : rule,
                mapper.selectLatestRun(actor.organizationId(), actor.memberId()));
    }

    @Transactional
    public FollowUpRuleResponse saveRule(AuthPrincipal actor, FollowUpRuleRequest input) {
        FollowUpService.requireWriter(actor);
        if (mapper.selectActor(actor.organizationId(), actor.memberId()) == null) throw new FollowUpWorkflowException("当前成员不能配置自动跟进");
        int count = input.version() == -1
                ? mapper.insertRule(actor.organizationId(), actor.memberId(), input, clock.instant())
                : mapper.updateRule(actor.organizationId(), actor.memberId(), input, clock.instant());
        if (count != 1) throw new OptimisticLockingFailureException("跟进规则已更新，请刷新后重试");
        return rule(actor);
    }

    public FollowUpAutomationRunRow runNow(AuthPrincipal actor) {
        FollowUpService.requireWriter(actor);
        return prepare(actor.organizationId(), actor.memberId());
    }

    public void scan() {
        var now = clock.instant();
        mapper.createReminders(null, null, now);
        mapper.expireRuns(now.minus(Duration.ofHours(2)), now);
        var local = now.atZone(FollowUpService.BUSINESS_ZONE);
        for (var rule : mapper.selectDueRules(local.toLocalDate(), local.getHour())) {
            try { prepare(rule.getOrganizationId(), rule.getMemberId()); }
            catch (RuntimeException error) { LOG.warn("Proactive follow-up scan failed for rule {}", rule.getId(), error); }
        }
    }

    private FollowUpAutomationRunRow prepare(UUID org, UUID member) {
        return transactions.execute(transaction -> {
            var rule = mapper.lockRule(org, member);
            if (rule == null || !rule.isEnabled()) throw new FollowUpWorkflowException("请先保存并开启自动跟进规则");
            if (mapper.selectActor(org, member) == null) throw new FollowUpWorkflowException("成员已停用，不能执行自动跟进");
            var now = clock.instant();
            var day = now.atZone(FollowUpService.BUSINESS_ZONE).toLocalDate();
            var id = UUID.randomUUID();
            if (mapper.insertRun(id, rule, day, now) == 1) {
                int count = mapper.createTasks(id, rule, now, now.minus(Duration.ofDays(rule.getInactiveDays())));
                mapper.prepared(org, id, count, count > 0 && rule.isAutoAnalyze() ? "QUEUED" : "COMPLETED", now);
            }
            return mapper.selectRun(org, member, day);
        });
    }

    public java.util.List<FollowUpAutomationRunRow> queuedRuns() { return mapper.selectQueuedRuns(); }

    public void process(FollowUpAutomationRunRow batch) {
        var org = batch.getOrganizationId();
        var id = batch.getId();
        if (mapper.claimRun(org, id, clock.instant()) != 1) return;
        try {
            var rule = mapper.selectRule(org, batch.getMemberId());
            var actor = mapper.selectActor(org, batch.getMemberId());
            if (rule == null || !rule.isEnabled() || !rule.isAutoAnalyze() || actor == null) {
                mapper.finishRun(org, id, "CANCELLED", null, "规则已关闭或成员已停用，已取消排队的 AI 分析。", clock.instant());
                return;
            }
            var customers = mapper.selectRunCustomers(org, id);
            if (customers.isEmpty()) {
                mapper.finishRun(org, id, "COMPLETED", null, null, clock.instant());
                return;
            }
            var result = agent.runScheduled(actor.principal(clock.instant()),
                    new AgentRunCreateRequest(customers.size(), 180, customers), id);
            mapper.finishRun(org, id, result.failedCount() > 0 ? "FAILED" : "COMPLETED", result.id(),
                    result.failedCount() > 0 ? "部分 AI 分析失败，请查看 Agent 运行记录及模型额度。" : null, clock.instant());
        } catch (RuntimeException error) {
            LOG.warn("Proactive follow-up AI analysis failed for batch {}", id, error);
            mapper.finishRun(org, id, "FAILED", null, "AI 分析失败，请检查模型配置、额度及 Agent 运行记录。", clock.instant());
        }
    }

    @Transactional
    public FollowUpRemindersResponse reminders(AuthPrincipal actor) {
        var now = clock.instant();
        mapper.createReminders(actor.organizationId(), actor.memberId(), now);
        return new FollowUpRemindersResponse(mapper.countReminders(actor.organizationId(), actor.memberId(), now),
                mapper.selectReminders(actor.organizationId(), actor.memberId(), now));
    }

    public void read(AuthPrincipal actor, UUID id) { mapper.readReminder(actor.organizationId(), actor.memberId(), id, clock.instant()); }
}
