package com.yourcompany.salesagent.commercial.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.CommercialMapper;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.tool.email.EmailConfigurationService;

@Service
public class QuoteEmailService {
    private final CommercialMapper mapper; private final CommercialAccess access; private final QuoteService quotes;
    private final QuoteDocuments documents; private final FileStorageService files; private final AgentWorkflowMapper agent;
    private final EmailConfigurationService email; private final Clock clock;
    public QuoteEmailService(CommercialMapper mapper,CommercialAccess access,QuoteService quotes,QuoteDocuments documents,FileStorageService files,AgentWorkflowMapper agent,EmailConfigurationService email,Clock clock) {
        this.mapper=mapper; this.access=access; this.quotes=quotes; this.documents=documents; this.files=files; this.agent=agent; this.email=email; this.clock=clock;
    }
    @Transactional
    public CommercialResponses.SendResult propose(AuthPrincipal actor,UUID id,QuoteRequests.Send input) {
        CommercialAccess.writer(actor); access.lockQuote(actor,id,input.version()); var quote=access.quote(actor,id);
        if(!"ISSUED".equals(quote.getStatus()) || quote.getValidUntil().isBefore(access.today())) throw new CommercialException("只能发送有效期内的已定稿报价");
        if(quote.getApprovalId()!=null) throw new CommercialException("此版本已有邮件审批记录，请先核对结果；需要调整时请创建新版本");
        if(input.subject().contains("\r") || input.subject().contains("\n") || input.to().contains("\r") || input.to().contains("\n")) throw new CommercialException("邮件地址和主题不能包含换行");
        if(!"OPEN".equals(access.opportunity(actor,quote.getOpportunityId()).getStatus())) throw new CommercialException("商机已经关闭，不能发送报价");
        var org=actor.organizationId(); var now=clock.instant(); email.requireRuntimeConfiguration(org);
        var detail=quotes.detail(actor,id,null);
        var file=files.uploadContent(actor,quote.getCustomerId(),"Quote-"+quote.getNumber()+"-V"+quote.getRevision()+".csv","text/csv",documents.csv(actor,detail));
        mapper.attachFile(org,detail.snapshot().getId(),file.id());
        var stored=files.requireActive(org,file.id());
        var payload=new LinkedHashMap<String,Object>(); payload.put("to",input.to().strip()); payload.put("subject",input.subject().strip()); payload.put("body",input.body().strip());
        payload.put("quoteId",id.toString()); payload.put("quoteRevision",quote.getRevision()); payload.put("attachmentRequired",true); payload.put("attachments",files.preview(List.of(stored)));
        payload.put("quoteNumber",quote.getNumber()); payload.put("quoteTotal",detail.snapshot().getTotal()); payload.put("currency","CNY");
        String hash; try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        agent.insertDefaultConfigIfAbsent(org,now); var run=UUID.randomUUID(); var action=UUID.randomUUID(); var approval=UUID.randomUUID();
        agent.insertRun(run,org,agent.selectDefaultConfigId(org),actor.memberId(),"MANUAL","RUNNING",access.today(),null,Map.of("source","QUOTE_EMAIL","quoteId",id.toString()),Map.of("quoteRevision",quote.getRevision()),now,now);
        agent.insertActionRequest(action,org,run,null,quote.getCustomerId(),actor.memberId(),"SEND_EMAIL","HIGH","AWAITING_APPROVAL","email.send","v1",true,"REQUIRE_APPROVAL","发送正式报价，需核对版本、金额、收件人和附件",payload,hash,payload,"quote-email:"+id+":"+quote.getRevision(),now.plus(Duration.ofDays(7)));
        agent.insertApproval(approval,org,action,actor.memberId(),"发送报价 "+quote.getNumber()+" V"+quote.getRevision(),hash,now,now.plus(Duration.ofDays(7)));
        agent.completeRun(run,org,"WAITING_APPROVAL",1,1,1,0,0,1,Map.of("message","报价发送等待审批"),null,null);
        CommercialAccess.changed(mapper.attachApproval(org,detail.snapshot().getId(),approval));
        CommercialAccess.changed(mapper.quoteStatus(org,id,"ISSUED",input.version(),now));
        mapper.event(org,"QUOTE",id,actor.memberId(),"提交 V"+quote.getRevision()+" 报价邮件审批："+approval,now);
        return new CommercialResponses.SendResult(approval,run);
    }
}
