package com.yourcompany.salesagent.mail.application;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;

@Service
public class MailDeliveryService {
    private final MailMapper mapper; private final Clock clock;
    public MailDeliveryService(MailMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }
    @Transactional
    public MailMessageRow prepare(ToolExecutionContext context,String from,List<String> to,String subject,String body,List<String> attachments,Map<String,Object> payload) {
        var customer=mapper.customer(context.organizationId(),context.customerId());
        if(customer==null) throw new MailWorkflowException("邮件关联客户不存在或已停用");
        var row=new MailMessageRow(); row.setId(UUID.randomUUID()); row.setOrganizationId(context.organizationId()); row.setCustomerId(customer.getId()); row.setOwnerMemberId(customer.getOwnerMemberId());
        row.setActionRequestId(context.actionRequestId()); row.setDirection("OUTBOUND"); row.setStatus("SENDING"); row.setFromAddress(from.toLowerCase(Locale.ROOT));
        row.setRecipients(to); row.setSubject(subject); row.setBodyText(body); row.setAttachmentNames(attachments); row.setCreatedAt(clock.instant()); row.setOccurredAt(clock.instant());
        row.setThreadId(row.getId()); row.setReferencesHeader("");
        row.setMessageId("<"+context.actionRequestId()+"@"+from.substring(from.lastIndexOf('@')+1)+">");
        if(payload.get("mailReplyId") instanceof String value) {
            var parent=mapper.message(context.organizationId(),UUID.fromString(value));
            if(parent==null || !customer.getId().equals(parent.getCustomerId()) || !"INBOUND".equals(parent.getDirection()) || !to.equals(List.of(parent.getFromAddress())))
                throw new MailWorkflowException("回复邮件与审批收件人不一致");
            row.setParentId(parent.getId()); row.setThreadId(parent.getThreadId()); row.setReferencesHeader(parent.getMessageId());
        }
        if(mapper.insertMessage(row)!=1) throw new MailWorkflowException("该邮件动作已有发送记录，请核对邮箱，不能重复发送");
        return row;
    }
    @Transactional
    public void sent(MailMessageRow row) {
        if(mapper.deliveryStatus(row.getOrganizationId(),row.getId(),"SENT")!=1) throw new MailWorkflowException("邮件发送记录状态已变化，请人工核对邮箱");
        row.setInteractionId(UUID.randomUUID()); mapper.insertInteraction(row); mapper.attachInteraction(row.getOrganizationId(),row.getId(),row.getInteractionId()); mapper.touchCustomer(row);
        if(row.getParentId()!=null) { mapper.replied(row.getOrganizationId(),row.getParentId()); mapper.completeTask(row.getOrganizationId(),row.getParentId(),clock.instant()); }
    }
    public void unknown(MailMessageRow row) { mapper.deliveryStatus(row.getOrganizationId(),row.getId(),"UNKNOWN"); }
}
