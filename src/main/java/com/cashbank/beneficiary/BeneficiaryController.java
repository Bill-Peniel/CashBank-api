package com.cashbank.beneficiary;

import java.util.UUID;

import com.cashbank.beneficiary.dto.BeneficiaryResponse;
import com.cashbank.beneficiary.dto.CreateBeneficiaryRequest;
import com.cashbank.common.web.PageResponse;
import com.cashbank.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@Tag(name = "Bénéficiaires")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer un bénéficiaire")
    public BeneficiaryResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBeneficiaryRequest request) {
        return beneficiaryService.add(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @Operation(summary = "Lister ses bénéficiaires")
    public PageResponse<BeneficiaryResponse> list(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "alias", direction = Sort.Direction.ASC) Pageable pageable) {
        return beneficiaryService.list(CurrentUser.id(jwt), pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un bénéficiaire")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        beneficiaryService.remove(CurrentUser.id(jwt), id);
    }
}
