package com.college.laballocation.scheduling;

/**
 * {@code DRAFT -> PUBLISHED -> SUPERSEDED}, strictly forward - never
 * {@code SUPERSEDED -> DRAFT} (a superseded version is permanent history,
 * ADR-009). Publishing a new version transitions the term's previous
 * {@code PUBLISHED} version to {@code SUPERSEDED} in the same transaction
 * (never deleted) - see {@code ScheduleVersionService.publish}.
 */
public enum ScheduleVersionStatus {
    DRAFT,
    PUBLISHED,
    SUPERSEDED
}
