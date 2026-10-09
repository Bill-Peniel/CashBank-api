package com.cashbank.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.cashbank.wallet.Wallet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        UUID id,
        String reference,
        TransactionType type,
        TransactionDirection direction,
        BigDecimal amount,
        String currency,
        String sourceWalletNumber,
        String destinationWalletNumber,
        String description,
        Instant createdAt) {

    public static TransactionResponse from(Transaction tx, TransactionDirection direction) {
        return new TransactionResponse(tx.getId(), tx.getReference(), tx.getType(), direction, tx.getAmount(),
                tx.getCurrency(), number(tx.getSourceWallet()), number(tx.getDestinationWallet()),
                tx.getDescription(), tx.getCreatedAt());
    }

    private static String number(Wallet wallet) {
        return wallet != null ? wallet.getWalletNumber() : null;
    }
}
