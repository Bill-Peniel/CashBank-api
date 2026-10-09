package com.cashbank.beneficiary;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    @EntityGraph(attributePaths = {"wallet", "wallet.owner"})
    Page<Beneficiary> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Beneficiary> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndWalletId(UUID ownerId, UUID walletId);

    @Query("select b.wallet.id from Beneficiary b where b.id = :id and b.owner.id = :ownerId")
    Optional<UUID> findWalletIdByIdAndOwnerId(UUID id, UUID ownerId);
}
