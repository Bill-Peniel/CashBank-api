package com.cashbank.limit;

import java.math.BigDecimal;

public record LimitsResponse(
        BigDecimal minAmount,
        BigDecimal maxPerTransaction,
        BigDecimal dailyOutgoingLimit,
        BigDecimal usedToday,
        BigDecimal remainingToday,
        String currency) {
}
