package com.cashbank.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import com.cashbank.common.exception.ErrorCode;
import com.cashbank.transaction.TransactionRepository;

import org.junit.jupiter.api.Test;

class TransactionLimitServiceTest {

    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final ZoneId BENIN = ZoneId.of("Africa/Porto-Novo");

    private final TransactionRepository repository = mock(TransactionRepository.class);
    // 23:30 UTC is 00:30 the next day in Benin (UTC+1), so the day started at 23:00 UTC.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T23:30:00Z"), ZoneOffset.UTC);
    private final TransactionLimitService service = new TransactionLimitService(
            new LimitProperties(new BigDecimal("100"), new BigDecimal("500000"), new BigDecimal("1000000"), BENIN),
            repository, clock);

    @Test
    void amountBelowMinimumIsRejected() {
        assertThatThrownBy(() -> service.checkAmount(new BigDecimal("99.99")))
                .extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_TOO_LOW);
    }

    @Test
    void amountAbovePerTransactionLimitIsRejected() {
        assertThatThrownBy(() -> service.checkAmount(new BigDecimal("500000.01")))
                .extracting("errorCode").isEqualTo(ErrorCode.TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    void boundariesAreInclusive() {
        assertThatCode(() -> service.checkAmount(new BigDecimal("100"))).doesNotThrowAnyException();
        assertThatCode(() -> service.checkAmount(new BigDecimal("500000"))).doesNotThrowAnyException();
    }

    @Test
    void dailyLimitUsesStartOfDayInConfiguredZone() {
        Instant expectedStartOfDay = Instant.parse("2026-10-08T23:00:00Z");
        when(repository.sumOutgoingSince(WALLET_ID, expectedStartOfDay)).thenReturn(new BigDecimal("900000"));

        assertThatCode(() -> service.checkDailyOutgoing(WALLET_ID, new BigDecimal("100000")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.checkDailyOutgoing(WALLET_ID, new BigDecimal("100000.01")))
                .extracting("errorCode").isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void describeReportsRemainingAmount() {
        when(repository.sumOutgoingSince(eq(WALLET_ID), any())).thenReturn(new BigDecimal("250000"));

        LimitsResponse limits = service.describe(WALLET_ID, "XOF");

        assertThat(limits.usedToday()).isEqualByComparingTo("250000");
        assertThat(limits.remainingToday()).isEqualByComparingTo("750000");
    }
}
