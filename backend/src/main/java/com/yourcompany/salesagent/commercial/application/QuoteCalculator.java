package com.yourcompany.salesagent.commercial.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;

public final class QuoteCalculator {
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    public static final BigDecimal MAX_TOTAL=new BigDecimal("999999999999.99");
    private QuoteCalculator() {}
    public static CommercialResponses.QuotePreview calculate(List<QuoteRequests.Line> inputs,List<ProductRow> products) {
        if(inputs==null || inputs.isEmpty() || inputs.size()>50) throw new CommercialException("报价需包含 1 至 50 项产品");
        var byId=new HashMap<UUID,ProductRow>(); products.forEach(p -> byId.put(p.getId(),p));
        var seen=new HashSet<UUID>(); var lines=new ArrayList<QuoteLineRow>();
        var subtotal=new BigDecimal("0.00"); var tax=new BigDecimal("0.00");
        for(var input:inputs) {
            var product=byId.get(input.productId());
            if(product==null || !product.isActive()) throw new CommercialException("产品已停用、不属于当前团队，或未在所选价格表中定价");
            if(!seen.add(input.productId())) throw new CommercialException("同一产品不能重复添加，请调整数量");
            if(input.quantity()==null || input.quantity().signum()<=0 || input.quantity().compareTo(new BigDecimal("1000000"))>0 || input.quantity().scale()>3
                || input.discountPercent()==null || input.discountPercent().signum()<0 || input.discountPercent().compareTo(HUNDRED)>=0 || input.discountPercent().scale()>2)
                throw new CommercialException("数量或折扣不合法");
            var net=product.getListPrice().multiply(input.quantity()).multiply(HUNDRED.subtract(input.discountPercent())).divide(HUNDRED).setScale(2,RoundingMode.HALF_UP);
            var lineTax=net.multiply(product.getTaxRate()).divide(HUNDRED).setScale(2,RoundingMode.HALF_UP);
            var line=new QuoteLineRow(); line.setPosition(lines.size()+1); line.setProductId(product.getId()); line.setSku(product.getSku()); line.setProductName(product.getName()); line.setUnit(product.getUnit());
            line.setQuantity(input.quantity()); line.setUnitPrice(product.getListPrice()); line.setTaxRate(product.getTaxRate()); line.setDiscountPercent(input.discountPercent());
            line.setSubtotal(net); line.setTax(lineTax); line.setTotal(net.add(lineTax)); lines.add(line);
            subtotal=subtotal.add(net); tax=tax.add(lineTax);
        }
        var total=subtotal.add(tax);
        if(total.signum()<=0 || total.compareTo(MAX_TOTAL)>0) throw new CommercialException("报价总额需大于零，且不能超过 999999999999.99 元");
        return new CommercialResponses.QuotePreview(List.copyOf(lines),subtotal,tax,total);
    }
}
