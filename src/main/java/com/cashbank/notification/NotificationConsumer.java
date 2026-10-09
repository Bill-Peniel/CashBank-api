package com.cashbank.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(NotificationMessage message) {
        notificationService.store(message);
        log.info("[SIMULATION] Notification {} envoyée à l'utilisateur {} : {}",
                message.type(), message.userId(), message.message());
    }
}
