package com.college.laballocation.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.CreateFacultyAvailabilityRequest;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacultyAvailabilityServiceTest {

    @Mock
    private FacultyAvailabilityRepository availabilityRepository;

    @Mock
    private FacultyService facultyService;

    @Mock
    private AcademicTermService academicTermService;

    private FacultyAvailabilityService service;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Faculty activeFaculty() {
        Faculty faculty = new Faculty("FAC-BDA", "Faculty BDA", "faculty.bda@example.edu", "Computer Science");
        setId(faculty, 1L);
        return faculty;
    }

    private AcademicTerm activeTerm() {
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5 (2026-27)", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 12, 15));
        term.updateStatus(TermStatus.ACTIVE);
        setId(term, 10L);
        return term;
    }

    private static long nextWindowId = 5000L;

    private FacultyAvailability window(Faculty faculty, AcademicTerm term, DayOfWeek day, int startHour, int endHour) {
        FacultyAvailability availability =
                new FacultyAvailability(faculty, term, day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
        setId(availability, nextWindowId++);
        return availability;
    }

    @BeforeEach
    void setUp() {
        service = new FacultyAvailabilityService(availabilityRepository, facultyService, academicTermService);
    }

    @Test
    void availableWithinOneIntervalIsTrue() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        lenient().when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(faculty, term, DayOfWeek.MONDAY, 9, 12)));

        assertThat(service.isAvailable(1L, 10L, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isTrue();
    }

    @Test
    void outsideAllIntervalsIsFalse() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(faculty, term, DayOfWeek.MONDAY, 9, 12), window(faculty, term, DayOfWeek.MONDAY, 14, 16)));

        assertThat(service.isAvailable(1L, 10L, DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0)))
                .isFalse();
    }

    @Test
    void multipleWindowsFindsCorrectOne() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(faculty, term, DayOfWeek.MONDAY, 9, 11), window(faculty, term, DayOfWeek.MONDAY, 14, 16)));

        assertThat(service.isAvailable(1L, 10L, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0)))
                .isTrue();
    }

    @Test
    void adjacentIntervalsAreTreatedAsContinuousForEvaluation() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(faculty, term, DayOfWeek.MONDAY, 9, 11), window(faculty, term, DayOfWeek.MONDAY, 11, 13)));

        assertThat(service.isAvailable(1L, 10L, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)))
                .isTrue();
    }

    @Test
    void inactiveFacultyIsNeverAvailable() {
        Faculty faculty = new Faculty("FAC-OLD", "Retired Faculty", null, null);
        faculty.update("Retired Faculty", null, null, false);
        setId(faculty, 2L);
        when(facultyService.getEntity(2L)).thenReturn(faculty);

        assertThat(service.isAvailable(2L, 10L, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isFalse();
    }

    @Test
    void wrongTermReturnsUnavailable() {
        Faculty faculty = activeFaculty();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 99L, DayOfWeek.MONDAY))
                .thenReturn(List.of());

        assertThat(service.isAvailable(1L, 99L, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isFalse();
    }

    @Test
    void noRecordsMeansUnavailableNotAvailableAllDay() {
        Faculty faculty = activeFaculty();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.SUNDAY))
                .thenReturn(List.of());

        assertThat(service.isAvailable(1L, 10L, DayOfWeek.SUNDAY, LocalTime.of(0, 0), LocalTime.of(23, 0)))
                .isFalse();
    }

    @Test
    void createValidIntervalSucceeds() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(academicTermService.getEntity(10L)).thenReturn(term);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of());
        when(availabilityRepository.save(any(FacultyAvailability.class)))
                .thenAnswer(invocation -> {
                    FacultyAvailability saved = invocation.getArgument(0);
                    setId(saved, 100L);
                    return saved;
                });

        assertThatCode(() -> service.create(
                        1L, new CreateFacultyAvailabilityRequest(10L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))))
                .doesNotThrowAnyException();
    }

    @Test
    void createWithStartEqualToEndIsRejected() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(academicTermService.getEntity(10L)).thenReturn(term);

        assertThatThrownBy(() -> service.create(
                        1L, new CreateFacultyAvailabilityRequest(10L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 0))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_AVAILABILITY_INTERVAL");
    }

    @Test
    void createOverlappingExistingIntervalIsRejected() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(academicTermService.getEntity(10L)).thenReturn(term);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(faculty, term, DayOfWeek.MONDAY, 9, 12)));

        assertThatThrownBy(() -> service.create(
                        1L, new CreateFacultyAvailabilityRequest(10L, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(14, 0))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "FACULTY_AVAILABILITY_OVERLAP");
    }

    @Test
    void createAdjacentIntervalIsAllowed() {
        Faculty faculty = activeFaculty();
        AcademicTerm term = activeTerm();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(academicTermService.getEntity(10L)).thenReturn(term);
        when(availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        1L, 10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(window(faculty, term, DayOfWeek.MONDAY, 9, 12)));
        when(availabilityRepository.save(any(FacultyAvailability.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.create(
                        1L, new CreateFacultyAvailabilityRequest(10L, DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(15, 0))))
                .doesNotThrowAnyException();
    }

    @Test
    void createForUnknownFacultyIsRejected() {
        when(facultyService.getEntity(999L))
                .thenThrow(new ResourceNotFoundException("FACULTY_NOT_FOUND", "Faculty not found: 999"));

        assertThatThrownBy(() -> service.create(
                        999L, new CreateFacultyAvailabilityRequest(10L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "FACULTY_NOT_FOUND");
    }

    @Test
    void createForInactiveFacultyIsRejected() {
        Faculty faculty = new Faculty("FAC-OLD", "Retired Faculty", null, null);
        faculty.update("Retired Faculty", null, null, false);
        setId(faculty, 2L);
        when(facultyService.getEntity(2L)).thenReturn(faculty);

        assertThatThrownBy(() -> service.create(
                        2L, new CreateFacultyAvailabilityRequest(10L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "FACULTY_INACTIVE");
    }

    @Test
    void createForUnknownTermIsRejected() {
        Faculty faculty = activeFaculty();
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(academicTermService.getEntity(999L))
                .thenThrow(new ResourceNotFoundException("ACADEMIC_TERM_NOT_FOUND", "Academic term not found: 999"));

        assertThatThrownBy(() -> service.create(
                        1L, new CreateFacultyAvailabilityRequest(999L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ACADEMIC_TERM_NOT_FOUND");
    }
}
