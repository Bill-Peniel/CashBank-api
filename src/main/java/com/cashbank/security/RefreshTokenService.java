package com.cashbank.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final String PREFIX = "cashbank:refresh-token:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenService(StringRedisTemplate redis, SecurityProperties properties) {
        this.redis = redis;
        this.ttl = properties.jwt().refreshTokenTtl();
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), userId.toString(), ttl);
        return token;
    }

    public UUID consume(String token) {
        // Atomic GETDEL: each refresh token can be used only once.
        String userId = redis.opsForValue().getAndDelete(key(token));
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return UUID.fromString(userId);
    }

    public void revoke(String token) {
        redis.delete(key(token));
    }

    public Duration ttl() {
        return ttl;
    }

    // Only the SHA-256 hash is stored, never the token itself.
    private static String key(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
