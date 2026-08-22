package com.college.laballocation.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.subject.Subject;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Proves the resolution order documented on {@link FacultyAssignmentResolutionService}. */
@ExtendWith(MockitoExtension.class)
class FacultyAssignmentResolutionServiceTest {

    @Mock
    private SubjectFacultyAssignmentRepository repository;

    private FacultyAssignmentResolutionService service;

    private final Subject subject = null; // entity identity not needed - repository is mocked by id
    private static final Long SUBJECT_ID = 1L;
    private static final Long DIVISION_ID = 2L;
    private static final Long BATCH_ID = 3L;
    private static final Long TERM_ID = 4L;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new FacultyAssignmentResolutionService(repository);
    }

    private SubjectFacultyAssignment fakeAssignment(String facultyName) {
        Faculty faculty = new Faculty("EMP-" + facultyName, facultyName, null, null);
        return new SubjectFacultyAssignment(subject, faculty, (Division) null, null, (AcademicTerm) null);
    }

    @Test
    void exactBatchAssignmentIsReturnedWhenItExists() {
        SubjectFacultyAssignment batchLevel = fakeAssignment("Batch Faculty");
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.of(batchLevel));

        SubjectFacultyAssignment result = service.resolveForBatch(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID);

        assertThat(result).isSameAs(batchLevel);
    }

    @Test
    void divisionLevelFallbackIsUsedWhenNoBatchAssignmentExists() {
        SubjectFacultyAssignment divisionLevel = fakeAssignment("Division Faculty");
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.empty());
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdIsNullAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, TERM_ID))
                .thenReturn(Optional.of(divisionLevel));

        SubjectFacultyAssignment result = service.resolveForBatch(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID);

        assertThat(result).isSameAs(divisionLevel);
    }

    @Test
    void exactBatchAssignmentWinsOverDivisionFallbackWhenBothExist() {
        SubjectFacultyAssignment batchLevel = fakeAssignment("Batch Faculty");
        SubjectFacultyAssignment divisionLevel = fakeAssignment("Division Faculty");
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.of(batchLevel));
        // Division-level fallback is intentionally NOT stubbed to return anything - it must
        // never even be consulted once the exact batch-level match is found.

        SubjectFacultyAssignment result = service.resolveForBatch(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID);

        assertThat(result).isSameAs(batchLevel).isNotSameAs(divisionLevel);
    }

    @Test
    void noAssignmentAtAllIsReportedAsNotFoundNotSilentlyGuessed() {
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .thenReturn(Optional.empty());
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdIsNullAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, TERM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveForBatch(SUBJECT_ID, DIVISION_ID, BATCH_ID, TERM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void divisionScopedResolutionNeverFallsBackToABatchAssignment() {
        when(repository.findBySubjectIdAndDivisionIdAndBatchIdIsNullAndAcademicTermIdAndActiveTrue(
                        SUBJECT_ID, DIVISION_ID, TERM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveForDivision(SUBJECT_ID, DIVISION_ID, TERM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
