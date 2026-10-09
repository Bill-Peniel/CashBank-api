package com.cashbank.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.user.Role;
import com.cashbank.user.User;

import org.junit.jupiter.api.Test;

class WalletTest {

    private final Wallet wallet = new Wallet("CB0000000001",
            new User("a@test.com", "hash", "Koffi", "Agbo", "+22990000001", Role.USER), "XOF");

    @Test
    void creditAndDebitUpdateBalance() {
        wallet.credit(new BigDecimal("5000"));
        wallet.debit(new BigDecimal("1500.50"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("3499.50");
    }

    @Test
    void debitMoreThanBalanceIsRejected() {
        wallet.credit(new BigDecimal("1000"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("1000.01")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(wallet.getBalance()).isEqualByComparingTo("1000");
    }

    @Test
    void frozenWalletCannotMoveFunds() {
        wallet.credit(new BigDecimal("1000"));
        wallet.changeStatus(WalletStatus.FROZEN);

        assertThatThrownBy(() -> wallet.debit(BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.WALLET_FROZEN);
        assertThatThrownBy(() -> wallet.credit(BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.WALLET_FROZEN);
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("-5"))).isInstanceOf(IllegalArgumentException.class);
    }
}
