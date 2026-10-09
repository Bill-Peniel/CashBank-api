package com.cashbank.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cashbank.transaction.TransactionCompletedEvent;
import com.cashbank.transaction.TransactionCompletedEvent.Party;
import com.cashbank.transaction.TransactionType;

import org.junit.jupiter.api.Test;

class NotificationPublisherTest {

    private final Party alice = new Party(UUID.randomUUID(), "Alice Dossou", "CB0000000001");
    private final Party bob = new Party(UUID.randomUUID(), "Bob Hounsou", "CB0000000002");

    @Test
    void transferNotifiesBothSenderAndReceiver() {
        List<NotificationMessage> messages = NotificationPublisher.toMessages(new TransactionCompletedEvent(
                "TX1", TransactionType.TRANSFER, new BigDecimal("15000"), "XOF", alice, bob));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).userId()).isEqualTo(alice.userId());
        assertThat(messages.get(0).type()).isEqualTo(NotificationType.TRANSFER_SENT);
        assertThat(messages.get(0).message()).contains("Bob Hounsou", "CB0000000002");
        assertThat(messages.get(1).userId()).isEqualTo(bob.userId());
        assertThat(messages.get(1).type()).isEqualTo(NotificationType.TRANSFER_RECEIVED);
        assertThat(messages.get(1).message()).contains("Alice Dossou");
    }

    @Test
    void depositNotifiesOnlyTheOwner() {
        List<NotificationMessage> messages = NotificationPublisher.toMessages(new TransactionCompletedEvent(
                "TX2", TransactionType.DEPOSIT, new BigDecimal("5000"), "XOF", null, alice));

        assertThat(messages).singleElement().satisfies(m -> {
            assertThat(m.userId()).isEqualTo(alice.userId());
            assertThat(m.type()).isEqualTo(NotificationType.DEPOSIT_RECEIVED);
            assertThat(m.reference()).isEqualTo("TX2");
        });
    }
}
