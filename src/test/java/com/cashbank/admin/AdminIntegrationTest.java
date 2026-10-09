package com.cashbank.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cashbank.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AdminIntegrationTest extends IntegrationTest {

    @Test
    void regularUserCannotAccessAdminEndpoints() throws Exception {
        TestUser user = newUser();

        getJson("/api/v1/admin/users", user.bearer())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void frozenWalletCannotSendOrReceiveFunds() throws Exception {
        TestUser admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        TestUser alice = newUser();
        TestUser bob = newUser();
        deposit(alice, "5000").andExpect(status().isCreated());

        patchJson("/api/v1/admin/wallets/" + bob.walletNumber() + "/status", """
                {"status": "FROZEN"}""", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        transfer(alice, bob.walletNumber(), "1000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));

        patchJson("/api/v1/admin/wallets/" + bob.walletNumber() + "/status", """
                {"status": "ACTIVE"}""", admin).andExpect(status().isOk());
        transfer(alice, bob.walletNumber(), "1000").andExpect(status().isCreated());
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        TestUser admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        TestUser user = newUser();

        patchJson("/api/v1/admin/users/" + user.id() + "/status", """
                {"enabled": false}""", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(user.email(), PASSWORD), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void adminCanSearchTransactionsAndAuditLogs() throws Exception {
        TestUser admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        TestUser user = newUser();
        deposit(user, "2000").andExpect(status().isCreated());
        withdraw(user, "999999").andExpect(status().isUnprocessableContent());

        getJson("/api/v1/admin/transactions?type=DEPOSIT&size=5", admin.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].direction").doesNotExist());

        getJson("/api/v1/admin/audit-logs?actorId=" + user.id() + "&outcome=FAILURE", admin.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("WITHDRAWAL"));
    }

    private ResultActions patchJson(String url, String json, TestUser as) throws Exception {
        return mockMvc.perform(patch(url)
                .header("Authorization", as.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
