package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleVersionServiceTest {

    @Mock
    private ScheduleVersionRepository scheduleVersionRepository;

    @Mock
    private AcademicTermService academicTermService;

    @Mock
    private AcademicTermRepository academicTermRepository;

    @Mock
    private AllocationRepository allocationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private ScheduleVersionService service;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private AcademicTerm term() {
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5 (2026-27)", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 12, 15));
        term.updateStatus(TermStatus.ACTIVE);
        setId(term, 10L);
        return term;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser("lab.assistant@example.edu", "hash", UserRole.LAB_ASSISTANT, "Lab Assistant");
        setId(user, id);
        return user;
    }

    @BeforeEach
    void setUp() {
        service = new ScheduleVersionService(
                scheduleVersionRepository, academicTermService, academicTermRepository, allocationRepository, userRepository, auditLogService);
    }

    @Test
    void firstVersionForATermNeedsNoReason() {
        when(academicTermService.getEntity(10L)).thenReturn(term());
        when(scheduleVersionRepository.countByAcademicTermId(10L)).thenReturn(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.save(any(ScheduleVersion.class))).thenAnswer(invocation -> {
            ScheduleVersion saved = invocation.getArgument(0);
            setId(saved, 999L);
            return saved;
        });

        assertThatCode(() -> service.createDraft(10L, null, 1L)).doesNotThrowAnyException();
    }

    @Test
    void secondVersionForATermRequiresAReason() {
        when(academicTermService.getEntity(10L)).thenReturn(term());
        when(scheduleVersionRepository.countByAcademicTermId(10L)).thenReturn(1);

        assertThatThrownBy(() -> service.createDraft(10L, null, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void secondVersionWithAReasonSucceeds() {
        when(academicTermService.getEntity(10L)).thenReturn(term());
        when(scheduleVersionRepository.countByAcademicTermId(10L)).thenReturn(1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.save(any(ScheduleVersion.class))).thenAnswer(invocation -> {
            ScheduleVersion saved = invocation.getArgument(0);
            setId(saved, 999L);
            return saved;
        });

        assertThatCode(() -> service.createDraft(10L, "Timetable correction after room reassignment", 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void publishingSupersedesThePreviouslyPublishedVersion() {
        ScheduleVersion previouslyPublished = new ScheduleVersion(term(), 1, null, user(1L));
        previouslyPublished.publish(user(1L));
        setId(previouslyPublished, 100L);

        ScheduleVersion draft = new ScheduleVersion(term(), 2, "revision", user(1L));
        setId(draft, 101L);

        when(scheduleVersionRepository.findAcademicTermIdById(101L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(101L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(previouslyPublished));
        when(allocationRepository.findByScheduleVersionIdAndStatus(101L, AllocationStatus.APPROVED)).thenReturn(List.of());

        service.publish(101L, 2L);

        assertThat(previouslyPublished.getStatus()).isEqualTo(ScheduleVersionStatus.SUPERSEDED);
        assertThat(draft.getStatus()).isEqualTo(ScheduleVersionStatus.PUBLISHED);
    }

    @Test
    void publishingWithNoExistingPublishedVersionSucceeds() {
        ScheduleVersion draft = new ScheduleVersion(term(), 1, null, user(1L));
        setId(draft, 100L);

        when(scheduleVersionRepository.findAcademicTermIdById(100L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(allocationRepository.findByScheduleVersionIdAndStatus(100L, AllocationStatus.APPROVED)).thenReturn(List.of());

        service.publish(100L, 1L);

        assertThat(draft.getStatus()).isEqualTo(ScheduleVersionStatus.PUBLISHED);
    }

    @Test
    void listByTermDelegatesToRepository() {
        when(scheduleVersionRepository.findByAcademicTermIdOrderByVersionNumberAsc(10L)).thenReturn(List.of());

        assertThat(service.listByTerm(10L)).isEmpty();
    }

    /** PART 6/51 - the term lock must be acquired before the racy count read, never after. */
    @Test
    void createDraftAcquiresTheTermLockBeforeComputingTheVersionNumber() {
        when(academicTermService.getEntity(10L)).thenReturn(term());
        when(scheduleVersionRepository.countByAcademicTermId(10L)).thenReturn(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.save(any(ScheduleVersion.class))).thenAnswer(invocation -> {
            ScheduleVersion saved = invocation.getArgument(0);
            setId(saved, 999L);
            return saved;
        });

        service.createDraft(10L, null, 1L);

        InOrder order = inOrder(academicTermRepository, scheduleVersionRepository);
        order.verify(academicTermRepository).lockById(10L);
        order.verify(scheduleVersionRepository).countByAcademicTermId(10L);
    }

    /** PART 14 - the term lock must be acquired before the version is loaded and before the "who is currently published" read, never after. */
    @Test
    void publishAcquiresTheTermLockBeforeLoadingTheVersionOrCheckingPublicationState() {
        ScheduleVersion draft = new ScheduleVersion(term(), 1, null, user(1L));
        setId(draft, 100L);

        when(scheduleVersionRepository.findAcademicTermIdById(100L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(allocationRepository.findByScheduleVersionIdAndStatus(100L, AllocationStatus.APPROVED)).thenReturn(List.of());

        service.publish(100L, 1L);

        InOrder order = inOrder(scheduleVersionRepository, academicTermRepository);
        order.verify(scheduleVersionRepository).findAcademicTermIdById(100L);
        order.verify(academicTermRepository).lockById(10L);
        order.verify(scheduleVersionRepository).findById(100L);
        order.verify(scheduleVersionRepository).findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED);
    }

    /** PART 4 - SUPERSEDED -> PUBLISHED is never a valid transition; the domain guard on {@link ScheduleVersion#publish} must reject it even when reached through the service. */
    @Test
    void publishingAnAlreadySupersededVersionIsRejected() {
        ScheduleVersion version = new ScheduleVersion(term(), 1, null, user(1L));
        version.publish(user(1L));
        version.supersede();
        setId(version, 100L);

        when(scheduleVersionRepository.findAcademicTermIdById(100L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.publish(100L, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SCHEDULE_VERSION_TRANSITION");
    }

    /** PART 4/48 - republishing an already-PUBLISHED version (a no-op double-publish attempt) is rejected the same way. */
    @Test
    void publishingAnAlreadyPublishedVersionIsRejected() {
        ScheduleVersion version = new ScheduleVersion(term(), 1, null, user(1L));
        version.publish(user(1L));
        setId(version, 100L);

        when(scheduleVersionRepository.findAcademicTermIdById(100L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.publish(100L, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SCHEDULE_VERSION_TRANSITION");
        // The up-front status guard (ADR-089) must reject before ever looking up "the term's
        // currently published version" - proving the self-supersede bug found live cannot recur.
        verify(scheduleVersionRepository, never()).findByAcademicTermIdAndStatus(any(), any());
    }

    /** PART 15 - every APPROVED allocation already attached to the version being published transitions to PUBLISHED in the same call; CANCELLED/other-version rows are never touched here (they're simply not in the returned list). */
    @Test
    void publishingTransitionsApprovedAllocationsOfThatVersionToPublished() {
        ScheduleVersion draft = new ScheduleVersion(term(), 1, null, user(1L));
        setId(draft, 100L);

        Program program = new Program("PROG", "Program", 4);
        Stream stream = new Stream(program, "CS", "CS");
        AcademicYear year = new AcademicYear(stream, 3);
        Division division = new Division(year, "A", 60);
        Subject subject = new Subject(year, "SUB", "Subject");
        Faculty faculty = new Faculty("FAC", "Faculty", null, null);
        LabType labType = new LabType("TYPE", "Type", null);
        Lab lab = new Lab("LAB-1", "Lab", 30, labType, "A", "1", "1");
        Allocation approved = Allocation.forDivision(
                AllocationType.REGULAR, division, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, draft, user(1L));

        when(scheduleVersionRepository.findAcademicTermIdById(100L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(allocationRepository.findByScheduleVersionIdAndStatus(100L, AllocationStatus.APPROVED)).thenReturn(List.of(approved));

        service.publish(100L, 1L);

        assertThat(approved.getStatus()).isEqualTo(AllocationStatus.PUBLISHED);
    }

    /** PART 30/31/52 - publication writes exactly one SCHEDULE_PUBLISHED event when there is nothing to supersede, and additionally one SCHEDULE_SUPERSEDED event when there is. */
    @Test
    void publishingWithNoExistingPublishedVersionWritesExactlyOneAuditEvent() {
        ScheduleVersion draft = new ScheduleVersion(term(), 1, null, user(1L));
        setId(draft, 100L);

        when(scheduleVersionRepository.findAcademicTermIdById(100L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(allocationRepository.findByScheduleVersionIdAndStatus(100L, AllocationStatus.APPROVED)).thenReturn(List.of());

        service.publish(100L, 1L);

        verify(auditLogService, times(1)).record(any(AuditEvent.class));
        verify(auditLogService).record(argThatActionIs(AuditAction.SCHEDULE_PUBLISHED));
    }

    @Test
    void publishingWithAnExistingPublishedVersionWritesBothSupersededAndPublishedEvents() {
        ScheduleVersion previouslyPublished = new ScheduleVersion(term(), 1, null, user(1L));
        previouslyPublished.publish(user(1L));
        setId(previouslyPublished, 100L);
        ScheduleVersion draft = new ScheduleVersion(term(), 2, "revision", user(1L));
        setId(draft, 101L);

        when(scheduleVersionRepository.findAcademicTermIdById(101L)).thenReturn(Optional.of(10L));
        when(scheduleVersionRepository.findById(101L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(previouslyPublished));
        when(allocationRepository.findByScheduleVersionIdAndStatus(101L, AllocationStatus.APPROVED)).thenReturn(List.of());

        service.publish(101L, 2L);

        verify(auditLogService, times(2)).record(any(AuditEvent.class));
        verify(auditLogService).record(argThatActionIs(AuditAction.SCHEDULE_SUPERSEDED));
        verify(auditLogService).record(argThatActionIs(AuditAction.SCHEDULE_PUBLISHED));
    }

    private static AuditEvent argThatActionIs(AuditAction action) {
        return org.mockito.ArgumentMatchers.argThat(event -> event != null && event.action() == action);
    }
}
