package com.cashbank.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBeneficiaryRequest(
        @NotBlank @Pattern(regexp = "^CB\\d{10}$", message = "Numéro de portefeuille invalide") String walletNumber,
        @NotBlank @Size(max = 100) String alias) {
}
