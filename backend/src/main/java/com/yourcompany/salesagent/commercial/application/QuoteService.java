package com.yourcompany.salesagent.commercial.application;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;

@Service
public class QuoteService {
    private final CommercialMapper mapper; private final CommercialAccess access; private final Clock clock;
    public QuoteService(CommercialMapper mapper,CommercialAccess access,Clock clock) { this.mapper=mapper; this.access=access; this.clock=clock; }
    public IPage<QuoteRow> list(AuthPrincipal actor,String query,String status,int page,int size) {
        if(!Set.of("","DRAFT","ISSUED","ACCEPTED","REJECTED","CANCELLED").contains(status)) throw new CommercialException("报价状态不支持");
        return mapper.quotes(Page.of(page+1L,size),actor.organizationId(),CommercialAccess.owner(actor),query.strip(),status);
    }
    @Transactional(readOnly=true)
    public CommercialResponses.QuoteDetail detail(AuthPrincipal actor,UUID id,Integer revision) {
        var quote=access.quote(actor,id); var version=snapshot(actor,id,revision==null?quote.getRevision():revision);
        return new CommercialResponses.QuoteDetail(quote,mapper.versions(actor.organizationId(),id),version,mapper.lines(actor.organizationId(),version.getId()),mapper.events(actor.organizationId(),"QUOTE",id));
    }
    public QuoteVersionRow snapshot(AuthPrincipal actor,UUID id,int revision) {
        access.quote(actor,id); var row=mapper.snapshot(actor.organizationId(),id,revision);
        if(row==null) throw CommercialAccess.missing("报价版本不存在"); return row;
    }
    @Transactional(readOnly=true)
    public CommercialResponses.QuotePreview preview(AuthPrincipal actor,QuoteRequests.Upsert input) { return calculate(actor,input); }
    private CommercialResponses.QuotePreview calculate(AuthPrincipal actor,QuoteRequests.Upsert input) {
        var opportunity=access.opportunity(actor,input.opportunityId());
        if(!"OPEN".equals(opportunity.getStatus())) throw new CommercialException("只能为进行中的商机创建报价");
        if(input.validUntil().isBefore(access.today())) throw new CommercialException("报价有效期不能早于今天");
        if(input.priceBookId()!=null) {
            var book=mapper.book(actor.organizationId(),input.priceBookId());
            if(book==null || !book.isActive() || access.today().isBefore(book.getValidFrom()) || access.today().isAfter(book.getValidUntil())) throw new CommercialException("所选价格表尚未生效、已到期或已停用");
        }
        return QuoteCalculator.calculate(input.lines(),mapper.pricing(actor.organizationId(),input.priceBookId(),input.lines().stream().map(QuoteRequests.Line::productId).toList()));
    }
    @Transactional
    public CommercialResponses.QuoteDetail save(AuthPrincipal actor,UUID id,QuoteRequests.Upsert input) {
        CommercialAccess.writer(actor); var org=actor.organizationId(); var now=clock.instant();
        var opportunity=access.opportunity(actor,input.opportunityId());
        QuoteRow quote;
        if(id==null) {
            quote=new QuoteRow(); quote.setId(UUID.randomUUID()); quote.setNumber(number("QT",quote.getId())); quote.setOpportunityId(input.opportunityId()); quote.setCustomerId(opportunity.getCustomerId()); quote.setRevision(1);
        } else {
            access.lockQuote(actor,id,input.version()); quote=access.quote(actor,id);
            if(!quote.getOpportunityId().equals(input.opportunityId())) throw new CommercialException("报价不能更换关联商机");
            if(!Set.of("DRAFT","ISSUED","REJECTED").contains(quote.getStatus())) throw new CommercialException("已确认或已取消的报价不能修改");
            requireNoPendingDelivery(quote);
            CommercialAccess.changed(mapper.reviseQuote(org,id,input.version(),now)); quote.setRevision(quote.getRevision()+1);
        }
        // The price-book lock prevents an entry replacement between validation and snapshotting.
        if(input.priceBookId()!=null && mapper.lockBook(org,input.priceBookId())==null) throw new CommercialException("价格表不存在");
        var calculated=calculate(actor,input);
        if(id==null) mapper.insertQuote(org,quote,now);
        var version=new QuoteVersionRow(); version.setId(UUID.randomUUID()); version.setQuoteId(quote.getId()); version.setRevision(quote.getRevision()); version.setTitle(input.title().strip());
        version.setPriceBookId(input.priceBookId()); version.setPriceBookName(input.priceBookId()==null?"产品标准价":mapper.book(org,input.priceBookId()).getName());
        version.setCustomerName(opportunity.getCustomerName()); version.setSellerName(actor.organizationName()); version.setValidUntil(input.validUntil()); version.setTerms(input.terms().strip());
        version.setSubtotal(calculated.subtotal()); version.setTax(calculated.tax()); version.setTotal(calculated.total()); version.setCreatedAt(now);
        mapper.insertQuoteVersion(org,version,actor.memberId()); for(var line:calculated.lines()) mapper.insertLine(org,version.getId(),line);
        mapper.event(org,"QUOTE",quote.getId(),actor.memberId(),"保存报价 V"+version.getRevision()+"，含税金额 "+calculated.total(),now);
        return detail(actor,quote.getId(),null);
    }
    @Transactional
    public CommercialResponses.QuoteDetail decide(AuthPrincipal actor,UUID id,String action,QuoteRequests.Decision input) {
        CommercialAccess.manager(actor); access.lockQuote(actor,id,input.version()); var row=access.quote(actor,id);
        String status;
        switch(action) {
            case "issue" -> { if(!"DRAFT".equals(row.getStatus())) throw new CommercialException("只能定稿草稿报价"); status="ISSUED"; }
            case "accept" -> { if(!"ISSUED".equals(row.getStatus())) throw new CommercialException("只能确认已定稿的报价"); status="ACCEPTED"; }
            case "reject" -> { if(!"ISSUED".equals(row.getStatus())) throw new CommercialException("只能拒绝已定稿的报价"); status="REJECTED"; }
            case "cancel" -> { if(!Set.of("DRAFT","ISSUED","REJECTED").contains(row.getStatus())) throw new CommercialException("该报价不能取消"); status="CANCELLED"; }
            default -> throw new CommercialException("不支持的报价操作");
        }
        requireNoPendingDelivery(row);
        if(Set.of("ISSUED","ACCEPTED").contains(status) && row.getValidUntil().isBefore(access.today())) throw new CommercialException("报价已过期，请新建有效版本");
        if(Set.of("ISSUED","ACCEPTED").contains(status) && !"OPEN".equals(access.opportunity(actor,row.getOpportunityId()).getStatus())) throw new CommercialException("商机已关闭，不能继续定稿或确认报价");
        CommercialAccess.changed(mapper.quoteStatus(actor.organizationId(),id,status,input.version(),clock.instant()));
        mapper.event(actor.organizationId(),"QUOTE",id,actor.memberId(),switch(action) { case "issue" -> "报价定稿"; case "accept" -> "登记客户确认"; case "reject" -> "登记客户拒绝"; default -> "取消报价"; }+"："+input.reason().strip(),clock.instant());
        return detail(actor,id,null);
    }
    public static void requireNoPendingDelivery(QuoteRow row) {
        if("PENDING".equals(row.getApprovalStatus()) || Set.of("APPROVED","EXECUTING").contains(Objects.toString(row.getActionStatus(),"")) || Set.of("SENDING","UNKNOWN").contains(Objects.toString(row.getDeliveryStatus(),"")))
            throw new CommercialException("该报价还有待审批、执行中或结果不确定的邮件，请先核对处理结果");
    }
    public static String number(String prefix,UUID id) { return prefix+"-"+id.toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT); }
}
