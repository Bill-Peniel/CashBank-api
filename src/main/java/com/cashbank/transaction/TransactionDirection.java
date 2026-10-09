package com.cashbank.transaction;

import java.util.UUID;

public enum TransactionDirection {
    CREDIT,
    DEBIT;

    public static TransactionDirection of(Transaction transaction, UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return transaction.getSourceWallet() != null && walletId.equals(transaction.getSourceWallet().getId())
                ? DEBIT
                : CREDIT;
    }
}
