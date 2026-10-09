package com.cashbank.limit;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.transaction.TransactionRepository;

import org.springframework.stereotype.Service;

@Service
public class TransactionLimitService {

    private final LimitProperties limits;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public TransactionLimitService(LimitProperties limits, TransactionRepository transactionRepository, Clock clock) {
        this.limits = limits;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public void checkAmount(BigDecimal amount) {
        if (amount.compareTo(limits.minAmount()) < 0) {
            throw new BusinessException(ErrorCode.AMOUNT_TOO_LOW,
                    "Le montant minimum est de " + limits.minAmount().toPlainString());
        }
        if (amount.compareTo(limits.maxPerTransaction()) > 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_LIMIT_EXCEEDED,
                    "Le montant maximum par transaction est de " + limits.maxPerTransaction().toPlainString());
        }
    }

    // Call only after locking the source wallet, otherwise concurrent withdrawals can exceed the limit.
    public void checkDailyOutgoing(UUID walletId, BigDecimal amount) {
        BigDecimal remaining = limits.dailyOutgoing().subtract(usedToday(walletId));
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "Plafond journalier atteint, montant encore disponible aujourd'hui : "
                            + remaining.max(BigDecimal.ZERO).toPlainString());
        }
    }

    public LimitsResponse describe(UUID walletId, String currency) {
        BigDecimal used = usedToday(walletId);
        return new LimitsResponse(limits.minAmount(), limits.maxPerTransaction(), limits.dailyOutgoing(), used,
                limits.dailyOutgoing().subtract(used).max(BigDecimal.ZERO), currency);
    }

    private BigDecimal usedToday(UUID walletId) {
        return transactionRepository.sumOutgoingSince(walletId, startOfToday());
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(limits.zone())).atStartOfDay(limits.zone()).toInstant();
    }
}
