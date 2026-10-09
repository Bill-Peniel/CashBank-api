package com.cashbank.limit;

import java.math.BigDecimal;
import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cashbank.limits")
public record LimitProperties(
        @NotNull @Positive BigDecimal minAmount,
        @NotNull @Positive BigDecimal maxPerTransaction,
        @NotNull @Positive BigDecimal dailyOutgoing,
        @NotNull ZoneId zone) {
}
