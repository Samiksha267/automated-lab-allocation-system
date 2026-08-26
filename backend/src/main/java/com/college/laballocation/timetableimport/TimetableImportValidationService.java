package com.college.laballocation.timetableimport;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.common.TimeIntervalUtils;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.CandidateAllocationFactory;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingContextFactory;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.constraint.ConstraintEngine;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Runs every staged row through this project's <em>existing</em> hard
 * constraints (PART 21 - no second constraint engine is implemented here) via
 * the same {@code SchedulingContextFactory}/{@code CandidateAllocationFactory}/
 * {@code ConstraintEngine} pipeline Phase 14/15 already use, plus an
 * import-local cross-row check (PART 22) - two rows in the <em>same</em> PDF
 * conflicting with each other, which the constraint engine alone cannot see
 * since neither row is persisted yet.
 *
 * <p><b>Recurring session -&gt; single date</b> (ADR-099, docs/15-DESIGN-DECISIONS.md):
 * a PDF row describes a weekly-recurring slot ({@code "MONDAY 09:00-11:00"}),
 * but {@code Allocation.allocationDate} is a single, specific date. Phase 19
 * maps each row to exactly one {@link Allocation} - the target term's
 * <em>first</em> occurrence of that weekday on or after
 * {@code AcademicTerm.startDate} - a deliberate scope decision, not whole-term
 * recurring expansion (see the ADR for why).
 */
@Component
class TimetableImportValidationService {

    private final SchedulingContextFactory schedulingContextFactory;
    private final CandidateAllocationFactory candidateAllocationFactory;
    private final ConstraintEngine constraintEngine;

    TimetableImportValidationService(
            SchedulingContextFactory schedulingContextFactory,
            CandidateAllocationFactory candidateAllocationFactory,
            ConstraintEngine constraintEngine) {
        this.schedulingContextFactory = schedulingContextFactory;
        this.candidateAllocationFactory = candidateAllocationFactory;
        this.constraintEngine = constraintEngine;
    }

    record ValidatedRow(
            ParsedTimetableRow raw,
            TimetableMappingService.MappingResult mapping,
            LocalDate allocationDate,
            List<ImportValidationMessage> messages,
            ImportRowStatus status) {}

    static LocalDate firstOccurrenceOnOrAfter(LocalDate start, java.time.DayOfWeek day) {
        return start.getDayOfWeek() == day ? start : start.with(TemporalAdjusters.next(day));
    }

    /** Validates every row - hard constraints (per row, against persisted state) then cross-row conflicts (against the other rows in this same import). */
    List<ValidatedRow> validateAll(
            List<ParsedTimetableRow> rawRows, TimetableMappingService.MappingContext mappingContext, TimetableMappingService mappingService,
            AcademicTerm term) {
        List<ValidatedRow> results = new ArrayList<>();
        for (ParsedTimetableRow raw : rawRows) {
            results.add(validateOne(raw, mappingContext, mappingService, term));
        }
        return applyCrossRowConflicts(results);
    }

    /**
     * Applies the import-local cross-row conflict pass (PART 22) to an
     * already-per-row-validated set - shared by {@link #validateAll} (a
     * fresh upload) and {@code TimetableImportService.revalidateImport}
     * (post-correction / approval-time revalidation), so both paths detect
     * "this row conflicts with another row in the same import" identically.
     */
    List<ValidatedRow> applyCrossRowConflicts(List<ValidatedRow> results) {
        Map<ValidatedRow, List<ImportValidationMessage>> crossRowMessages = detectCrossRowConflicts(results);
        return results.stream().map(row -> finalizeStatus(row, crossRowMessages)).toList();
    }

    private ValidatedRow validateOne(
            ParsedTimetableRow raw, TimetableMappingService.MappingContext mappingContext, TimetableMappingService mappingService,
            AcademicTerm term) {
        TimetableMappingService.MappingResult mapping = mappingService.mapRow(raw, mappingContext);
        List<ImportValidationMessage> messages = new ArrayList<>(mapping.messages());

        boolean fullyMapped = mapping.day() != null && mapping.startTime() != null && mapping.endTime() != null
                && mapping.subject() != null && mapping.faculty() != null && mapping.lab() != null && mapping.division() != null;
        if (!fullyMapped) {
            // Cannot build a SchedulingRequest at all - mapping already explained why via `messages`.
            return new ValidatedRow(raw, mapping, null, messages, ImportRowStatus.ERROR);
        }

        LocalDate allocationDate = firstOccurrenceOnOrAfter(term.getStartDate(), mapping.day());
        return runConstraintPipeline(raw, mapping, allocationDate, term, messages);
    }

    /**
     * Re-runs constraint validation for a row whose mapping is already
     * resolved (a correction, or approval-time revalidation, PART 29/34) -
     * skips the mapping step entirely, reusing the exact same constraint
     * pipeline {@link #validateOne} uses for a freshly-parsed row. Cross-row
     * conflicts are not (re)computed here - the caller re-runs
     * {@link #validateAll}'s cross-row pass, or accepts this method's result
     * as the per-row hard-constraint outcome only.
     */
    ValidatedRow revalidateMappedRow(
            ParsedTimetableRow raw, TimetableMappingService.MappingResult mapping, LocalDate allocationDate, AcademicTerm term) {
        return runConstraintPipeline(raw, mapping, allocationDate, term, new ArrayList<>(mapping.messages()));
    }

