package com.cashbank.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditService;
import com.cashbank.beneficiary.BeneficiaryRepository;
import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.limit.TransactionLimitService;
import com.cashbank.transaction.Transaction;
import com.cashbank.transaction.TransactionCompletedEvent;
import com.cashbank.transaction.TransactionDirection;
import com.cashbank.transaction.TransactionRepository;
import com.cashbank.transaction.TransactionResponse;
import com.cashbank.transaction.TransactionType;
import com.cashbank.user.Role;
import com.cashbank.user.User;
import com.cashbank.wallet.dto.AmountRequest;
import com.cashbank.wallet.dto.TransferRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    // TARGET_ID < SOURCE_ID, so the target wallet must be locked first.
    private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000000");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TARGET_NUMBER = "CB0000000002";

    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock BeneficiaryRepository beneficiaryRepository;
    @Mock TransactionLimitService limitService;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher events;
    @Mock PlatformTransactionManager transactionManager;

    private WalletService service;
    private Wallet source;
    private Wallet target;

    @BeforeEach
    void setUp() {
        service = new WalletService(walletRepository, transactionRepository, beneficiaryRepository, limitService,
                auditService, events, new TransactionTemplate(transactionManager), "XOF");
        source = wallet(SOURCE_ID, "CB0000000001", USER_ID, "Koffi", new BigDecimal("5000"));
        target = wallet(TARGET_ID, TARGET_NUMBER, UUID.randomUUID(), "Awa", BigDecimal.ZERO);
    }

    @Test
    void transferMovesFundsRecordsTransactionAndPublishesEvent() {
        stubTransferLookups();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = service.transfer(USER_ID,
                new TransferRequest(TARGET_NUMBER, null, new BigDecimal("2000"), "Loyer"), null);

        assertThat(source.getBalance()).isEqualByComparingTo("3000");
        assertThat(target.getBalance()).isEqualByComparingTo("2000");
        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(response.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(response.destinationWalletNumber()).isEqualTo(TARGET_NUMBER);

        ArgumentCaptor<TransactionCompletedEvent> event = ArgumentCaptor.forClass(TransactionCompletedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().destination().fullName()).isEqualTo("Awa Test");
        verify(auditService).success(eq(USER_ID), eq(AuditAction.TRANSFER), eq("TRANSACTION"), anyString(), anyString());
        verify(transactionManager).commit(any());
    }

    @Test
    void transferLocksWalletsInIdOrderToAvoidDeadlocks() {
        stubTransferLookups();
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(USER_ID, new TransferRequest(TARGET_NUMBER, null, new BigDecimal("100"), null), null);

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByIdForUpdate(TARGET_ID);
        order.verify(walletRepository).findByIdForUpdate(SOURCE_ID);
    }

    @Test
    void transferWithInsufficientFundsIsRolledBackAndAudited() {
        stubTransferLookups();

        assertThatThrownBy(() -> service.transfer(USER_ID,
                new TransferRequest(TARGET_NUMBER, null, new BigDecimal("9000"), null), null))
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        verify(transactionRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
        verify(transactionManager).rollback(any());
        verify(auditService).failure(eq(USER_ID), isNull(), eq(AuditAction.TRANSFER), eq("WALLET"), isNull(),
                anyString());
    }

    @Test
    void transferToOwnWalletIsRejected() {
        when(walletRepository.findIdByOwnerId(USER_ID)).thenReturn(Optional.of(SOURCE_ID));
        when(walletRepository.findIdByWalletNumber("CB0000000001")).thenReturn(Optional.of(SOURCE_ID));

        assertThatThrownBy(() -> service.transfer(USER_ID,
                new TransferRequest("CB0000000001", null, new BigDecimal("100"), null), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SAME_WALLET_TRANSFER);
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void transferToUnknownBeneficiaryIsRejected() {
        UUID beneficiaryId = UUID.randomUUID();
        when(walletRepository.findIdByOwnerId(USER_ID)).thenReturn(Optional.of(SOURCE_ID));
        when(beneficiaryRepository.findWalletIdByIdAndOwnerId(beneficiaryId, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(USER_ID,
                new TransferRequest(null, beneficiaryId, new BigDecimal("100"), null), null))
                .extracting("errorCode").isEqualTo(ErrorCode.BENEFICIARY_NOT_FOUND);
    }

    @Test
    void idempotentRequestReturnsPreviousTransactionWithoutMovingFunds() {
        Transaction previous = Transaction.deposit(source, new BigDecimal("1000"), null, USER_ID, "key-1");
        when(transactionRepository.findByInitiatedByAndIdempotencyKey(USER_ID, "key-1"))
                .thenReturn(Optional.of(previous));

        TransactionResponse response = service.deposit(USER_ID, new AmountRequest(new BigDecimal("1000"), null), "key-1");

        assertThat(response.reference()).isEqualTo(previous.getReference());
        assertThat(source.getBalance()).isEqualByComparingTo("5000");
        verify(walletRepository, never()).findByOwnerIdForUpdate(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void withdrawalChecksDailyLimitAfterLockingWallet() {
        when(walletRepository.findByOwnerIdForUpdate(USER_ID)).thenReturn(Optional.of(source));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.withdraw(USER_ID, new AmountRequest(new BigDecimal("500"), null), null);

        InOrder order = inOrder(walletRepository, limitService);
        order.verify(walletRepository).findByOwnerIdForUpdate(USER_ID);
        order.verify(limitService).checkDailyOutgoing(SOURCE_ID, new BigDecimal("500"));
        assertThat(source.getBalance()).isEqualByComparingTo("4500");
    }

    private void stubTransferLookups() {
        when(walletRepository.findIdByOwnerId(USER_ID)).thenReturn(Optional.of(SOURCE_ID));
        when(walletRepository.findIdByWalletNumber(TARGET_NUMBER)).thenReturn(Optional.of(TARGET_ID));
        when(walletRepository.findByIdForUpdate(SOURCE_ID)).thenReturn(Optional.of(source));
        when(walletRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));
    }

    private static Wallet wallet(UUID id, String number, UUID ownerId, String firstName, BigDecimal balance) {
        User owner = new User(firstName.toLowerCase() + "@test.com", "hash", firstName, "Test", "+22990000000", Role.USER);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Wallet wallet = new Wallet(number, owner, "XOF");
        ReflectionTestUtils.setField(wallet, "id", id);
        if (balance.signum() > 0) {
            wallet.credit(balance);
        }
        return wallet;
    }
}
