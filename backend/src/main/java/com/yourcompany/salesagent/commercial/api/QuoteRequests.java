package com.yourcompany.salesagent.commercial.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class QuoteRequests {
    private QuoteRequests() {}
    public record Line(@NotNull UUID productId,
        @NotNull @DecimalMin("0.001") @DecimalMax("1000000") @Digits(integer=7,fraction=3) BigDecimal quantity,
        @NotNull @DecimalMin("0") @DecimalMax("99.99") @Digits(integer=2,fraction=2) BigDecimal discountPercent) {}
    public record Upsert(@NotNull UUID opportunityId, @NotBlank @Size(max=200) String title,
        UUID priceBookId, @NotNull LocalDate validUntil, @NotNull @Size(max=5000) String terms,
        @NotNull @Size(min=1,max=50) List<@NotNull @Valid Line> lines, @Min(0) Long version) {}
    public record Decision(@NotNull @Min(0) Long version, @NotBlank @Size(max=1000) String reason) {}
    public record Send(@NotNull @Min(0) Long version, @NotBlank @Email @Size(max=320) String to,
        @NotBlank @Size(max=500) String subject, @NotBlank @Size(max=20000) String body) {}
}
