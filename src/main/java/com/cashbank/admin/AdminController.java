package com.cashbank.admin;

import java.time.Instant;
import java.util.UUID;

import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditLogResponse;
import com.cashbank.audit.AuditOutcome;
import com.cashbank.audit.AuditService;
import com.cashbank.common.web.PageResponse;
import com.cashbank.security.CurrentUser;
import com.cashbank.transaction.TransactionResponse;
import com.cashbank.transaction.TransactionService;
import com.cashbank.transaction.TransactionType;
import com.cashbank.user.UserService;
import com.cashbank.user.dto.UserResponse;
import com.cashbank.wallet.WalletService;
import com.cashbank.wallet.dto.UpdateWalletStatusRequest;
import com.cashbank.wallet.dto.WalletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final UserService userService;
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final AuditService auditService;

    public AdminController(UserService userService, WalletService walletService,
            TransactionService transactionService, AuditService auditService) {
        this.userService = userService;
        this.walletService = walletService;
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    @Operation(summary = "Lister les utilisateurs")
    public PageResponse<UserResponse> users(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.listUsers(pageable);
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Activer / désactiver un utilisateur")
    public UserResponse setUserStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request) {
        return userService.setEnabled(CurrentUser.id(jwt), id, request.enabled());
    }

    @PatchMapping("/wallets/{walletNumber}/status")
    @Operation(summary = "Geler / dégeler un portefeuille")
    public WalletResponse setWalletStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String walletNumber,
            @Valid @RequestBody UpdateWalletStatusRequest request) {
        return walletService.changeStatus(CurrentUser.id(jwt), walletNumber, request.status());
    }

    @GetMapping("/transactions")
    @Operation(summary = "Toutes les transactions (filtrables)")
    public PageResponse<TransactionResponse> transactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.searchAll(type, from, to, pageable);
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Journal d'audit (filtrable par acteur, action, résultat)")
    public PageResponse<AuditLogResponse> auditLogs(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(actorId, action, outcome, pageable);
    }

    public record UserStatusRequest(@NotNull Boolean enabled) {
    }
}
