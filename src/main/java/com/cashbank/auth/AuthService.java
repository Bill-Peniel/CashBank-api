package com.cashbank.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditService;
import com.cashbank.auth.dto.LoginRequest;
import com.cashbank.auth.dto.RegisterRequest;
import com.cashbank.auth.dto.TokenResponse;
import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.security.JwtService;
import com.cashbank.security.LoginAttemptService;
import com.cashbank.security.RefreshTokenService;
import com.cashbank.security.TokenBlacklistService;
import com.cashbank.user.Role;
import com.cashbank.user.User;
import com.cashbank.user.UserRepository;
import com.cashbank.user.dto.UserResponse;
import com.cashbank.wallet.WalletService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final Clock clock;
    // Checked for unknown emails so response time does not reveal which accounts exist.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, WalletService walletService, PasswordEncoder passwordEncoder,
            JwtService jwtService, RefreshTokenService refreshTokenService,
            TokenBlacklistService tokenBlacklistService, LoginAttemptService loginAttemptService,
            AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_USED);
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), request.phoneNumber(), Role.USER));
        walletService.createWallet(user);
        auditService.success(user, AuditAction.USER_REGISTERED, "USER", user.getId().toString(), null);
        log.info("Nouvel utilisateur inscrit : {}", user.getId());
        return UserResponse.from(user);
    }

    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        if (loginAttemptService.isLocked(email)) {
            auditService.failure(null, email, AuditAction.LOGIN, "USER", null, "compte temporairement bloqué");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        if (!passwordEncoder.matches(request.password(), hash) || user == null) {
            loginAttemptService.recordFailure(email);
            auditService.failure(user != null ? user.getId() : null, email, AuditAction.LOGIN, "USER", null,
                    "identifiants invalides");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            auditService.failure(user.getId(), email, AuditAction.LOGIN, "USER", null, "compte désactivé");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        loginAttemptService.reset(email);
        auditService.success(user, AuditAction.LOGIN, "USER", user.getId().toString(), null);
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        UUID userId = refreshTokenService.consume(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        auditService.success(user, AuditAction.TOKEN_REFRESHED, "USER", userId.toString(), null);
        return issueTokens(user);
    }

    public void logout(Jwt accessToken, String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        tokenBlacklistService.blacklist(accessToken.getId(), accessToken.getExpiresAt());
        auditService.success(UUID.fromString(accessToken.getSubject()), AuditAction.LOGOUT, "USER",
                accessToken.getSubject(), null);
    }

    private TokenResponse issueTokens(User user) {
        JwtService.AccessToken accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        long expiresIn = Duration.between(clock.instant(), accessToken.expiresAt()).toSeconds();
        return new TokenResponse(accessToken.value(), refreshToken, "Bearer", expiresIn,
                refreshTokenService.ttl().toSeconds());
    }
}
