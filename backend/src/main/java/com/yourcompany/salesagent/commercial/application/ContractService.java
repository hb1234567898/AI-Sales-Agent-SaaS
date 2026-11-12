package com.yourcompany.salesagent.commercial.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.opportunity.application.OpportunityService;
import com.yourcompany.salesagent.opportunity.api.OpportunityStageRequest;
import com.yourcompany.salesagent.opportunity.domain.OpportunityStage;

@Service
public class ContractService {
    private final CommercialMapper mapper; private final CommercialAccess access; private final Clock clock;
    private final FileStorageService files; private final OpportunityService opportunities;
    public ContractService(CommercialMapper mapper,CommercialAccess access,Clock clock,FileStorageService files,OpportunityService opportunities) {
        this.mapper=mapper; this.access=access; this.clock=clock; this.files=files; this.opportunities=opportunities;
    }
    public IPage<ContractRow> list(AuthPrincipal actor,String query,String status,int page,int size) {
        if(!Set.of("","DRAFT","SIGNED","COMPLETED","CANCELLED").contains(status)) throw new CommercialException("合同状态不支持");
        return mapper.contracts(Page.of(page+1L,size),actor.organizationId(),CommercialAccess.owner(actor),query.strip(),status);
    }
    @Transactional(readOnly=true)
    public CommercialResponses.ContractDetail detail(AuthPrincipal actor,UUID id) {
        return new CommercialResponses.ContractDetail(access.contract(actor,id),mapper.plans(actor.organizationId(),id,CommercialAccess.owner(actor),access.today()),mapper.receipts(actor.organizationId(),id),mapper.events(actor.organizationId(),"CONTRACT",id));
    }
    public CommercialMetricsRow metrics(AuthPrincipal actor) { return mapper.metrics(actor.organizationId(),CommercialAccess.owner(actor),access.today()); }
    public IPage<PaymentPlanRow> receivables(AuthPrincipal actor,boolean overdue,int page,int size) {
        return mapper.receivables(Page.of(page+1L,size),actor.organizationId(),CommercialAccess.owner(actor),overdue,access.today());
    }
    public static void validatePlans(List<ContractRequests.Installment> plans,BigDecimal total) {
        if(plans==null || plans.isEmpty() || plans.size()>24) throw new CommercialException("回款计划需包含 1 至 24 期");
        var sum=BigDecimal.ZERO;
        for(var plan:plans) {
            if(plan.amount()==null || plan.amount().signum()<=0 || plan.amount().scale()>2 || plan.dueOn()==null || plan.name()==null || plan.name().isBlank()) throw new CommercialException("回款计划的名称、金额或到期日不合法");
            sum=sum.add(plan.amount());
        }
        if(sum.compareTo(total)!=0) throw new CommercialException("各期回款金额合计必须等于合同含税总额");
    }
    @Transactional
    public CommercialResponses.ContractDetail save(AuthPrincipal actor,UUID id,ContractRequests.Upsert input) {
        CommercialAccess.writer(actor);
        if(input.endsOn().isBefore(input.startsOn())) throw new CommercialException("合同结束日期不能早于开始日期");
        var org=actor.organizationId(); var now=clock.instant(); ContractRow contract;
        if(id==null) {
            access.quote(actor,input.quoteId()); mapper.lockQuote(org,input.quoteId()); var quote=access.quote(actor,input.quoteId());
            if(!"ACCEPTED".equals(quote.getStatus())) throw new CommercialException("只能从客户已确认的报价生成合同");
            if(mapper.contractForQuote(org,quote.getId())!=null) throw new CommercialException("该报价已经生成合同，请查看现有合同");
            var opportunity=access.opportunity(actor,quote.getOpportunityId());
            if(!Set.of("OPEN","WON").contains(opportunity.getStatus())) throw new CommercialException("输单或归档的商机不能生成合同");
            var version=mapper.snapshot(org,quote.getId(),quote.getRevision());
            contract=new ContractRow(); contract.setId(UUID.randomUUID()); contract.setNumber(QuoteService.number("CT",contract.getId()));
            contract.setQuoteId(quote.getId()); contract.setQuoteVersionId(version.getId()); contract.setCustomerId(quote.getCustomerId()); contract.setOpportunityId(quote.getOpportunityId()); contract.setTotal(version.getTotal());
        } else {
            access.lockContract(actor,id,input.version()); contract=access.contract(actor,id);
            if(!"DRAFT".equals(contract.getStatus())) throw new CommercialException("已签约或已关闭的合同不能修改金额、条款和计划");
            if(!contract.getQuoteId().equals(input.quoteId())) throw new CommercialException("合同不能更换报价来源");
        }
        validatePlans(input.installments(),contract.getTotal());
        if(id==null) {
            contract.setName(input.name().strip()); contract.setStartsOn(input.startsOn()); contract.setEndsOn(input.endsOn()); contract.setTerms(input.terms().strip()); mapper.insertContract(org,contract,now);
        } else { CommercialAccess.changed(mapper.updateContract(org,id,input,now)); mapper.deletePlans(org,id); }
        int position=0;
        for(var plan:input.installments()) {
            var row=new PaymentPlanRow(); row.setId(UUID.randomUUID()); row.setPosition(++position); row.setName(plan.name().strip()); row.setAmount(plan.amount()); row.setDueOn(plan.dueOn()); mapper.insertPlan(org,contract.getId(),row);
        }
        mapper.event(org,"CONTRACT",contract.getId(),actor.memberId(),id==null?"从确认报价生成合同草稿":"更新合同草稿与回款计划",now);
        return detail(actor,contract.getId());
    }
    @Transactional
    public CommercialResponses.ContractDetail sign(AuthPrincipal actor,UUID id,ContractRequests.Sign input) {
        CommercialAccess.manager(actor); access.lockContract(actor,id,input.version()); var row=access.contract(actor,id);
        if(!"DRAFT".equals(row.getStatus())) throw new CommercialException("只能登记草稿合同的签约");
        if(input.signedOn().isAfter(access.today()) || input.signedOn().isAfter(row.getEndsOn())) throw new CommercialException("签约日期不能晚于今天或合同结束日期");
        var file=files.requireActive(actor.organizationId(),input.signedFileId());
        if(!row.getCustomerId().equals(file.getCustomerId())) throw new CommercialException("签署文件必须是当前客户的有效附件");
        if(!file.getOriginalFilename().toLowerCase(Locale.ROOT).matches(".+\\.(pdf|docx?|png|jpe?g)$")) throw new CommercialException("签署文件需为 PDF、Word 或图片");
        var opportunity=access.opportunity(actor,row.getOpportunityId());
        if(!Set.of("OPEN","WON").contains(opportunity.getStatus())) throw new CommercialException("商机已经输单或归档，不能登记签约");
        var plans=mapper.plans(actor.organizationId(),id,CommercialAccess.owner(actor),access.today());
        validatePlans(plans.stream().map(p -> new ContractRequests.Installment(p.getName(),p.getAmount(),p.getDueOn())).toList(),row.getTotal());
        CommercialAccess.changed(mapper.signContract(actor.organizationId(),id,input,clock.instant()));
        if("OPEN".equals(opportunity.getStatus())) opportunities.changeStage(actor,row.getOpportunityId(),new OpportunityStageRequest(OpportunityStage.WON,opportunity.getVersion(),"合同 "+row.getNumber()+" 已登记签约"));
        mapper.event(actor.organizationId(),"CONTRACT",id,actor.memberId(),"登记签约，签约日期 "+input.signedOn()+"，签署附件 "+file.getOriginalFilename(),clock.instant());
        return detail(actor,id);
    }
    @Transactional
    public CommercialResponses.ContractDetail cancel(AuthPrincipal actor,UUID id,QuoteRequests.Decision input) {
        CommercialAccess.manager(actor); access.lockContract(actor,id,input.version()); var row=access.contract(actor,id);
        if(!"DRAFT".equals(row.getStatus())) throw new CommercialException("只能取消尚未签约的合同草稿");
        CommercialAccess.changed(mapper.contractStatus(actor.organizationId(),id,"CANCELLED",input.version(),clock.instant()));
        mapper.event(actor.organizationId(),"CONTRACT",id,actor.memberId(),"取消合同草稿："+input.reason().strip(),clock.instant()); return detail(actor,id);
    }
    @Transactional
    public CommercialResponses.ContractDetail receive(AuthPrincipal actor,UUID id,UUID planId,ContractRequests.Receipt raw) {
        CommercialAccess.manager(actor); access.contract(actor,id); var version=mapper.lockContract(actor.organizationId(),id);
        var input=new ContractRequests.Receipt(raw.version(),raw.amount(),raw.receivedOn(),raw.reference().strip(),raw.note().strip(),raw.requestKey());
        var previous=mapper.receiptByKey(actor.organizationId(),input.requestKey());
        if(previous!=null) {
            if(!id.equals(previous.getContractId()) || !planId.equals(previous.getPlanId()) || !actor.memberId().equals(previous.getCreatedBy()) || previous.getAmount().compareTo(input.amount())!=0 || !previous.getReceivedOn().equals(input.receivedOn()) || !previous.getReference().equals(input.reference()) || !previous.getNote().equals(input.note())) throw new CommercialException("该回款请求号已用于不同记录");
            return detail(actor,id);
        }
        CommercialAccess.version(version,input.version()); var contract=access.contract(actor,id);
        if(!"SIGNED".equals(contract.getStatus())) throw new CommercialException("只能登记已签约、尚未结清合同的回款");
        if(input.amount().signum()<=0 || input.amount().scale()>2 || input.receivedOn().isAfter(access.today())) throw new CommercialException("回款金额须为正数且精确到分，日期不能晚于今天");
        var plan=mapper.plans(actor.organizationId(),id,CommercialAccess.owner(actor),access.today()).stream().filter(p -> p.getId().equals(planId)).findFirst().orElseThrow(() -> CommercialAccess.missing("该期回款计划不存在"));
        if(input.amount().compareTo(plan.getAmount().subtract(plan.getPaid()))>0) throw new CommercialException("回款金额不能超过该期剩余应收金额");
        if(mapper.receiptReferenceExists(actor.organizationId(),id,input.reference())>0) throw new CommercialException("该合同已登记此流水号，请勿重复登记");
        try { mapper.insertReceipt(actor.organizationId(),id,planId,actor.memberId(),input,clock.instant()); }
        catch(DataIntegrityViolationException error) { throw new CommercialException("回款流水或请求号已存在，请刷新核对"); }
        var status=contract.getPaid().add(input.amount()).compareTo(contract.getTotal())==0?"COMPLETED":"SIGNED";
        CommercialAccess.changed(mapper.contractStatus(actor.organizationId(),id,status,version,clock.instant()));
        mapper.event(actor.organizationId(),"CONTRACT",id,actor.memberId(),"登记回款 "+input.amount()+"，流水号 "+input.reference(),clock.instant());
        return detail(actor,id);
    }
    public UploadedFile signedFile(AuthPrincipal actor,UUID id) {
        var row=access.contract(actor,id); if(row.getSignedFileId()==null) throw CommercialAccess.missing("合同尚未登记签署文件");
        return files.requireActive(actor.organizationId(),row.getSignedFileId());
    }
}
