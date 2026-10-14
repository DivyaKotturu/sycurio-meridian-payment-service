package com.example.payments.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreatePaymentRequest(@NotBlank String token,
                                   @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
                                   @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
