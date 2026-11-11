package com.yourcompany.salesagent.mail.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.mail.api.*;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;
import com.yourcompany.salesagent.interaction.api.ChatAnalysisResponse;
import com.yourcompany.salesagent.tool.email.EmailConfigurationService;

@Service
public class MailConversationService {
    private final MailMapper mapper; private final MailSyncService sync; private final AgentWorkflowMapper agent;
    private final FileStorageService files; private final EmailConfigurationService email; private final ChatAnalysisService analysis; private final Clock clock;
    public MailConversationService(MailMapper mapper,MailSyncService sync,AgentWorkflowMapper agent,FileStorageService files,EmailConfigurationService email,ChatAnalysisService analysis,Clock clock) {
        this.mapper=mapper; this.sync=sync; this.agent=agent; this.files=files; this.email=email; this.analysis=analysis; this.clock=clock;
    }
    public IPage<MailMessageRow> list(AuthPrincipal actor,String filter,int page,int size) {
        if(!Set.of("PENDING","UNMATCHED","CLOSED","SENT","ALL").contains(filter)) throw new MailWorkflowException("不支持的邮件筛选");
        return mapper.page(Page.of(page+1L,size),actor.organizationId(),MailAccess.owner(actor),filter);
    }
    public MailMessageRow get(AuthPrincipal actor,UUID id) {
        var row=mapper.message(actor.organizationId(),id);
        if(row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"邮件不存在");
        if(!MailAccess.manages(actor)) {
            var customer=row.getCustomerId()==null?null:mapper.customer(actor.organizationId(),row.getCustomerId());
            if(customer==null || (customer.getOwnerMemberId()!=null && !actor.memberId().equals(customer.getOwnerMemberId()))) throw new AccessDeniedException("无权查看该客户邮件");
        }
        return row;
    }
    public List<MailMessageRow> thread(AuthPrincipal actor,UUID id) { var row=get(actor,id); return mapper.thread(actor.organizationId(),row.getThreadId(),MailAccess.owner(actor)); }
    @Transactional
    public MailMessageRow link(AuthPrincipal actor,UUID id,MailLinkRequest input) {
        MailAccess.writer(actor); if(!MailAccess.manages(actor)) throw new AccessDeniedException("仅团队负责人可关联待匹配邮件");
        var row=get(actor,id); var customer=mapper.customer(actor.organizationId(),input.customerId());
        if(customer==null) throw new MailWorkflowException("客户不存在或已停用");
        if(mapper.link(actor.organizationId(),id,customer,null,input.version())!=1) conflict();
        row.setCustomerId(customer.getId()); row.setOwnerMemberId(customer.getOwnerMemberId()); sync.persistIncoming(row);
        return get(actor,id);
    }
    @Transactional
    public void handle(AuthPrincipal actor,UUID id,long version) {
        MailAccess.writer(actor); get(actor,id);
        if(mapper.handle(actor.organizationId(),id,version)!=1) conflict();
        mapper.completeTask(actor.organizationId(),id,clock.instant());
    }
    public ChatAnalysisResponse analyze(AuthPrincipal actor,UUID id) {
        MailAccess.writer(actor); var row=get(actor,id);
        if(row.getCustomerId()==null || row.getInteractionId()==null || !row.getDirection().equals("INBOUND")) throw new MailWorkflowException("请先关联客户再分析邮件");
        if(row.getBodyText().isBlank() || row.getBodyText().startsWith("[邮件超过")) throw new MailWorkflowException("该邮件没有可分析的文本正文，请到原邮箱核对内容");
        return analysis.analyze(actor.organizationId(),row.getCustomerId(),row.getInteractionId(),actor.memberId());
    }
    public record ReplyResult(UUID approvalId,UUID runId) {}
    @Transactional
    public ReplyResult reply(AuthPrincipal actor,UUID id,MailReplyRequest input) {
        MailAccess.writer(actor); var row=get(actor,id); var org=actor.organizationId(); var now=clock.instant();
        if(!"INBOUND".equals(row.getDirection()) || row.getCustomerId()==null || !"RECEIVED".equals(row.getStatus())) throw new MailWorkflowException("只能回复已关联且待处理的客户来信");
        if(input.subject().contains("\r") || input.subject().contains("\n")) throw new MailWorkflowException("主题不能包含换行");
        email.requireRuntimeConfiguration(org);
        var attachments=input.attachmentIds().stream().distinct().map(file -> files.requireActive(org,file)).toList();
        if(attachments.stream().anyMatch(file -> !row.getCustomerId().equals(file.getCustomerId()))) throw new MailWorkflowException("只能附加当前客户的文件");
        if(mapper.reserveReply(org,id,input.version())!=1) conflict();
        var payload=new LinkedHashMap<String,Object>(); payload.put("to",row.getFromAddress()); payload.put("subject",input.subject().strip()); payload.put("body",input.body().strip());
        payload.put("mailReplyId",id.toString()); payload.put("attachmentRequired",!attachments.isEmpty()); payload.put("attachments",files.preview(attachments));
        String hash; try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        agent.insertDefaultConfigIfAbsent(org,now); var config=agent.selectDefaultConfigId(org); var run=UUID.randomUUID(); var action=UUID.randomUUID(); var approval=UUID.randomUUID();
        agent.insertRun(run,org,config,actor.memberId(),"MANUAL","RUNNING",LocalDate.ofInstant(now,ZoneId.of("Asia/Shanghai")),null,
            Map.of("source","EMAIL_REPLY","mailMessageId",id.toString()),Map.of("requestedBy",actor.email()),now,now);
        agent.insertActionRequest(action,org,run,null,row.getCustomerId(),actor.memberId(),"SEND_EMAIL","HIGH","AWAITING_APPROVAL","email.send","v1",true,"REQUIRE_APPROVAL",
            "回复客户来信，需人工确认收件人、正文和附件",payload,hash,payload,"mail-reply:"+id+":"+input.version(),now.plus(Duration.ofDays(7)));
        agent.insertApproval(approval,org,action,actor.memberId(),"客户邮件回复",hash,now,now.plus(Duration.ofDays(7)));
        agent.completeRun(run,org,"WAITING_APPROVAL",1,1,1,0,0,1,Map.of("message","邮件回复等待审批"),null,null);
        mapper.attachApproval(org,id,approval); return new ReplyResult(approval,run);
    }
    private static void conflict() { throw new OptimisticLockingFailureException("邮件已变化或已有待执行回复，请刷新并核对审批状态"); }
}
