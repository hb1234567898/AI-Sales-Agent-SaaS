package com.yourcompany.salesagent.mail.application;

import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.mail.transport.*;
import com.yourcompany.salesagent.tool.email.EmailConnectionException;

@Service
public class MailSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(MailSyncService.class);
    private final MailMapper mapper; private final MailAccountService accounts; private final ImapMailboxClient client;
    private final TransactionTemplate transactions; private final Clock clock;
    public MailSyncService(MailMapper mapper, MailAccountService accounts, ImapMailboxClient client, PlatformTransactionManager manager, Clock clock) {
        this.mapper=mapper; this.accounts=accounts; this.client=client; this.transactions=new TransactionTemplate(manager); this.clock=clock;
    }
    public record SyncResult(int imported, int unmatched, boolean busy) {}
    public SyncResult sync(UUID org) {
        var token=UUID.randomUUID(); var account=accounts.require(org);
        if (!account.isEnabled()) throw new MailWorkflowException("请先开启收信同步");
        if (mapper.claim(org, token, clock.instant()) != 1) return new SyncResult(0,0,true);
        account=accounts.require(org);
        long validity=account.getUidValidity(), uid=account.getLastUid();
        try {
            var batch=client.fetch(account, accounts.password(account), clock.instant());
            int count=0, unmatched=0;
            for(var message:batch.messages()) {
                var result=transactions.execute(status -> importMessage(org, message));
                if(result!=null && result>=0) { count++; if(result==0) unmatched++; }
            }
            validity=batch.validity(); uid=batch.lastUid();
            mapper.finishSync(org,token,validity,uid,null,clock.instant());
            return new SyncResult(count,unmatched,false);
        } catch(Exception error) {
            LOG.warn("Mailbox synchronization failed for organization {} ({})", org, error.getClass().getSimpleName());
            mapper.finishSync(org,token,validity,uid,"收信失败，请检查 IMAP 配置、网络和授权码后重试。",clock.instant());
            throw new EmailConnectionException("收信失败，请检查 IMAP 配置、网络和授权码后重试",error);
        }
    }
    int importMessage(UUID org, InboundMail input) {
        if(input.fromAddress().isBlank() || input.recipients().isEmpty()) return -1;
        var now=clock.instant(); var row=new MailMessageRow(); row.setId(UUID.randomUUID()); row.setOrganizationId(org);
        row.setDirection("INBOUND"); row.setStatus(input.automatic()?"IGNORED":"UNMATCHED"); row.setMessageId(input.messageId());
        row.setFromAddress(input.fromAddress()); row.setRecipients(input.recipients()); row.setSubject(input.subject()); row.setBodyText(input.body());
        row.setAttachmentNames(input.attachmentNames()); row.setReferencesHeader(String.join(" ",input.references())); row.setOccurredAt(input.occurredAt()); row.setCreatedAt(now); row.setThreadId(row.getId());
        MailCustomerRow customer=null;
        if(!input.automatic()) {
            var parent=input.references().isEmpty()?null:mapper.parent(org,input.references(),input.fromAddress(),input.recipients());
            if(parent!=null) { customer=mapper.customer(org,parent.getCustomerId()); if(customer!=null) { row.setParentId(parent.getId()); row.setThreadId(parent.getThreadId()); } }
            if(customer==null) { var matches=mapper.customersByEmail(org,input.fromAddress()); if(matches.size()==1) customer=matches.get(0); }
        }
        if(customer!=null) { row.setCustomerId(customer.getId()); row.setOwnerMemberId(customer.getOwnerMemberId()); row.setStatus("RECEIVED"); }
        if(mapper.insertMessage(row)!=1) return -1;
        if(customer!=null) { persistIncoming(row); return 1; }
        return input.automatic()?1:0;
    }
    public void persistIncoming(MailMessageRow row) {
        row.setInteractionId(UUID.randomUUID()); mapper.insertInteraction(row); mapper.attachInteraction(row.getOrganizationId(),row.getId(),row.getInteractionId());
        mapper.touchCustomer(row); mapper.createTask(row,clock.instant());
    }
}
