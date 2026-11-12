package com.yourcompany.salesagent.commercial.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;

@Service
public class CatalogService {
    private final CommercialMapper mapper;
    private final Clock clock;
    public CatalogService(CommercialMapper mapper,Clock clock) { this.mapper=mapper; this.clock=clock; }
    public IPage<ProductRow> products(AuthPrincipal actor,String query,Boolean active,int page,int size) {
        return mapper.products(Page.of(page+1L,size),actor.organizationId(),query.strip(),active);
    }
    public ProductRow product(AuthPrincipal actor,UUID id) {
        var row=mapper.product(actor.organizationId(),id);
        if(row==null) throw CommercialAccess.missing("产品不存在");
        return row;
    }
    @Transactional
    public ProductRow saveProduct(AuthPrincipal actor,UUID id,CatalogRequests.Product input) {
        CommercialAccess.manager(actor);
        if(id!=null) { product(actor,id); if(input.version()==null) throw new CommercialException("请携带产品版本"); }
        var target=id==null?UUID.randomUUID():id;
        try {
            if(id==null) mapper.insertProduct(actor.organizationId(),target,input,clock.instant());
            else CommercialAccess.changed(mapper.updateProduct(actor.organizationId(),target,input,clock.instant()));
        } catch(DataIntegrityViolationException error) { throw new CommercialException("产品编码已存在或格式不正确"); }
        return product(actor,target);
    }
    public List<PriceBookRow> books(AuthPrincipal actor) { return mapper.books(actor.organizationId()); }
    public CommercialResponses.PriceBookDetail book(AuthPrincipal actor,UUID id) {
        var row=mapper.book(actor.organizationId(),id);
        if(row==null) throw CommercialAccess.missing("价格表不存在");
        return new CommercialResponses.PriceBookDetail(row,mapper.entries(actor.organizationId(),id));
    }
    @Transactional
    public CommercialResponses.PriceBookDetail saveBook(AuthPrincipal actor,UUID id,CatalogRequests.PriceBook input) {
        CommercialAccess.manager(actor);
        if(input.validUntil().isBefore(input.validFrom())) throw new CommercialException("价格表结束日期不能早于开始日期");
        var ids=new HashSet<UUID>();
        for(var entry:input.entries()) {
            if(!ids.add(entry.productId())) throw new CommercialException("价格表中不能重复添加同一产品");
            product(actor,entry.productId());
        }
        var target=id==null?UUID.randomUUID():id;
        if(id==null) mapper.insertBook(actor.organizationId(),target,input,clock.instant());
        else {
            var row=mapper.lockBook(actor.organizationId(),id);
            if(row==null) throw CommercialAccess.missing("价格表不存在");
            CommercialAccess.version(row.getVersion(),input.version());
            CommercialAccess.changed(mapper.updateBook(actor.organizationId(),id,input,clock.instant()));
            mapper.deleteEntries(actor.organizationId(),id);
        }
        for(var entry:input.entries()) mapper.insertEntry(actor.organizationId(),target,entry);
        return book(actor,target);
    }
}
