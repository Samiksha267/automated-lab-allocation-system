package com.college.laballocation.timetableimport;

import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.faculty.SubjectFacultyAssignmentRepository;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.subject.Subject;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves normalized PDF text to <em>existing</em> database entities - never
 * creates one (PART 18: "a Lab Assistant must resolve the mismatch," not the
 * parser). No hard-coded college data anywhere in this class (PART 66); every
 * decision comes from bulk-loaded database rows.
 *
 * <p><b>Mapping strategy</b> (ADR-098, docs/15-DESIGN-DECISIONS.md): subject,
 * faculty, division, and batch are resolved <em>together</em>, as one unit, by
 * matching a row's normalized subject/division/batch codes against this
 * project's existing {@link SubjectFacultyAssignment} table for the target
 * term - the same authoritative "which subject+division+batch+faculty
 * combinations are valid this term" source {@code SchedulingContextFactory}
 * already resolves faculty from (Phase 4/14). A row's raw faculty text is
 * never independently matched against a faculty-name index; it is only
 * cross-checked as a {@code WARNING} against the assignment's actual faculty
 * once resolved. Lab is resolved independently by exact code match
 * ({@link LabRepository#findByCode}, globally unique in this schema).
 *
 * <p>Bulk-loaded once per import via {@link #buildContext} (PART 68 - never
 * one query per row/field).
 */
@Component
class TimetableMappingService {

    private final SubjectFacultyAssignmentRepository subjectFacultyAssignmentRepository;
    private final LabRepository labRepository;

    TimetableMappingService(SubjectFacultyAssignmentRepository subjectFacultyAssignmentRepository, LabRepository labRepository) {
        this.subjectFacultyAssignmentRepository = subjectFacultyAssignmentRepository;
        this.labRepository = labRepository;
    }

    record AssignmentKey(String subjectCode, String divisionCode, String batchCode) {}

    record MappingContext(Map<AssignmentKey, SubjectFacultyAssignment> assignmentsByKey, Map<String, Lab> labsByCode) {}

    record MappingResult(
            DayOfWeek day,
            LocalTime startTime,
            LocalTime endTime,
            Subject subject,
            Faculty faculty,
            Lab lab,
            Division division,
            Batch batch,
            List<ImportValidationMessage> messages) {}

    MappingContext buildContext(Long academicTermId) {
        Map<AssignmentKey, SubjectFacultyAssignment> assignmentsByKey = new HashMap<>();
        for (SubjectFacultyAssignment assignment : subjectFacultyAssignmentRepository.findByAcademicTermIdAndActiveTrue(academicTermId)) {
            String batchCode = assignment.getBatch() != null ? TimetableNormalizer.normalizeToken(assignment.getBatch().getCode()) : null;
            assignmentsByKey.put(
                    new AssignmentKey(
                            TimetableNormalizer.normalizeToken(assignment.getSubject().getCode()),
                            TimetableNormalizer.normalizeToken(assignment.getDivision().getCode()),
                            batchCode),
                    assignment);
        }
        Map<String, Lab> labsByCode = new HashMap<>();
        for (Lab lab : labRepository.findAll()) {
            labsByCode.put(TimetableNormalizer.normalizeToken(lab.getCode()), lab);
        }
        return new MappingContext(assignmentsByKey, labsByCode);
    }

    MappingResult mapRow(ParsedTimetableRow raw, MappingContext context) {
        List<ImportValidationMessage> messages = new ArrayList<>();

        DayOfWeek day = TimetableNormalizer.normalizeDay(raw.rawDay());
        if (day == null) {
            messages.add(ImportValidationMessage.error(
                    "UNRECOGNIZED_DAY", "Could not interpret \"" + raw.rawDay() + "\" as a day of week.", Map.of("rawDay", raw.rawDay())));
        }
        LocalTime startTime = TimetableNormalizer.normalizeTime(raw.rawStartTime());
        LocalTime endTime = TimetableNormalizer.normalizeTime(raw.rawEndTime());
        if (startTime == null || endTime == null) {
            messages.add(ImportValidationMessage.error(
                    "MALFORMED_TIME",
                    "Times must be in 24-hour HH:MM format; got \"" + raw.rawStartTime() + "\"-\"" + raw.rawEndTime() + "\".",
                    Map.of("rawStartTime", raw.rawStartTime(), "rawEndTime", raw.rawEndTime())));
        } else if (!startTime.isBefore(endTime)) {
            messages.add(ImportValidationMessage.error(
                    "INVALID_TIME_RANGE", "startTime must be strictly before endTime.",
                    Map.of("startTime", startTime.toString(), "endTime", endTime.toString())));
        }

        String subjectCode = TimetableNormalizer.normalizeToken(raw.rawSubject());
        String divisionCode = TimetableNormalizer.normalizeToken(raw.rawDivision());
        String batchCode = TimetableNormalizer.normalizeToken(raw.rawBatch());

        SubjectFacultyAssignment assignment = null;
        if (subjectCode != null && divisionCode != null) {
            assignment = context.assignmentsByKey().get(new AssignmentKey(subjectCode, divisionCode, batchCode));
            if (assignment == null && batchCode == null) {
                // A blank raw batch might still legitimately match a BATCH-scoped assignment
                // is not attempted here - a DIVISION-wide PDF row stays DIVISION-wide; see
                // docs/18-PDF-IMPORT.md "Mapping limitations."
            }
        }
        if (assignment == null) {
            messages.add(ImportValidationMessage.error(
                    "UNRESOLVED_ACADEMIC_ASSIGNMENT",
                    "No active subject+division+batch+faculty assignment found for subject \"" + raw.rawSubject()
                            + "\", division \"" + raw.rawDivision() + "\", batch \"" + raw.rawBatch() + "\" in this term.",
                    Map.of("rawSubject", raw.rawSubject(), "rawDivision", raw.rawDivision(), "rawBatch", raw.rawBatch())));
        } else if (raw.rawFaculty() != null && !raw.rawFaculty().isBlank()
                && !TimetableNormalizer.normalizeToken(raw.rawFaculty()).contains(TimetableNormalizer.normalizeToken(assignment.getFaculty().getName()))
                && !TimetableNormalizer.normalizeToken(assignment.getFaculty().getName()).contains(TimetableNormalizer.normalizeToken(raw.rawFaculty()))) {
            messages.add(ImportValidationMessage.warning(
                    "FACULTY_NAME_MISMATCH",
                    "PDF lists faculty \"" + raw.rawFaculty() + "\" but the assigned faculty for this subject/division/batch is \""
                            + assignment.getFaculty().getName() + "\".",
                    Map.of("rawFaculty", raw.rawFaculty(), "assignedFacultyId", assignment.getFaculty().getId())));
        }

        Lab lab = raw.rawLab() != null ? context.labsByCode().get(TimetableNormalizer.normalizeToken(raw.rawLab())) : null;
        if (lab == null) {
            messages.add(ImportValidationMessage.error(
                    "UNKNOWN_LAB", "No lab found with code \"" + raw.rawLab() + "\".", Map.of("rawLab", raw.rawLab())));
        }

        return new MappingResult(
                day,
                startTime,
                endTime,
                assignment != null ? assignment.getSubject() : null,
                assignment != null ? assignment.getFaculty() : null,
                lab,
                assignment != null ? assignment.getDivision() : null,
                assignment != null ? assignment.getBatch() : null,
                messages);
    }
}
