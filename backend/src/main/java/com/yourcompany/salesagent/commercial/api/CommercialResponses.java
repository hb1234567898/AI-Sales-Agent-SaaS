package com.yourcompany.salesagent.commercial.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.yourcompany.salesagent.commercial.infrastructure.*;

public final class CommercialResponses {
    private CommercialResponses() {}
    public record PriceBookDetail(PriceBookRow book, List<PriceEntryRow> entries) {}
    public record QuotePreview(List<QuoteLineRow> lines, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}
    public record QuoteDetail(QuoteRow quote, List<QuoteVersionRow> versions, QuoteVersionRow snapshot,
        List<QuoteLineRow> lines, List<CommercialEventRow> history) {}
    public record ContractDetail(ContractRow contract, List<PaymentPlanRow> installments,
        List<ReceiptRow> receipts, List<CommercialEventRow> history) {}
    public record SendResult(UUID approvalId, UUID runId) {}
}
