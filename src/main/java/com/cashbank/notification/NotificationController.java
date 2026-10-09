package com.cashbank.notification;

import java.util.Map;
import java.util.UUID;

import com.cashbank.common.web.PageResponse;
import com.cashbank.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Lister ses notifications")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(CurrentUser.id(jwt), unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", notificationService.unreadCount(CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public NotificationResponse markAsRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return notificationService.markAsRead(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu")
    public Map<String, Integer> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", notificationService.markAllAsRead(CurrentUser.id(jwt)));
    }
}
