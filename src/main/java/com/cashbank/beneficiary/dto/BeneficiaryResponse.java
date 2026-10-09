package com.cashbank.beneficiary.dto;

import java.time.Instant;
import java.util.UUID;

import com.cashbank.beneficiary.Beneficiary;
import com.cashbank.user.User;

public record BeneficiaryResponse(UUID id, String alias, String walletNumber, String holderName, Instant createdAt) {

    public static BeneficiaryResponse from(Beneficiary beneficiary) {
        User holder = beneficiary.getWallet().getOwner();
        return new BeneficiaryResponse(beneficiary.getId(), beneficiary.getAlias(),
                beneficiary.getWallet().getWalletNumber(), maskedName(holder), beneficiary.getCreatedAt());
    }

    private static String maskedName(User user) {
        return user.getFirstName() + " " + user.getLastName().charAt(0) + ".";
    }
}
