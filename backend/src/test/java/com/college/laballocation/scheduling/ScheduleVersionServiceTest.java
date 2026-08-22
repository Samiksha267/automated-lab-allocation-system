package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleVersionServiceTest {

    @Mock
    private ScheduleVersionRepository scheduleVersionRepository;

    @Mock
    private AcademicTermService academicTermService;

    @Mock
    private UserRepository userRepository;

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
        service = new ScheduleVersionService(scheduleVersionRepository, academicTermService, userRepository);
    }

    @Test
    void firstVersionForATermNeedsNoReason() {
        when(academicTermService.getEntity(10L)).thenReturn(term());
        when(scheduleVersionRepository.countByAcademicTermId(10L)).thenReturn(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.save(any(ScheduleVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(scheduleVersionRepository.save(any(ScheduleVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

        when(scheduleVersionRepository.findById(101L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(previouslyPublished));

        service.publish(101L, 2L);

        assertThat(previouslyPublished.getStatus()).isEqualTo(ScheduleVersionStatus.SUPERSEDED);
        assertThat(draft.getStatus()).isEqualTo(ScheduleVersionStatus.PUBLISHED);
    }

    @Test
    void publishingWithNoExistingPublishedVersionSucceeds() {
        ScheduleVersion draft = new ScheduleVersion(term(), 1, null, user(1L));
        setId(draft, 100L);

        when(scheduleVersionRepository.findById(100L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(scheduleVersionRepository.findByAcademicTermIdAndStatus(10L, ScheduleVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        service.publish(100L, 1L);

        assertThat(draft.getStatus()).isEqualTo(ScheduleVersionStatus.PUBLISHED);
    }

    @Test
    void listByTermDelegatesToRepository() {
        when(scheduleVersionRepository.findByAcademicTermIdOrderByVersionNumberAsc(10L)).thenReturn(List.of());

        assertThat(service.listByTerm(10L)).isEmpty();
    }
}
