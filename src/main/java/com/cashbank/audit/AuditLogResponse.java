package com.cashbank.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorEmail,
        AuditAction action,
        AuditOutcome outcome,
        String resourceType,
        String resourceId,
        String details,
        String ipAddress,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorId(), log.getActorEmail(), log.getAction(),
                log.getOutcome(), log.getResourceType(), log.getResourceId(), log.getDetails(),
                log.getIpAddress(), log.getCreatedAt());
    }
}
