package com.yourcompany.salesagent.followup.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import com.yourcompany.salesagent.followup.api.FollowUpRuleRequest;

public interface FollowUpAutomationMapper {
    FollowUpRuleRow selectRule(@Param("org") UUID org, @Param("member") UUID member);
    FollowUpRuleRow lockRule(@Param("org") UUID org, @Param("member") UUID member);
    int insertRule(@Param("org") UUID org, @Param("member") UUID member, @Param("input") FollowUpRuleRequest input, @Param("now") Instant now);
    int updateRule(@Param("org") UUID org, @Param("member") UUID member, @Param("input") FollowUpRuleRequest input, @Param("now") Instant now);
    List<FollowUpRuleRow> selectDueRules(@Param("day") LocalDate day, @Param("hour") int hour);
    FollowUpActorRow selectActor(@Param("org") UUID org, @Param("member") UUID member);
    int insertRun(@Param("id") UUID id, @Param("rule") FollowUpRuleRow rule, @Param("day") LocalDate day, @Param("now") Instant now);
    int createTasks(@Param("id") UUID id, @Param("rule") FollowUpRuleRow rule, @Param("now") Instant now, @Param("cutoff") Instant cutoff);
    List<UUID> selectRunCustomers(@Param("org") UUID org, @Param("id") UUID id);
    int prepared(@Param("org") UUID org, @Param("id") UUID id, @Param("count") int count, @Param("status") String status, @Param("now") Instant now);
    FollowUpAutomationRunRow selectRun(@Param("org") UUID org, @Param("member") UUID member, @Param("day") LocalDate day);
    FollowUpAutomationRunRow selectLatestRun(@Param("org") UUID org, @Param("member") UUID member);
    List<FollowUpAutomationRunRow> selectQueuedRuns();
    int claimRun(@Param("org") UUID org, @Param("id") UUID id, @Param("now") Instant now);
    int finishRun(@Param("org") UUID org, @Param("id") UUID id, @Param("status") String status,
            @Param("agentRunId") UUID agentRunId, @Param("error") String error, @Param("now") Instant now);
    int expireRuns(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
    int createReminders(@Param("org") UUID org, @Param("member") UUID member, @Param("now") Instant now);
    List<FollowUpReminderRow> selectReminders(@Param("org") UUID org, @Param("member") UUID member, @Param("now") Instant now);
    long countReminders(@Param("org") UUID org, @Param("member") UUID member, @Param("now") Instant now);
    int readReminder(@Param("org") UUID org, @Param("member") UUID member, @Param("id") UUID id, @Param("now") Instant now);
}
