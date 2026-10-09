package com.cashbank.wallet;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerId(UUID ownerId);

    Optional<Wallet> findByWalletNumber(String walletNumber);

    boolean existsByWalletNumber(String walletNumber);

    // Id-only lookups: loading the entity before locking it would leave a stale balance in the persistence context.
    @Query("select w.id from Wallet w where w.owner.id = :ownerId")
    Optional<UUID> findIdByOwnerId(UUID ownerId);

    @Query("select w.id from Wallet w where w.walletNumber = :walletNumber")
    Optional<UUID> findIdByWalletNumber(String walletNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.owner.id = :ownerId")
    Optional<Wallet> findByOwnerIdForUpdate(UUID ownerId);
}
