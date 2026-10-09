package com.cashbank.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cashbank.common.web.PageResponse;
import com.cashbank.user.User;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    // Joins the business transaction: the operation and its audit entry commit together.
    @Transactional
    public void success(User actor, AuditAction action, String resourceType, String resourceId, String details) {
        save(actor.getId(), actor.getEmail(), action, AuditOutcome.SUCCESS, resourceType, resourceId, details);
    }

    @Transactional
    public void success(UUID actorId, AuditAction action, String resourceType, String resourceId, String details) {
        save(actorId, null, action, AuditOutcome.SUCCESS, resourceType, resourceId, details);
    }

    // Own transaction so the entry survives the business rollback.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(UUID actorId, String actorEmail, AuditAction action, String resourceType,
            String resourceId, String details) {
        save(actorId, actorEmail, action, AuditOutcome.FAILURE, resourceType, resourceId, details);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID actorId, AuditAction action, AuditOutcome outcome,
            Pageable pageable) {
        List<Specification<AuditLog>> filters = new ArrayList<>();
        if (actorId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (action != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (outcome != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("outcome"), outcome));
        }
        return PageResponse.of(repository.findAll(Specification.allOf(filters), pageable), AuditLogResponse::from);
    }

    private void save(UUID actorId, String actorEmail, AuditAction action, AuditOutcome outcome,
            String resourceType, String resourceId, String details) {
        repository.save(new AuditLog(actorId, actorEmail, action, outcome, resourceType, resourceId, details,
                currentIp()));
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
