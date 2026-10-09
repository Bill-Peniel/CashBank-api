package com.cashbank.auth;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.cashbank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registrationCreatesUserWithEmptyWallet() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.com";

        register(email, randomPhone(), PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        TestUser user = login(email, PASSWORD);
        getJson("/api/v1/wallet", user.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletNumber").value(matchesPattern("CB\\d{10}")))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("XOF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        TestUser existing = newUser();

        register(existing.email(), randomPhone(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        register("not-an-email", "12", "weak")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        TestUser user = newUser();

        postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "WrongPass1"}""".formatted(user.email()), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountIsLockedAfterTooManyFailures() throws Exception {
        TestUser user = newUser();
        String badLogin = """
                {"email": "%s", "password": "WrongPass1"}""".formatted(user.email());
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", badLogin, null).andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(user.email(), PASSWORD), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        getJson("/api/v1/wallet", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        getJson("/api/v1/wallet", "Bearer not.a.jwt")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsRotatedAndSingleUse() throws Exception {
        TestUser user = newUser();
        String body = """
                {"refreshToken": "%s"}""".formatted(user.refreshToken());

        postJson("/api/v1/auth/refresh", body, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(user.refreshToken())));

        postJson("/api/v1/auth/refresh", body, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        TestUser user = newUser();
        String body = """
                {"refreshToken": "%s"}""".formatted(user.refreshToken());

        postJson("/api/v1/auth/logout", body, user.bearer()).andExpect(status().isNoContent());

        getJson("/api/v1/users/me", user.bearer()).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh", body, null).andExpect(status().isUnauthorized());
    }

    @Test
    void profileCanBeReadAndUpdated() throws Exception {
        TestUser user = newUser();
        String newPhone = randomPhone();

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Afi", "lastName": "Mensah", "phoneNumber": "%s"}""".formatted(newPhone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Afi"));

        getJson("/api/v1/users/me", user.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Afi"))
                .andExpect(jsonPath("$.phoneNumber").value(newPhone));
    }
}
