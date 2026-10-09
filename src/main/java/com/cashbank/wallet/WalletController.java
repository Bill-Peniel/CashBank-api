package com.cashbank.wallet;

import java.time.Instant;

import com.cashbank.common.web.PageResponse;
import com.cashbank.limit.LimitsResponse;
import com.cashbank.security.CurrentUser;
import com.cashbank.transaction.TransactionResponse;
import com.cashbank.transaction.TransactionService;
import com.cashbank.transaction.TransactionType;
import com.cashbank.wallet.dto.AmountRequest;
import com.cashbank.wallet.dto.TransferRequest;
import com.cashbank.wallet.dto.WalletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Portefeuille")
public class WalletController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_DOC =
            "Clé unique côté client : une requête rejouée avec la même clé renvoie la transaction initiale";

    private final WalletService walletService;
    private final TransactionService transactionService;

    public WalletController(WalletService walletService, TransactionService transactionService) {
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Consulter son portefeuille et son solde")
    public WalletResponse myWallet(@AuthenticationPrincipal Jwt jwt) {
        return walletService.getMyWallet(CurrentUser.id(jwt));
    }

    @GetMapping("/limits")
    @Operation(summary = "Plafonds applicables et consommation du jour")
    public LimitsResponse limits(@AuthenticationPrincipal Jwt jwt) {
        return walletService.getMyLimits(CurrentUser.id(jwt));
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dépôt (simulation d'un approvisionnement externe : carte, mobile money...)")
    public TransactionResponse deposit(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = IDEMPOTENCY_DOC) @RequestHeader(name = IDEMPOTENCY_HEADER, required = false)
            @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return walletService.deposit(CurrentUser.id(jwt), request, idempotencyKey);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Retrait")
    public TransactionResponse withdraw(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = IDEMPOTENCY_DOC) @RequestHeader(name = IDEMPOTENCY_HEADER, required = false)
            @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return walletService.withdraw(CurrentUser.id(jwt), request, idempotencyKey);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfert vers un autre portefeuille (par numéro ou bénéficiaire enregistré)")
    public TransactionResponse transfer(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = IDEMPOTENCY_DOC) @RequestHeader(name = IDEMPOTENCY_HEADER, required = false)
            @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return walletService.transfer(CurrentUser.id(jwt), request, idempotencyKey);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Historique paginé, filtrable par type et période")
    public PageResponse<TransactionResponse> history(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.history(CurrentUser.id(jwt), type, from, to, pageable);
    }

    @GetMapping("/transactions/{reference}")
    @Operation(summary = "Détail d'une transaction")
    public TransactionResponse transaction(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return transactionService.getByReference(CurrentUser.id(jwt), reference);
    }
}
