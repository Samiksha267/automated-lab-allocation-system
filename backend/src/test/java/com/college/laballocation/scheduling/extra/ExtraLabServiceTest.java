package com.college.laballocation.scheduling.extra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.CrAssignment;
import com.college.laballocation.academic.CrOwnershipService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionRepository;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ForbiddenDivisionAccessException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyAssignmentResolutionService;
import com.college.laballocation.faculty.FacultyService;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabService;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.CandidateAllocationFactory;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.ScheduleVersionStatus;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingContextFactory;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.alternative.AlternativeSuggestionService;
import com.college.laballocation.scheduling.constraint.ConstraintEngine;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabAllocationResponse;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabBookingRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabCancelRequest;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRole;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration-level tests for {@link ExtraLabService} - ownership
 * resolution, faculty resolution, book-time revalidation, persistence
 * correctness, and cancellation lifecycle. Deliberately does NOT re-test any
 * individual hard constraint (HC-01..HC-12 are already unit-tested in
 * {@code scheduling.constraint}) - {@link ConstraintEngine} is mocked here to
 * isolate exactly what this class is responsible for (PART 77 of the phase
 * brief).
 */
@ExtendWith(MockitoExtension.class)
class ExtraLabServiceTest {

    @Mock
    private CrOwnershipService crOwnershipService;

    @Mock
    private FacultyAssignmentResolutionService facultyAssignmentResolutionService;

    @Mock
    private AlternativeSuggestionService alternativeSuggestionService;

    @Mock
    private SchedulingContextFactory schedulingContextFactory;

    @Mock
    private CandidateAllocationFactory candidateAllocationFactory;

    @Mock
    private ConstraintEngine constraintEngine;

    @Mock
    private ScheduleVersionRepository scheduleVersionRepository;

    @Mock
    private AllocationRepository allocationRepository;

    @Mock
    private DivisionRepository divisionRepository;

    @Mock
    private BatchService batchService;

    @Mock
    private SubjectService subjectService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private LabService labService;

    @Mock
    private com.college.laballocation.audit.AuditLogService auditLogService;

    private ExtraLabService service;

    private Division division;
    private Batch batch;
    private Subject subject;
    private Faculty faculty;
    private Lab lab;
    private AcademicTerm term;
    private AppUser crUser;
    private CrAssignment assignment;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        service = new ExtraLabService(
                crOwnershipService, facultyAssignmentResolutionService, alternativeSuggestionService,
                schedulingContextFactory, candidateAllocationFactory, constraintEngine, scheduleVersionRepository,
                allocationRepository, divisionRepository, batchService, subjectService, facultyService, labService,
                auditLogService);

        Program program = new Program("EXTRA-PROG", "Extra Lab Test Program", 4);
        Stream stream = new Stream(program, "CS", "CS");
        AcademicYear year = new AcademicYear(stream, 3);
        division = new Division(year, "A", 60);
        setId(division, 1L);
        batch = new Batch(division, "A1", 30);
        setId(batch, 2L);
        subject = new Subject(year, "BDA", "Big Data Analytics");
        setId(subject, 3L);
        faculty = new Faculty("FAC-BDA", "Faculty BDA", null, null);
        setId(faculty, 4L);
        LabType labType = new LabType("DE", "Data Engineering", null);
        lab = new Lab("C-202", "Test Lab", 68, labType, "C", "2", "202");
        setId(lab, 5L);
        term = new AcademicTerm("2026-27", 5, "Semester 5", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        setId(term, 6L);
        crUser = new AppUser("cr@example.edu", "hash", UserRole.CR, "Test CR");
        setId(crUser, 7L);
        assignment = new CrAssignment(crUser, division, term, crUser);
    }

    private ExtraLabBookingRequest bookingRequest() {
        return new ExtraLabBookingRequest(
                subject.getId(), TargetType.BATCH, batch.getId(), LocalDate.of(2026, 8, 24),
                LocalTime.of(9, 0), LocalTime.of(11, 0), lab.getId());
    }

    private void stubOwnershipAndFaculty() {
        when(crOwnershipService.getCurrentAssignment(crUser.getId())).thenReturn(Optional.of(assignment));
        when(divisionRepository.lockById(division.getId())).thenReturn(Optional.of(division));
        SubjectFacultyAssignment sfa = new SubjectFacultyAssignment(subject, faculty, division, batch, term);
        when(facultyAssignmentResolutionService.resolveForBatch(subject.getId(), division.getId(), batch.getId(), term.getId()))
                .thenReturn(sfa);
    }

    // --- Ownership resolution ---

