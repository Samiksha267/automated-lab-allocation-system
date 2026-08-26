package com.college.laballocation.audit;

import com.college.laballocation.audit.AuditLogDtos.AuditLogResponse;
import com.college.laballocation.audit.AuditLogDtos.AuditLogSearchCriteria;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single, centralized place every audited action is recorded through
 * (PART 19 of the phase brief) - no controller or service anywhere calls
 * {@link AuditLogRepository#save} directly.
 *
 * <p><b>Why search/read actions are never audited</b> (PART 9): a CR
 * searching for available labs, or a Lab Assistant listing faculty
 * availability, changes no state and produces no historical fact worth
 * preserving forever - only logging state-changing actions keeps this
 * table's signal-to-noise ratio meaningful. If usage analytics on search
 * behavior is ever wanted, that is a fundamentally different (and
 * explicitly out-of-scope, PART "do not convert audit logs into analytics")
 * concern from immutable historical evidence.
 *
 * <p><b>Same-transaction semantics</b> (PART 21/31): {@link #record} carries
 * no {@code @Transactional} propagation override, so it always joins
 * whichever transaction its caller (a state-changing service method) is
 * already running in - the default Spring propagation, {@code REQUIRED}.
 * If the audit insert fails for any reason, the exception propagates
 * normally and the enclosing transaction rolls back exactly as if any other
 * statement in that method had failed - audit persistence is never silently
 * swallowed, and a business mutation can never commit without its
 * corresponding audit row (or fail to commit at all, taking the audit
 * attempt down with it). This is why {@link #record} is called from deep
 * inside each mutation service method, never from a controller after the
 * service call returns (which would run in a separate transaction and
 * break this guarantee entirely).
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(AuditEvent event) {
        auditLogRepository.save(new AuditLog(
                event.actorUserId(),
                event.actorRole(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.resourceDisplay(),
                event.academicTermId(),
                event.divisionId(),
                event.metadata()));
    }

    /**
     * Lab-Assistant activity search (PART 32-36). Resolves every distinct
     * actor on the returned page with exactly one bulk
     * {@code UserRepository.findAllById(...)} call, never one query per row
     * (PART 78) - deliberately not a JPA {@code @ManyToOne} on {@link AuditLog}
     * for this exact reason (see that class's javadoc).
     */
    public Page<AuditLogResponse> search(AuditLogSearchCriteria criteria, Pageable pageable) {
        Specification<AuditLog> spec = Specification.allOf();
        if (criteria.actorUserId() != null) {
            spec = spec.and(AuditLogSpecifications.actorUserId(criteria.actorUserId()));
        }
        if (criteria.action() != null) {
            spec = spec.and(AuditLogSpecifications.action(criteria.action()));
        }
        if (criteria.resourceType() != null) {
            spec = spec.and(AuditLogSpecifications.resourceType(criteria.resourceType()));
        }
        if (criteria.academicTermId() != null) {
            spec = spec.and(AuditLogSpecifications.academicTermId(criteria.academicTermId()));
        }
        if (criteria.divisionId() != null) {
            spec = spec.and(AuditLogSpecifications.divisionId(criteria.divisionId()));
        }
        if (criteria.from() != null) {
            spec = spec.and(AuditLogSpecifications.createdAtFrom(criteria.from()));
        }
        if (criteria.to() != null) {
            spec = spec.and(AuditLogSpecifications.createdAtTo(criteria.to()));
        }

        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);

        List<Long> actorIds = page.getContent().stream().map(AuditLog::getActorUserId).distinct().toList();
        Map<Long, AppUser> actorsById = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u, (a, b) -> a, HashMap::new));

        return page.map(log -> toResponse(log, actorsById.get(log.getActorUserId())));
    }

    private AuditLogResponse toResponse(AuditLog log, AppUser actor) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorUserId(),
                actor != null ? actor.getDisplayName() : null,
                actor != null ? actor.getEmail() : null,
                log.getActorRole().name(),
                log.getAction().name(),
                log.getResourceType().name(),
                log.getResourceId(),
                log.getResourceDisplay(),
                log.getAcademicTermId(),
                log.getDivisionId(),
                log.getMetadata(),
                log.getCreatedAt());
    }
}
