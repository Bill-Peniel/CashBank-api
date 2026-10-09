package com.cashbank.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final String PREFIX = "cashbank:login-attempts:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(StringRedisTemplate redis, SecurityProperties properties) {
        this.redis = redis;
        this.maxAttempts = properties.login().maxAttempts();
        this.lockDuration = properties.login().lockDuration();
    }

    public boolean isLocked(String email) {
        String value = redis.opsForValue().get(key(email));
        return value != null && Integer.parseInt(value) >= maxAttempts;
    }

    public void recordFailure(String email) {
        Long attempts = redis.opsForValue().increment(key(email));
        if (attempts != null && attempts == 1) {
            redis.expire(key(email), lockDuration);
        }
    }

    public void reset(String email) {
        redis.delete(key(email));
    }

    private static String key(String email) {
        return PREFIX + email.toLowerCase();
    }
}
