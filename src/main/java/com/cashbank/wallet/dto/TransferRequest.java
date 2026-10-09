package com.cashbank.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TransferRequest(
        @Pattern(regexp = "^CB\\d{10}$", message = "Numéro de portefeuille invalide") String walletNumber,
        UUID beneficiaryId,
        @NotNull @DecimalMin(value = "0.01", message = "Le montant doit être positif")
        @Digits(integer = 17, fraction = 2, message = "Montant invalide (2 décimales maximum)")
        BigDecimal amount,
        @Size(max = 255) String description) {

    @JsonIgnore
    @AssertTrue(message = "Indiquez soit walletNumber, soit beneficiaryId (pas les deux)")
    public boolean isRecipientValid() {
        return (walletNumber == null) != (beneficiaryId == null);
    }
}
