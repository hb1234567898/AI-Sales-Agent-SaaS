package com.yourcompany.salesagent.commercial.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class CatalogRequests {
    private CatalogRequests() {}
    public record Product(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,40}") String sku,
        @NotBlank @Size(max=200) String name, @NotBlank @Size(max=30) String unit,
        @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer=9,fraction=2) BigDecimal listPrice,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        boolean active, @NotNull @Size(max=2000) String description, @Min(0) Long version) {}
    public record PriceEntry(@NotNull UUID productId,
        @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer=9,fraction=2) BigDecimal unitPrice) {}
    public record PriceBook(@NotBlank @Size(max=200) String name, @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil, boolean active,
        @NotNull @Size(min=1,max=1000) List<@NotNull @Valid PriceEntry> entries, @Min(0) Long version) {}
}