    @Test
    void bookThrowsCrAssignmentNotFoundWhenNoActiveAssignment() {
        when(crOwnershipService.getCurrentAssignment(crUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active CR assignment");
        verify(allocationRepository, never()).saveAndFlush(any());
    }

    // --- Faculty resolution ---

    @Test
    void batchTargetResolvesFacultyViaExactBatchAssignment() {
        stubOwnershipAndFaculty();
        stubNoPublishedVersion();

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest())).isInstanceOf(ApiException.class);

        verify(facultyAssignmentResolutionService).resolveForBatch(subject.getId(), division.getId(), batch.getId(), term.getId());
    }

    @Test
    void divisionTargetResolvesFacultyViaDivisionLevelAssignment() {
        when(crOwnershipService.getCurrentAssignment(crUser.getId())).thenReturn(Optional.of(assignment));
        when(divisionRepository.lockById(division.getId())).thenReturn(Optional.of(division));
        SubjectFacultyAssignment sfa = new SubjectFacultyAssignment(subject, faculty, division, null, term);
        when(facultyAssignmentResolutionService.resolveForDivision(subject.getId(), division.getId(), term.getId()))
                .thenReturn(sfa);
        stubNoPublishedVersion();

        ExtraLabBookingRequest divisionRequest = new ExtraLabBookingRequest(
                subject.getId(), TargetType.DIVISION, null, LocalDate.of(2026, 8, 24),
                LocalTime.of(9, 0), LocalTime.of(11, 0), lab.getId());

        assertThatThrownBy(() -> service.book(crUser.getId(), divisionRequest)).isInstanceOf(ApiException.class);

        verify(facultyAssignmentResolutionService).resolveForDivision(subject.getId(), division.getId(), term.getId());
        verify(facultyAssignmentResolutionService, never()).resolveForBatch(any(), any(), any(), any());
    }

    // --- Schedule version resolution ---

    private void stubNoPublishedVersion() {
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
    }

    @Test
    void bookThrowsNoPublishedScheduleWhenTermHasNoPublishedVersion() {
        stubOwnershipAndFaculty();
        stubNoPublishedVersion();

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No published schedule");
        verify(constraintEngine, never()).evaluate(any(), any());
        verify(allocationRepository, never()).saveAndFlush(any());
    }

    // --- Book-time revalidation ---

