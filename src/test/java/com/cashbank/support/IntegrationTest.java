package com.cashbank.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.cashbank.TestcontainersConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Secret123";
    protected static final String ADMIN_EMAIL = "admin@cashbank.local";
    protected static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    protected MockMvc mockMvc;

    protected record TestUser(UUID id, String email, String accessToken, String refreshToken, String walletNumber) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    protected TestUser newUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        register(email, randomPhone(), PASSWORD).andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    protected TestUser login(String email, String password) throws Exception {
        String tokens = postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(email, password), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(tokens, "$.accessToken");
        String refreshToken = JsonPath.read(tokens, "$.refreshToken");

        String me = getJson("/api/v1/users/me", "Bearer " + accessToken).andReturn().getResponse().getContentAsString();
        String wallet = getJson("/api/v1/wallet", "Bearer " + accessToken).andReturn().getResponse().getContentAsString();
        String walletNumber = wallet.isEmpty() ? null : tryRead(wallet, "$.walletNumber");
        return new TestUser(UUID.fromString(JsonPath.read(me, "$.id")), email, accessToken, refreshToken, walletNumber);
    }

    protected ResultActions register(String email, String phone, String password) throws Exception {
        return postJson("/api/v1/auth/register", """
                {"firstName": "Koffi", "lastName": "Agbo", "email": "%s", "phoneNumber": "%s", "password": "%s"}
                """.formatted(email, phone, password), null);
    }

    protected ResultActions deposit(TestUser user, String amount) throws Exception {
        return postJson("/api/v1/wallet/deposit", """
                {"amount": %s, "description": "Rechargement"}""".formatted(amount), user.bearer());
    }

    protected ResultActions withdraw(TestUser user, String amount) throws Exception {
        return postJson("/api/v1/wallet/withdraw", """
                {"amount": %s}""".formatted(amount), user.bearer());
    }

    protected ResultActions transfer(TestUser from, String toWalletNumber, String amount) throws Exception {
        return postJson("/api/v1/wallet/transfer", """
                {"walletNumber": "%s", "amount": %s, "description": "Loyer"}""".formatted(toWalletNumber, amount),
                from.bearer());
    }

    protected BigDecimal balanceOf(TestUser user) throws Exception {
        String body = getJson("/api/v1/wallet", user.bearer()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.read(body, "$.balance").toString());
    }

    protected ResultActions postJson(String url, String json, String authorization) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getJson(String url, String authorization) throws Exception {
        var request = get(url);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    protected static String randomPhone() {
        return "+229" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L);
    }

    private static String tryRead(String json, String path) {
        try {
            return JsonPath.read(json, path);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
