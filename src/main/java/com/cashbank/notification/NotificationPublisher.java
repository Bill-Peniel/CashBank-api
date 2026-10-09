package com.cashbank.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.cashbank.transaction.TransactionCompletedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        for (NotificationMessage message : toMessages(event)) {
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
            } catch (AmqpException e) {
                // Funds are already committed: a lost notification must not fail the operation.
                log.error("Échec de publication de la notification {} pour {}", message.type(), message.userId(), e);
            }
        }
    }

    static List<NotificationMessage> toMessages(TransactionCompletedEvent e) {
        String amount = format(e.amount()) + " " + e.currency();
        List<NotificationMessage> messages = new ArrayList<>();
        switch (e.type()) {
            case DEPOSIT -> messages.add(new NotificationMessage(e.destination().userId(),
                    NotificationType.DEPOSIT_RECEIVED, "Dépôt reçu",
                    "Votre portefeuille a été crédité de %s.".formatted(amount), e.reference()));
            case WITHDRAWAL -> messages.add(new NotificationMessage(e.source().userId(),
                    NotificationType.WITHDRAWAL_COMPLETED, "Retrait effectué",
                    "Un retrait de %s a été effectué sur votre portefeuille.".formatted(amount), e.reference()));
            case TRANSFER -> {
                messages.add(new NotificationMessage(e.source().userId(), NotificationType.TRANSFER_SENT,
                        "Transfert envoyé", "Vous avez envoyé %s à %s (%s).".formatted(amount,
                                e.destination().fullName(), e.destination().walletNumber()), e.reference()));
                messages.add(new NotificationMessage(e.destination().userId(), NotificationType.TRANSFER_RECEIVED,
                        "Transfert reçu", "Vous avez reçu %s de %s.".formatted(amount, e.source().fullName()),
                        e.reference()));
            }
        }
        return messages;
    }

    private static String format(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}