    @Test
    void bookRejectsWithAllocationConflictWhenSelectedLabIsNoLongerValid() {
        stubOwnershipAndFaculty();
        ScheduleVersion version = publishedVersion();
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));

        SchedulingContext context = fakeContext();
        when(schedulingContextFactory.build(any(SchedulingRequest.class))).thenReturn(context);
        CandidateAllocation candidate = new CandidateAllocation(context, null);
        when(candidateAllocationFactory.build(context, lab.getId())).thenReturn(candidate);

        ConstraintViolation violation = new ConstraintViolation("LAB_CONFLICT", "Lab is occupied.", "LAB", "C-202");
        ConstraintResult failResult = ConstraintResult.fail(HardConstraintId.HC_01_LAB_CONFLICT, violation);
        ConstraintEvaluation invalid = ConstraintEvaluation.of(List.of(failResult));
        when(constraintEngine.evaluate(context, candidate)).thenReturn(invalid);

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("ALLOCATION_CONFLICT");
                    assertThat(apiEx.getDetails()).containsKey("violations");
                });
        verify(allocationRepository, never()).saveAndFlush(any());
    }

    // --- Successful booking ---

    @Test
    void bookPersistsExtraAllocationPublishedAgainstCurrentVersionWithCorrectCreatedBy() {
        stubOwnershipAndFaculty();
        ScheduleVersion version = publishedVersion();
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));

        SchedulingContext context = fakeContext();
        when(schedulingContextFactory.build(any(SchedulingRequest.class))).thenReturn(context);
        CandidateAllocation candidate = new CandidateAllocation(context, null);
        when(candidateAllocationFactory.build(context, lab.getId())).thenReturn(candidate);
        when(constraintEngine.evaluate(context, candidate)).thenReturn(ConstraintEvaluation.of(List.of()));

        when(subjectService.getEntity(subject.getId())).thenReturn(subject);
        when(facultyService.getEntity(faculty.getId())).thenReturn(faculty);
        when(labService.getEntity(lab.getId())).thenReturn(lab);
        when(batchService.getEntity(batch.getId())).thenReturn(batch);
        when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> {
            Allocation saved = inv.getArgument(0);
            setId(saved, 100L);
            return saved;
        });

        ExtraLabAllocationResponse response = service.book(crUser.getId(), bookingRequest());

        ArgumentCaptor<Allocation> captor = ArgumentCaptor.forClass(Allocation.class);
        verify(allocationRepository).saveAndFlush(captor.capture());
        Allocation saved = captor.getValue();

        assertThat(saved.getAllocationType()).isEqualTo(AllocationType.EXTRA);
        assertThat(saved.getStatus()).isEqualTo(AllocationStatus.PUBLISHED);
        assertThat(saved.getTargetType()).isEqualTo(TargetType.BATCH);
        assertThat(saved.getDivision().getId()).isEqualTo(division.getId());
        assertThat(saved.getBatch().getId()).isEqualTo(batch.getId());
        assertThat(saved.getCreatedBy().getId()).isEqualTo(crUser.getId());
        assertThat(saved.getScheduleVersion()).isSameAs(version);
        assertThat(response.allocationType()).isEqualTo("EXTRA");
        assertThat(response.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void bookAcquiresDivisionLockBeforeConstraintRevalidation() {
        stubOwnershipAndFaculty();
        ScheduleVersion version = publishedVersion();
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        SchedulingContext context = fakeContext();
        when(schedulingContextFactory.build(any(SchedulingRequest.class))).thenReturn(context);
        CandidateAllocation candidate = new CandidateAllocation(context, null);
        when(candidateAllocationFactory.build(context, lab.getId())).thenReturn(candidate);
        when(constraintEngine.evaluate(context, candidate)).thenReturn(ConstraintEvaluation.of(List.of()));
        when(subjectService.getEntity(subject.getId())).thenReturn(subject);
        when(facultyService.getEntity(faculty.getId())).thenReturn(faculty);
        when(labService.getEntity(lab.getId())).thenReturn(lab);
        when(batchService.getEntity(batch.getId())).thenReturn(batch);
        when(allocationRepository.saveAndFlush(any(Allocation.class))).thenAnswer(inv -> {
            Allocation saved = inv.getArgument(0);
            setId(saved, 100L);
            return saved;
        });

        service.book(crUser.getId(), bookingRequest());

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(divisionRepository, constraintEngine, allocationRepository);
        inOrder.verify(divisionRepository).lockById(division.getId());
        inOrder.verify(constraintEngine).evaluate(context, candidate);
        inOrder.verify(allocationRepository).saveAndFlush(any(Allocation.class));
    }

    @Test
    void bookRejectsWithAllocationConflictWhenExclusionConstraintRejectsTheInsert() {
        stubOwnershipAndFaculty();
        ScheduleVersion version = publishedVersion();
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        SchedulingContext context = fakeContext();
        when(schedulingContextFactory.build(any(SchedulingRequest.class))).thenReturn(context);
        CandidateAllocation candidate = new CandidateAllocation(context, null);
        when(candidateAllocationFactory.build(context, lab.getId())).thenReturn(candidate);
        when(constraintEngine.evaluate(context, candidate)).thenReturn(ConstraintEvaluation.of(List.of()));
        when(subjectService.getEntity(subject.getId())).thenReturn(subject);
        when(facultyService.getEntity(faculty.getId())).thenReturn(faculty);
        when(labService.getEntity(lab.getId())).thenReturn(lab);
        when(batchService.getEntity(batch.getId())).thenReturn(batch);

        org.hibernate.exception.ConstraintViolationException hibernateEx = new org.hibernate.exception.ConstraintViolationException(
                "duplicate key", null, "ex_allocation_lab_overlap");
        when(allocationRepository.saveAndFlush(any(Allocation.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("insert failed", hibernateEx));

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("ALLOCATION_CONFLICT");
                    assertThat(apiEx.getStatus().value()).isEqualTo(409);
                    assertThat(apiEx.getDetails()).containsEntry("reason", "CONCURRENT_ALLOCATION_CONFLICT");
                    assertThat(apiEx.getDetails()).containsEntry("conflictingResource", "lab");
                });
    }

    /**
     * Regression test for a real bug found live in Docker (Phase 16): two
     * genuinely simultaneous INSERTs whose new rows mutually overlap can make
     * PostgreSQL's exclusion-constraint check deadlock rather than cleanly
     * reject the second insert - surfaced as {@code CannotAcquireLockException}
     * (a {@link org.springframework.dao.ConcurrencyFailureException}), a
     * completely different Spring DAO branch than {@code DataIntegrityViolationException}.
     * Both must produce the identical clean 409, never a 500.
     */
    @Test
    void bookMapsADeadlockDuringExclusionCheckToTheSameCleanConflictResponse() {
        stubOwnershipAndFaculty();
        ScheduleVersion version = publishedVersion();
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        SchedulingContext context = fakeContext();
        when(schedulingContextFactory.build(any(SchedulingRequest.class))).thenReturn(context);
        CandidateAllocation candidate = new CandidateAllocation(context, null);
        when(candidateAllocationFactory.build(context, lab.getId())).thenReturn(candidate);
        when(constraintEngine.evaluate(context, candidate)).thenReturn(ConstraintEvaluation.of(List.of()));
        when(subjectService.getEntity(subject.getId())).thenReturn(subject);
        when(facultyService.getEntity(faculty.getId())).thenReturn(faculty);
        when(labService.getEntity(lab.getId())).thenReturn(lab);
        when(batchService.getEntity(batch.getId())).thenReturn(batch);

        when(allocationRepository.saveAndFlush(any(Allocation.class)))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("deadlock detected"));

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getCode()).isEqualTo("ALLOCATION_CONFLICT");
                    assertThat(apiEx.getStatus().value()).isEqualTo(409);
                    assertThat(apiEx.getDetails()).containsEntry("reason", "CONCURRENT_ALLOCATION_CONFLICT");
                });
    }

    /**
     * Regression test for a real bug found live in Docker (Phase 16):
     * Hibernate's own {@code ConstraintViolationException.getConstraintName()}
     * reliably returns {@code null} for PostgreSQL EXCLUDE-constraint
     * violations (its extractor recognizes "duplicate key value violates
     * unique constraint" but not "conflicting key value violates exclusion
     * constraint") - the PostgreSQL-native {@code ServerErrorMessage.getConstraint()}
     * fallback must still resolve the correct resource.
     */
    @Test
    void extractConstraintNameFallsBackToPostgresServerErrorMessage() {
        stubOwnershipAndFaculty();
        ScheduleVersion version = publishedVersion();
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(term.getId(), ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        SchedulingContext context = fakeContext();
        when(schedulingContextFactory.build(any(SchedulingRequest.class))).thenReturn(context);
        CandidateAllocation candidate = new CandidateAllocation(context, null);
        when(candidateAllocationFactory.build(context, lab.getId())).thenReturn(candidate);
        when(constraintEngine.evaluate(context, candidate)).thenReturn(ConstraintEvaluation.of(List.of()));
        when(subjectService.getEntity(subject.getId())).thenReturn(subject);
        when(facultyService.getEntity(faculty.getId())).thenReturn(faculty);
        when(labService.getEntity(lab.getId())).thenReturn(lab);
        when(batchService.getEntity(batch.getId())).thenReturn(batch);

        org.postgresql.util.ServerErrorMessage serverError = new org.postgresql.util.ServerErrorMessage(
                "SERROR\0C23P01\0Mconflicting key value violates exclusion constraint \"ex_allocation_faculty_overlap\"\0"
                        + "nex_allocation_faculty_overlap\0");
        org.postgresql.util.PSQLException psqlEx = new org.postgresql.util.PSQLException(serverError);
        // Hibernate's own extractor finds no constraint name (constructor arg null) - only the PSQLException fallback can resolve it.
        org.hibernate.exception.ConstraintViolationException hibernateEx =
                new org.hibernate.exception.ConstraintViolationException("could not execute statement", psqlEx, null);
        when(allocationRepository.saveAndFlush(any(Allocation.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("insert failed", hibernateEx));

        assertThatThrownBy(() -> service.book(crUser.getId(), bookingRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getDetails()).containsEntry("conflictingResource", "faculty"));
    }

    // --- Cancellation ---

    @Test
    void cancelRejectsRegularAllocationType() {
        Allocation regular = Allocation.forBatch(
                AllocationType.REGULAR, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, publishedVersion(), crUser);
        when(allocationRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> service.cancel(crUser.getId(), 99L, new ExtraLabCancelRequest("no longer needed")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("EXTRA_ALLOCATION_FORBIDDEN"));
        verify(crOwnershipService, never()).requireOwnsDivision(anyLong(), anyLong());
    }

    @Test
    void cancelRejectsWhenCallerDoesNotOwnAllocationsDivision() {
        Allocation extra = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, publishedVersion(), crUser);
        when(allocationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(extra));
        when(crOwnershipService.requireOwnsDivision(crUser.getId(), division.getId()))
                .thenThrow(new ForbiddenDivisionAccessException("You are not assigned to division A."));

        assertThatThrownBy(() -> service.cancel(crUser.getId(), 42L, null))
                .isInstanceOf(ForbiddenDivisionAccessException.class);
        assertThat(extra.getStatus()).isEqualTo(AllocationStatus.PUBLISHED);
    }

    @Test
    void cancelSetsStatusAndAuditFieldsAndNormalizesBlankReasonToNull() {
        Allocation extra = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, publishedVersion(), crUser);
        setId(extra, 42L);
        when(allocationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(extra));
        when(crOwnershipService.requireOwnsDivision(crUser.getId(), division.getId())).thenReturn(assignment);

        ExtraLabAllocationResponse response = service.cancel(crUser.getId(), 42L, new ExtraLabCancelRequest("   "));

        assertThat(extra.getStatus()).isEqualTo(AllocationStatus.CANCELLED);
        assertThat(extra.getCancelledBy()).isEqualTo(crUser);
        assertThat(extra.getCancelledAt()).isNotNull();
        assertThat(extra.getCancellationReason()).isNull();
        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelAgainOnAlreadyCancelledAllocationIsRejected() {
        Allocation extra = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, publishedVersion(), crUser);
        extra.cancel(crUser, "first cancel");
        when(allocationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(extra));
        when(crOwnershipService.requireOwnsDivision(crUser.getId(), division.getId())).thenReturn(assignment);

        assertThatThrownBy(() -> service.cancel(crUser.getId(), 42L, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_ALLOCATION_TRANSITION"));
    }

    /** Phase 18, PART 10/23/67 - an EXTRA allocation whose schedule version has since been superseded by a republish is permanent history and must not be cancelled. */
    @Test
    void cancelRejectsWhenTheAllocationsScheduleVersionIsNoLongerCurrent() {
        ScheduleVersion superseded = new ScheduleVersion(term, 1, null, crUser);
        setId(superseded, 10L);
        superseded.publish(crUser);
        superseded.supersede();
        Allocation extra = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, superseded, crUser);
        when(allocationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(extra));

        assertThatThrownBy(() -> service.cancel(crUser.getId(), 42L, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("SCHEDULE_VERSION_NOT_CURRENT"));
        verify(crOwnershipService, never()).requireOwnsDivision(anyLong(), anyLong());
    }

    // --- mine() / activity() delegation ---

    @Test
    void mineDerivesDivisionFromServerSideAssignmentNeverFromAParameter() {
        when(crOwnershipService.getCurrentAssignment(crUser.getId())).thenReturn(Optional.of(assignment));
        when(allocationRepository.findByDivisionIdAndAllocationTypeOrderByCreatedAtDesc(division.getId(), AllocationType.EXTRA))
                .thenReturn(List.of());

        List<ExtraLabAllocationResponse> result = service.mine(crUser.getId());

        assertThat(result).isEmpty();
        verify(allocationRepository).findByDivisionIdAndAllocationTypeOrderByCreatedAtDesc(division.getId(), AllocationType.EXTRA);
    }

    @Test
    void activityFiltersByOptionalDivisionAndStatus() {
        Allocation matching = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, publishedVersion(), crUser);
        Division otherDivision = new Division(division.getAcademicYear(), "B", 60);
        setId(otherDivision, 8L);
        Allocation otherDivisionAllocation = Allocation.forDivision(
                AllocationType.EXTRA, otherDivision, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.PUBLISHED, publishedVersion(), crUser);
        when(allocationRepository.findByAllocationTypeAndScheduleVersion_AcademicTerm_IdOrderByCreatedAtDesc(
                        AllocationType.EXTRA, term.getId()))
                .thenReturn(List.of(matching, otherDivisionAllocation));

        List<ExtraLabAllocationResponse> filtered = service.activity(term.getId(), division.getId(), null);

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).divisionId()).isEqualTo(division.getId());
    }

    // --- helpers ---

    private ScheduleVersion publishedVersion() {
        ScheduleVersion version = new ScheduleVersion(term, 1, null, crUser);
        setId(version, 10L);
        version.publish(crUser);
        return version;
    }

    private SchedulingContext fakeContext() {
        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(),
                term.getId(), LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                new com.college.laballocation.scheduling.SchedulingActor(crUser.getId(), UserRole.CR));
        return new SchedulingContext(
                request,
                new SubjectRef(subject.getId(), subject.getCode(), subject.getName(), 100L, null, null),
                new FacultyRef(faculty.getId(), faculty.getEmployeeCode(), faculty.getName(), true),
                new DivisionRef(division.getId(), division.getCode(), division.getStrength(), 100L),
                new BatchRef(batch.getId(), batch.getCode(), batch.getStrength(), division.getId()),
                List.of(), List.of(), List.of());
    }
}