    private ValidatedRow runConstraintPipeline(
            ParsedTimetableRow raw, TimetableMappingService.MappingResult mapping, LocalDate allocationDate, AcademicTerm term,
            List<ImportValidationMessage> messages) {
        TargetType targetType = mapping.batch() != null ? TargetType.BATCH : TargetType.DIVISION;
        SchedulingRequest request = new SchedulingRequest(
                AllocationType.REGULAR, targetType, mapping.division().getId(),
                mapping.batch() != null ? mapping.batch().getId() : null,
                mapping.subject().getId(), mapping.faculty().getId(), term.getId(),
                allocationDate, mapping.startTime(), mapping.endTime(), null);

        SchedulingContext context = schedulingContextFactory.build(request);
        CandidateAllocation candidate = candidateAllocationFactory.build(context, mapping.lab().getId());
        ConstraintEvaluation evaluation = constraintEngine.evaluate(context, candidate);
        for (var violation : evaluation.violations()) {
            messages.add(ImportValidationMessage.error(violation.errorCode(), violation.message(), violation.details()));
        }

        ImportRowStatus status = messages.stream().anyMatch(m -> m.severity() == ImportRowStatus.ERROR)
                ? ImportRowStatus.ERROR
                : messages.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.WARNING;
        return new ValidatedRow(raw, mapping, allocationDate, messages, status);
    }

    /**
     * Returns per-row cross-row conflict messages as a local value, never a
     * shared/instance field - this is a singleton Spring bean, and two
     * concurrent import validations (a real, expected scenario under Phase
     * 16-style concurrent approval load) must never share mutable state.
     * Keyed by identity ({@link java.util.IdentityHashMap}), not
     * {@code ValidatedRow}'s generated value-equality, since two distinct
     * rows could otherwise coincidentally compare equal.
     */
    private Map<ValidatedRow, List<ImportValidationMessage>> detectCrossRowConflicts(List<ValidatedRow> rows) {
        Map<ValidatedRow, List<ImportValidationMessage>> extraMessages = new java.util.IdentityHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ValidatedRow a = rows.get(i);
            if (a.allocationDate() == null) {
                continue;
            }
            for (int j = i + 1; j < rows.size(); j++) {
                ValidatedRow b = rows.get(j);
                if (b.allocationDate() == null || !a.allocationDate().equals(b.allocationDate())
                        || !TimeIntervalUtils.overlaps(a.mapping().startTime(), a.mapping().endTime(), b.mapping().startTime(), b.mapping().endTime())) {
                    continue;
                }
                if (a.mapping().lab().getId().equals(b.mapping().lab().getId())) {
                    addCrossRowConflict(
                            extraMessages, a, b, "LAB_DOUBLE_BOOKING_IN_IMPORT",
                            "Lab " + a.mapping().lab().getCode() + " is double-booked within this import");
                }
                if (a.mapping().faculty().getId().equals(b.mapping().faculty().getId())) {
                    addCrossRowConflict(
                            extraMessages, a, b, "FACULTY_DOUBLE_BOOKING_IN_IMPORT",
                            "Faculty " + a.mapping().faculty().getName() + " is double-booked within this import");
                }
                boolean sameDivision = a.mapping().division().getId().equals(b.mapping().division().getId());
                boolean batchOverlap = a.mapping().batch() == null || b.mapping().batch() == null
                        || a.mapping().batch().getId().equals(b.mapping().batch().getId());
                if (sameDivision && batchOverlap) {
                    addCrossRowConflict(extraMessages, a, b, "DIVISION_BATCH_CONFLICT_IN_IMPORT", "Division/batch is double-booked within this import");
                }
            }
        }
        return extraMessages;
    }

    private void addCrossRowConflict(
            Map<ValidatedRow, List<ImportValidationMessage>> extraMessages, ValidatedRow a, ValidatedRow b, String code, String message) {
        extraMessages.computeIfAbsent(a, k -> new ArrayList<>())
                .add(ImportValidationMessage.error(code, message, Map.of("otherRowNumber", b.raw().sourceLineNumber())));
        extraMessages.computeIfAbsent(b, k -> new ArrayList<>())
                .add(ImportValidationMessage.error(code, message, Map.of("otherRowNumber", a.raw().sourceLineNumber())));
    }

    private ValidatedRow finalizeStatus(ValidatedRow row, Map<ValidatedRow, List<ImportValidationMessage>> crossRowMessages) {
        List<ImportValidationMessage> extra = crossRowMessages.get(row);
        if (extra == null || extra.isEmpty()) {
            return row;
        }
        List<ImportValidationMessage> combined = new ArrayList<>(row.messages());
        combined.addAll(extra);
        return new ValidatedRow(row.raw(), row.mapping(), row.allocationDate(), combined, ImportRowStatus.ERROR);
    }
}
