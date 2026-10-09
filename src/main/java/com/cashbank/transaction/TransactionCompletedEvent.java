package com.cashbank.transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionCompletedEvent(
        String reference,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Party source,
        Party destination) {

    public record Party(UUID userId, String fullName, String walletNumber) {
    }
}
