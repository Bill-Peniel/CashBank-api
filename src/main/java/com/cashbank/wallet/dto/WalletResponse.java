package com.cashbank.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cashbank.wallet.Wallet;
import com.cashbank.wallet.WalletStatus;

public record WalletResponse(
        UUID id,
        String walletNumber,
        BigDecimal balance,
        String currency,
        WalletStatus status,
        Instant createdAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getWalletNumber(), wallet.getBalance(),
                wallet.getCurrency(), wallet.getStatus(), wallet.getCreatedAt());
    }
}
