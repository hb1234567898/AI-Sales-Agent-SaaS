package com.yourcompany.salesagent.commercial.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class ContractRequests {
    private ContractRequests() {}
    public record Installment(@NotBlank @Size(max=120) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount, @NotNull LocalDate dueOn) {}
    public record Upsert(@NotNull UUID quoteId, @NotBlank @Size(max=200) String name,
        @NotNull LocalDate startsOn, @NotNull LocalDate endsOn, @NotNull @Size(max=5000) String terms,
        @NotNull @Size(min=1,max=24) List<@NotNull @Valid Installment> installments, @Min(0) Long version) {}
    public record Sign(@NotNull @Min(0) Long version, @NotNull UUID signedFileId, @NotNull LocalDate signedOn) {}
    public record Receipt(@NotNull @Min(0) Long version,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotNull LocalDate receivedOn, @NotBlank @Size(max=200) String reference,
        @NotNull @Size(max=1000) String note, @NotNull UUID requestKey) {}
}
