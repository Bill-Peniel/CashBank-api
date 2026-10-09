package com.cashbank.transaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.JoinType;

import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.common.web.PageResponse;
import com.cashbank.wallet.WalletRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionService(TransactionRepository transactionRepository, WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    public PageResponse<TransactionResponse> history(UUID userId, TransactionType type, Instant from, Instant to,
            Pageable pageable) {
        UUID walletId = walletRepository.findIdByOwnerId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        List<Specification<Transaction>> filters = commonFilters(type, from, to);
        filters.add(involvesWallet(walletId));
        return PageResponse.of(transactionRepository.findAll(Specification.allOf(filters), pageable),
                tx -> TransactionResponse.from(tx, TransactionDirection.of(tx, walletId)));
    }

    public TransactionResponse getByReference(UUID userId, String reference) {
        UUID walletId = walletRepository.findIdByOwnerId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        Transaction tx = transactionRepository.findByReference(reference)
                .filter(t -> t.involves(walletId))
                // Same 404 as an unknown reference, so other users' transactions are not revealed.
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        return TransactionResponse.from(tx, TransactionDirection.of(tx, walletId));
    }

    public PageResponse<TransactionResponse> searchAll(TransactionType type, Instant from, Instant to,
            Pageable pageable) {
        return PageResponse.of(
                transactionRepository.findAll(Specification.allOf(commonFilters(type, from, to)), pageable),
                tx -> TransactionResponse.from(tx, null));
    }

    private static List<Specification<Transaction>> commonFilters(TransactionType type, Instant from, Instant to) {
        List<Specification<Transaction>> filters = new ArrayList<>();
        if (type != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (from != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            filters.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }
        return filters;
    }

    private static Specification<Transaction> involvesWallet(UUID walletId) {
        // Left joins: deposits have no source wallet, withdrawals no destination wallet.
        return (root, query, cb) -> cb.or(
                cb.equal(root.join("sourceWallet", JoinType.LEFT).get("id"), walletId),
                cb.equal(root.join("destinationWallet", JoinType.LEFT).get("id"), walletId));
    }
}
