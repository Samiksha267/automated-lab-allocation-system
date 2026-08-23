package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.ACADEMIC_YEAR_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.BATCH_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.DIVISION_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.FACULTY_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.SUBJECT_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.TERM_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyAssignmentResolutionService;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.subject.Subject;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcademicRelationshipConstraintTest {

    @Mock
    private FacultyAssignmentResolutionService facultyAssignmentResolutionService;

    private AcademicRelationshipConstraint constraint;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private SchedulingContext contextWith(Long batchDivisionId, Long subjectAcademicYearId, Long divisionAcademicYearId) {
        var request = batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0));
        return new SchedulingContext(
                request,
                new SubjectRef(SUBJECT_ID, "BDA", "Big Data Analytics", subjectAcademicYearId, null),
                new FacultyRef(FACULTY_ID, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(DIVISION_ID, "A", 68, divisionAcademicYearId),
                new BatchRef(BATCH_ID, "A1", 23, batchDivisionId),
                List.of(),
                List.of(),
                List.of());
    }

    private SubjectFacultyAssignment assignment(Long facultyId) {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Subject subject = new Subject(year, "BDA", "Big Data Analytics");
        Division division = new Division(year, "A", 68);
        Batch batch = new Batch(division, "A1", 23);
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 1));
        term.updateStatus(TermStatus.ACTIVE);
        Faculty faculty = new Faculty("FAC-X", "Some Faculty", null, null);
        setId(faculty, facultyId);
        return new SubjectFacultyAssignment(subject, faculty, division, batch, term);
    }

    @Test
    void idIsHc12() {
        constraint = new AcademicRelationshipConstraint(facultyAssignmentResolutionService);
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_12_ACADEMIC_RELATIONSHIP);
    }

    @Test
    void failsWhenBatchDoesNotBelongToDivision() {
        constraint = new AcademicRelationshipConstraint(facultyAssignmentResolutionService);
        SchedulingContext context = contextWith(999L, ACADEMIC_YEAR_ID, ACADEMIC_YEAR_ID);

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("INVALID_ACADEMIC_RELATIONSHIP");
    }

    @Test
    void failsWhenSubjectAcademicYearDoesNotMatchDivisionAcademicYear() {
        constraint = new AcademicRelationshipConstraint(facultyAssignmentResolutionService);
        SchedulingContext context = contextWith(DIVISION_ID, 111L, 222L);

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("INVALID_ACADEMIC_RELATIONSHIP");
    }

    @Test
    void failsWhenNoAuthoritativeAssignmentResolves() {
        when(facultyAssignmentResolutionService.resolveForBatchIfPresent(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.empty());
        constraint = new AcademicRelationshipConstraint(facultyAssignmentResolutionService);
        SchedulingContext context = contextWith(DIVISION_ID, ACADEMIC_YEAR_ID, ACADEMIC_YEAR_ID);

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
    }

    @Test
    void failsWhenResolvedFacultyDoesNotMatchRequestedFaculty() {
        when(facultyAssignmentResolutionService.resolveForBatchIfPresent(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.of(assignment(999L)));
        constraint = new AcademicRelationshipConstraint(facultyAssignmentResolutionService);
        SchedulingContext context = contextWith(DIVISION_ID, ACADEMIC_YEAR_ID, ACADEMIC_YEAR_ID);

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().details()).containsEntry("requestedFacultyId", FACULTY_ID).containsEntry("assignedFacultyId", 999L);
    }

    @Test
    void passesWhenEverythingIsCoherent() {
        when(facultyAssignmentResolutionService.resolveForBatchIfPresent(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.of(assignment(FACULTY_ID)));
        constraint = new AcademicRelationshipConstraint(facultyAssignmentResolutionService);
        SchedulingContext context = contextWith(DIVISION_ID, ACADEMIC_YEAR_ID, ACADEMIC_YEAR_ID);

        ConstraintResult result = constraint.evaluate(context, candidate(context));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
