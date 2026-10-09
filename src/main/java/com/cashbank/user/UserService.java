package com.cashbank.user;

import java.util.UUID;

import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditService;
import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.common.web.PageResponse;
import com.cashbank.user.dto.UpdateProfileRequest;
import com.cashbank.user.dto.UserResponse;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    public static final String USERS_CACHE = "users";

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = USERS_CACHE, key = "#userId")
    public UserResponse getProfile(UUID userId) {
        return UserResponse.from(findById(userId));
    }

    @Transactional
    @CachePut(cacheNames = USERS_CACHE, key = "#userId")
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findById(userId);
        if (userRepository.existsByPhoneNumberAndIdNot(request.phoneNumber(), userId)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_USED);
        }
        user.updateProfile(request.firstName().trim(), request.lastName().trim(), request.phoneNumber());
        auditService.success(user, AuditAction.PROFILE_UPDATED, "USER", userId.toString(), null);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable), UserResponse::from);
    }

    @Transactional
    @CacheEvict(cacheNames = USERS_CACHE, key = "#userId")
    public UserResponse setEnabled(UUID adminId, UUID userId, boolean enabled) {
        User user = findById(userId);
        user.setEnabled(enabled);
        auditService.success(adminId, AuditAction.USER_STATUS_CHANGED, "USER", userId.toString(),
                "enabled=" + enabled);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
