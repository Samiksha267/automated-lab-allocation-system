package com.college.laballocation.scheduling;

import com.college.laballocation.user.UserRole;

/**
 * The identity/role of whoever originated a {@link SchedulingRequest}, if
 * any - resolved before the request is built, the same way {@code facultyId}
 * is already resolved (PART 15/36 of the Phase 9 brief), never derived
 * inside a constraint from an HTTP/security context. {@code null} on the
 * request means no actor context at all (e.g. automated REGULAR schedule
 * generation, Phase 14) - deliberately distinct from an actor whose
 * authorization simply fails, see {@code CrAuthorizationConstraint}.
 *
 * <p>Reuses {@link UserRole} rather than a duplicate role enum - it is
 * already a plain, framework-free domain type (no Spring Security
 * dependency), so importing it here does not violate the "keep scheduling
 * domain objects HTTP/security-free" rule; it is the same rule this project
 * already treats as domain-neutral everywhere else (e.g. {@code AppUser.getRole()}).
 */
public record SchedulingActor(Long userId, UserRole role) {}
