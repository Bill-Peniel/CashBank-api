package com.cashbank.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Override
    @EntityGraph(attributePaths = {"sourceWallet", "destinationWallet"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"sourceWallet", "destinationWallet"})
    Optional<Transaction> findByReference(String reference);

    @EntityGraph(attributePaths = {"sourceWallet", "destinationWallet"})
    Optional<Transaction> findByInitiatedByAndIdempotencyKey(UUID initiatedBy, String idempotencyKey);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.sourceWallet.id = :walletId and t.createdAt >= :since
            """)
    BigDecimal sumOutgoingSince(UUID walletId, Instant since);
}
