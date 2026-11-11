package com.yourcompany.salesagent.mail.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface MailMapper {
    MailAccountRow account(@Param("org") UUID org);
    int insertAccount(@Param("row") MailAccountRow row, @Param("now") Instant now);
    int updateAccount(@Param("row") MailAccountRow row, @Param("version") long version, @Param("now") Instant now);
    List<UUID> dueAccounts(@Param("now") Instant now);
    int claim(@Param("org") UUID org, @Param("token") UUID token, @Param("now") Instant now);
    int finishSync(@Param("org") UUID org, @Param("token") UUID token, @Param("validity") long validity, @Param("uid") long uid, @Param("error") String error, @Param("now") Instant now);
    int insertMessage(@Param("row") MailMessageRow row);
    MailMessageRow message(@Param("org") UUID org, @Param("id") UUID id);
    MailMessageRow parent(@Param("org") UUID org, @Param("references") List<String> references, @Param("sender") String sender, @Param("recipients") List<String> recipients);
    List<MailCustomerRow> customersByEmail(@Param("org") UUID org, @Param("email") String email);
    MailCustomerRow customer(@Param("org") UUID org, @Param("id") UUID id);
    IPage<MailMessageRow> page(Page<MailMessageRow> page, @Param("org") UUID org, @Param("owner") UUID owner, @Param("filter") String filter);
    List<MailMessageRow> thread(@Param("org") UUID org, @Param("thread") UUID thread, @Param("owner") UUID owner);
    void insertInteraction(@Param("row") MailMessageRow row);
    void attachInteraction(@Param("org") UUID org, @Param("id") UUID id, @Param("interaction") UUID interaction);
    void touchCustomer(@Param("row") MailMessageRow row);
    void createTask(@Param("row") MailMessageRow row, @Param("now") Instant now);
    int link(@Param("org") UUID org, @Param("id") UUID id, @Param("customer") MailCustomerRow customer, @Param("interaction") UUID interaction, @Param("version") long version);
    int handle(@Param("org") UUID org, @Param("id") UUID id, @Param("version") long version);
    void completeTask(@Param("org") UUID org, @Param("id") UUID id, @Param("now") Instant now);
    int reserveReply(@Param("org") UUID org, @Param("id") UUID id, @Param("version") long version);
    void attachApproval(@Param("org") UUID org, @Param("id") UUID id, @Param("approval") UUID approval);
    int deliveryStatus(@Param("org") UUID org, @Param("id") UUID id, @Param("status") String status);
    void replied(@Param("org") UUID org, @Param("id") UUID id);
    int expireSending(@Param("now") Instant now);
}
