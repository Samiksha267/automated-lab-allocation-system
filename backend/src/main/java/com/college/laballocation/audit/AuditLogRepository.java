package com.college.laballocation.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Deliberately extends plain {@link JpaRepository} rather than a narrower
 * hand-picked interface (PART 39/40 of the phase brief) - {@code save} is
 * genuinely needed (every recorded event is a fresh insert) and the
 * inherited {@code delete}/{@code deleteById} methods are simply never
 * called by any application code path (verified by
 * {@code AuditLogImmutabilityTest}). Immutability here is an architectural
 * property (no code path exists that mutates or removes a row), not a
 * type-level restriction - see {@link AuditLog}'s class javadoc for why a
 * database-level trigger was considered and not added.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {}
