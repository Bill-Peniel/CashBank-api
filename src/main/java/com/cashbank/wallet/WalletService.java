package com.cashbank.wallet;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Supplier;

import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditService;
import com.cashbank.beneficiary.BeneficiaryRepository;
import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.limit.LimitsResponse;
import com.cashbank.limit.TransactionLimitService;
import com.cashbank.transaction.Transaction;
import com.cashbank.transaction.TransactionCompletedEvent;
import com.cashbank.transaction.TransactionDirection;
import com.cashbank.transaction.TransactionRepository;
import com.cashbank.transaction.TransactionResponse;
import com.cashbank.transaction.TransactionType;
import com.cashbank.user.User;
import com.cashbank.wallet.dto.AmountRequest;
import com.cashbank.wallet.dto.TransferRequest;
import com.cashbank.wallet.dto.WalletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TransactionLimitService limitService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final String currency;

    public WalletService(WalletRepository walletRepository, TransactionRepository transactionRepository,
            BeneficiaryRepository beneficiaryRepository, TransactionLimitService limitService,
            AuditService auditService, ApplicationEventPublisher events, TransactionTemplate transactionTemplate,
            @Value("${cashbank.currency}") String currency) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.limitService = limitService;
        this.auditService = auditService;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.currency = currency;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet createWallet(User owner) {
        String number;
        do {
            number = "CB" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
        } while (walletRepository.existsByWalletNumber(number));
        return walletRepository.save(new Wallet(number, owner, currency));
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(UUID userId) {
        return WalletResponse.from(findByOwner(userId));
    }

    @Transactional(readOnly = true)
    public LimitsResponse getMyLimits(UUID userId) {
        Wallet wallet = findByOwner(userId);
        return limitService.describe(wallet.getId(), wallet.getCurrency());
    }

    public TransactionResponse deposit(UUID userId, AmountRequest request, String idempotencyKey) {
        return execute(userId, AuditAction.DEPOSIT, idempotencyKey, () -> {
            limitService.checkAmount(request.amount());
            Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
            wallet.credit(request.amount());
            return transactionRepository.save(Transaction.deposit(wallet, request.amount(),
                    request.description(), userId, idempotencyKey));
        });
    }

    public TransactionResponse withdraw(UUID userId, AmountRequest request, String idempotencyKey) {
        return execute(userId, AuditAction.WITHDRAWAL, idempotencyKey, () -> {
            limitService.checkAmount(request.amount());
            Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
            limitService.checkDailyOutgoing(wallet.getId(), request.amount());
            wallet.debit(request.amount());
            return transactionRepository.save(Transaction.withdrawal(wallet, request.amount(),
                    request.description(), userId, idempotencyKey));
        });
    }

    public TransactionResponse transfer(UUID userId, TransferRequest request, String idempotencyKey) {
        return execute(userId, AuditAction.TRANSFER, idempotencyKey, () -> {
            limitService.checkAmount(request.amount());
            UUID sourceId = walletRepository.findIdByOwnerId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
            UUID targetId = resolveTargetWalletId(userId, request);
            if (sourceId.equals(targetId)) {
                throw new BusinessException(ErrorCode.SAME_WALLET_TRANSFER);
            }

            // Lock in id order to prevent deadlocks between opposite concurrent transfers.
            boolean sourceFirst = sourceId.compareTo(targetId) < 0;
            Wallet first = lock(sourceFirst ? sourceId : targetId);
            Wallet second = lock(sourceFirst ? targetId : sourceId);
            Wallet source = sourceFirst ? first : second;
            Wallet target = sourceFirst ? second : first;

            limitService.checkDailyOutgoing(source.getId(), request.amount());
            source.debit(request.amount());
            target.credit(request.amount());
            return transactionRepository.save(Transaction.transfer(source, target, request.amount(),
                    request.description(), userId, idempotencyKey));
        });
    }

    @Transactional
    public WalletResponse changeStatus(UUID adminId, String walletNumber, WalletStatus status) {
        Wallet wallet = walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        wallet.changeStatus(status);
        auditService.success(adminId, AuditAction.WALLET_STATUS_CHANGED, "WALLET", walletNumber,
                "status=" + status);
        log.info("Portefeuille {} passé au statut {}", walletNumber, status);
        return WalletResponse.from(wallet);
    }

    private TransactionResponse execute(UUID userId, AuditAction action, String idempotencyKey,
            Supplier<Transaction> operation) {
        try {
            return transactionTemplate.execute(status -> {
                if (idempotencyKey != null) {
                    var previous = transactionRepository.findByInitiatedByAndIdempotencyKey(userId, idempotencyKey);
                    if (previous.isPresent()) {
                        log.info("Requête idempotente rejouée (clé {}), transaction {}", idempotencyKey,
                                previous.get().getReference());
                        return toInitiatorResponse(previous.get());
                    }
                }
                Transaction tx = operation.get();
                auditService.success(userId, action, "TRANSACTION", tx.getReference(),
                        tx.getAmount().toPlainString() + " " + tx.getCurrency());
                events.publishEvent(toEvent(tx));
                log.info("{} {} {} {} effectué", tx.getType(), tx.getReference(), tx.getAmount(), tx.getCurrency());
                return toInitiatorResponse(tx);
            });
        } catch (BusinessException e) {
            // Audited after rollback, once locks and the connection are released.
            auditService.failure(userId, null, action, "WALLET", null, e.getErrorCode().name() + " : " + e.getMessage());
            log.info("{} refusé pour l'utilisateur {} : {}", action, userId, e.getErrorCode());
            throw e;
        }
    }

    private UUID resolveTargetWalletId(UUID userId, TransferRequest request) {
        if (request.beneficiaryId() != null) {
            return beneficiaryRepository.findWalletIdByIdAndOwnerId(request.beneficiaryId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BENEFICIARY_NOT_FOUND));
        }
        return walletRepository.findIdByWalletNumber(request.walletNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND,
                        "Aucun portefeuille ne correspond au numéro " + request.walletNumber()));
    }

    private Wallet lock(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
    }

    private Wallet findByOwner(UUID userId) {
        return walletRepository.findByOwnerId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
    }

    private static TransactionResponse toInitiatorResponse(Transaction tx) {
        TransactionDirection direction = tx.getType() == TransactionType.DEPOSIT
                ? TransactionDirection.CREDIT
                : TransactionDirection.DEBIT;
        return TransactionResponse.from(tx, direction);
    }

    private static TransactionCompletedEvent toEvent(Transaction tx) {
        return new TransactionCompletedEvent(tx.getReference(), tx.getType(), tx.getAmount(), tx.getCurrency(),
                party(tx.getSourceWallet()), party(tx.getDestinationWallet()));
    }

    private static TransactionCompletedEvent.Party party(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        User owner = wallet.getOwner();
        return new TransactionCompletedEvent.Party(owner.getId(), owner.getFullName(), wallet.getWalletNumber());
    }
}
