package com.yourcompany.salesagent.commercial.application;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.CommercialResponses.QuoteDetail;

@Service
public class QuoteDocuments {
    public byte[] csv(AuthPrincipal actor,QuoteDetail detail) {
        var csv=new StringBuilder("\uFEFF"); var v=detail.snapshot();
        row(csv,"报价单",detail.quote().getNumber(),"版本","V"+v.getRevision());
        row(csv,"销售方",v.getSellerName(),"客户",v.getCustomerName());
        row(csv,"报价名称",v.getTitle(),"有效期",v.getValidUntil().toString());
        row(csv,"币种","CNY","价格表",v.getPriceBookName());
        row(csv,"编码","产品","单位","数量","未税单价","折扣(%)","税率(%)","未税金额","税额","含税金额");
        for(var line:detail.lines()) row(csv,line.getSku(),line.getProductName(),line.getUnit(),line.getQuantity().toPlainString(),line.getUnitPrice().toPlainString(),line.getDiscountPercent().toPlainString(),line.getTaxRate().toPlainString(),line.getSubtotal().toPlainString(),line.getTax().toPlainString(),line.getTotal().toPlainString());
        row(csv,"未税合计",v.getSubtotal().toPlainString(),"税额合计",v.getTax().toPlainString(),"含税总额",v.getTotal().toPlainString());
        row(csv,"报价条款",v.getTerms());
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void row(StringBuilder out,String... values) { out.append(String.join(",",Arrays.stream(values).map(QuoteDocuments::cell).toList())).append("\r\n"); }
    static String cell(String raw) {
        var value=Objects.toString(raw,"");
        // Spreadsheet formula prefixes must remain literal, including after leading whitespace.
        if(!value.stripLeading().isEmpty() && "=+-@".indexOf(value.stripLeading().charAt(0))>=0) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
