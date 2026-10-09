package com.cashbank.beneficiary;

import java.util.UUID;

import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditService;
import com.cashbank.beneficiary.dto.BeneficiaryResponse;
import com.cashbank.beneficiary.dto.CreateBeneficiaryRequest;
import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.common.web.PageResponse;
import com.cashbank.user.UserRepository;
import com.cashbank.wallet.Wallet;
import com.cashbank.wallet.WalletRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository, WalletRepository walletRepository,
            UserRepository userRepository, AuditService auditService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BeneficiaryResponse add(UUID userId, CreateBeneficiaryRequest request) {
        Wallet wallet = walletRepository.findByWalletNumber(request.walletNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_BENEFICIARY);
        }
        if (beneficiaryRepository.existsByOwnerIdAndWalletId(userId, wallet.getId())) {
            throw new BusinessException(ErrorCode.BENEFICIARY_ALREADY_EXISTS);
        }
        Beneficiary saved = beneficiaryRepository.save(
                new Beneficiary(userRepository.getReferenceById(userId), wallet, request.alias().trim()));
        auditService.success(userId, AuditAction.BENEFICIARY_ADDED, "BENEFICIARY", saved.getId().toString(),
                request.walletNumber());
        return BeneficiaryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<BeneficiaryResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.of(beneficiaryRepository.findByOwnerId(userId, pageable), BeneficiaryResponse::from);
    }

    @Transactional
    public void remove(UUID userId, UUID beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndOwnerId(beneficiaryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BENEFICIARY_NOT_FOUND));
        beneficiaryRepository.delete(beneficiary);
        auditService.success(userId, AuditAction.BENEFICIARY_REMOVED, "BENEFICIARY", beneficiaryId.toString(), null);
    }
}
