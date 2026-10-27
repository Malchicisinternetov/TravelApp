package com.example.expensesservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull LocalDate spentOn,
        @NotNull @Positive Long paidBy,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 3) BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        List<@NotNull @Positive Long> participantIds,
        List<@NotNull @Valid Share> shares
) {
    public record Share(@NotNull @Positive Long userId,
                        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal amount) {}
}
