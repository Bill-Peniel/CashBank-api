package com.cashbank.transaction;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.cashbank.wallet.Wallet;

@Entity
@Table(name = "transactions")
public class Transaction {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false, length = 30)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id", updatable = false)
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_wallet_id", updatable = false)
    private Wallet destinationWallet;

    @Column(updatable = false)
    private String description;

    @Column(name = "initiated_by", nullable = false, updatable = false)
    private UUID initiatedBy;

    @Column(name = "idempotency_key", updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    protected Transaction() {
    }

    private Transaction(TransactionType type, BigDecimal amount, String currency, Wallet source, Wallet destination,
            String description, UUID initiatedBy, String idempotencyKey) {
        this.reference = generateReference();
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.sourceWallet = source;
        this.destinationWallet = destination;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey;
    }

    public static Transaction deposit(Wallet destination, BigDecimal amount, String description, UUID initiatedBy,
            String idempotencyKey) {
        return new Transaction(TransactionType.DEPOSIT, amount, destination.getCurrency(), null, destination,
                description, initiatedBy, idempotencyKey);
    }

    public static Transaction withdrawal(Wallet source, BigDecimal amount, String description, UUID initiatedBy,
            String idempotencyKey) {
        return new Transaction(TransactionType.WITHDRAWAL, amount, source.getCurrency(), source, null,
                description, initiatedBy, idempotencyKey);
    }

    public static Transaction transfer(Wallet source, Wallet destination, BigDecimal amount, String description,
            UUID initiatedBy, String idempotencyKey) {
        return new Transaction(TransactionType.TRANSFER, amount, source.getCurrency(), source, destination,
                description, initiatedBy, idempotencyKey);
    }

    private static String generateReference() {
        StringBuilder sb = new StringBuilder("TX").append(REF_DATE.format(Instant.now()));
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public boolean involves(UUID walletId) {
        return (sourceWallet != null && sourceWallet.getId().equals(walletId))
                || (destinationWallet != null && destinationWallet.getId().equals(walletId));
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Wallet getSourceWallet() {
        return sourceWallet;
    }

    public Wallet getDestinationWallet() {
        return destinationWallet;
    }

    public String getDescription() {
        return description;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
