package com.cashbank.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.cashbank.audit.AuditAction;
import com.cashbank.audit.AuditLogRepository;
import com.cashbank.audit.AuditOutcome;
import com.cashbank.common.exception.BusinessException;
import com.cashbank.common.exception.ErrorCode;
import com.cashbank.notification.NotificationRepository;
import com.cashbank.support.IntegrationTest;
import com.cashbank.wallet.dto.AmountRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class WalletIntegrationTest extends IntegrationTest {

    @Autowired WalletService walletService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Test
    void depositAndWithdrawalUpdateBalance() throws Exception {
        TestUser user = newUser();

        deposit(user, "10000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.direction").value("CREDIT"))
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        withdraw(user, "2500.50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("DEBIT"));

        assertThat(balanceOf(user)).isEqualByComparingTo("7499.50");
    }

    @Test
    void transferMovesFundsAndNotifiesBothPartiesAsynchronously() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        deposit(alice, "50000").andExpect(status().isCreated());

        transfer(alice, bob.walletNumber(), "15000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.sourceWalletNumber").value(alice.walletNumber()))
                .andExpect(jsonPath("$.destinationWalletNumber").value(bob.walletNumber()));

        assertThat(balanceOf(alice)).isEqualByComparingTo("35000");
        assertThat(balanceOf(bob)).isEqualByComparingTo("15000");

        getJson("/api/v1/wallet/transactions", bob.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findByUserId(bob.id(), Pageable.unpaged()).getContent())
                        .anySatisfy(n -> assertThat(n.getTitle()).isEqualTo("Transfert reçu")));
        getJson("/api/v1/notifications/unread-count", bob.bearer())
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void transferWithInsufficientFundsChangesNothingAndIsAudited() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        deposit(alice, "1000").andExpect(status().isCreated());

        transfer(alice, bob.walletNumber(), "5000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(alice)).isEqualByComparingTo("1000");
        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getActorId()).isEqualTo(alice.id());
            assertThat(log.getAction()).isEqualTo(AuditAction.TRANSFER);
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        });
    }

    @Test
    void transferToOwnWalletOrUnknownWalletIsRejected() throws Exception {
        TestUser alice = newUser();
        deposit(alice, "1000").andExpect(status().isCreated());

        transfer(alice, alice.walletNumber(), "100")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_WALLET_TRANSFER"));
        transfer(alice, "CB0000000000", "100")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void transferToRegisteredBeneficiary() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        deposit(alice, "5000").andExpect(status().isCreated());

        String beneficiary = postJson("/api/v1/beneficiaries", """
                {"walletNumber": "%s", "alias": "Bob"}""".formatted(bob.walletNumber()), alice.bearer())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holderName").value("Koffi A."))
                .andReturn().getResponse().getContentAsString();
        String beneficiaryId = JsonPath.read(beneficiary, "$.id");

        postJson("/api/v1/wallet/transfer", """
                {"beneficiaryId": "%s", "amount": 2000}""".formatted(beneficiaryId), alice.bearer())
                .andExpect(status().isCreated());

        assertThat(balanceOf(bob)).isEqualByComparingTo("2000");
    }

    @Test
    void sameIdempotencyKeyIsProcessedOnlyOnce() throws Exception {
        TestUser user = newUser();
        var request = MockMvcRequestBuilders.post("/api/v1/wallet/deposit")
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", "deposit-42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": 3000}""");

        String first = mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.reference")).isEqualTo(JsonPath.read(first, "$.reference"));
        assertThat(balanceOf(user)).isEqualByComparingTo("3000");
    }

    @Test
    void perTransactionAndDailyLimitsAreEnforced() throws Exception {
        TestUser user = newUser();
        deposit(user, "1000001")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TRANSACTION_LIMIT_EXCEEDED"));
        deposit(user, "50").andExpect(jsonPath("$.code").value("AMOUNT_TOO_LOW"));

        for (int i = 0; i < 3; i++) {
            deposit(user, "1000000").andExpect(status().isCreated());
        }
        withdraw(user, "1000000").andExpect(status().isCreated());
        withdraw(user, "1000000").andExpect(status().isCreated());

        withdraw(user, "100")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        getJson("/api/v1/wallet/limits", user.bearer())
                .andExpect(jsonPath("$.usedToday").value(2000000))
                .andExpect(jsonPath("$.remainingToday").value(0));
    }

    @Test
    void historyIsPaginatedAndFilterable() throws Exception {
        TestUser user = newUser();
        for (int i = 1; i <= 5; i++) {
            deposit(user, String.valueOf(1000 * i)).andExpect(status().isCreated());
        }
        withdraw(user, "500").andExpect(status().isCreated());

        getJson("/api/v1/wallet/transactions?page=0&size=2", user.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"));

        getJson("/api/v1/wallet/transactions?type=DEPOSIT&sort=amount,desc", user.bearer())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].amount").value(5000));

        getJson("/api/v1/wallet/transactions?sort=unknownField", user.bearer())
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCannotSeeTransactionsOfOthers() throws Exception {
        TestUser alice = newUser();
        TestUser eve = newUser();
        String body = deposit(alice, "1000").andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(body, "$.reference");

        getJson("/api/v1/wallet/transactions/" + reference, alice.bearer()).andExpect(status().isOk());
        getJson("/api/v1/wallet/transactions/" + reference, eve.bearer())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        TestUser user = newUser();
        deposit(user, "10000").andExpect(status().isCreated());

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                try {
                    walletService.withdraw(user.id(), new AmountRequest(new BigDecimal("1000"), null), null);
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                    return false;
                }
            });
        }

        int successes = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (Future<Boolean> result : pool.invokeAll(tasks)) {
                if (result.get()) {
                    successes++;
                }
            }
        }

        assertThat(successes).isEqualTo(10);
        assertThat(balanceOf(user)).isEqualByComparingTo("0");
    }
}
