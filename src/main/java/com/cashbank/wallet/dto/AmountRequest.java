package com.cashbank.wallet.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AmountRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Le montant doit être positif")
        @Digits(integer = 17, fraction = 2, message = "Montant invalide (2 décimales maximum)")
        BigDecimal amount,
        @Size(max = 255) String description) {
}
