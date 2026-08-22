package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionService;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyService;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectService;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulingContextFactoryTest {

    @Mock
    private SubjectService subjectService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private DivisionService divisionService;

    @Mock
    private BatchService batchService;

    @Mock
    private AllocationQueryService allocationQueryService;

    private SchedulingContextFactory factory;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Division division() {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Division division = new Division(year, "A", 68);
        setId(division, 1L);
        return division;
    }

    private Batch batch(Division division) {
        Batch batch = new Batch(division, "A1", 23);
        setId(batch, 2L);
        return batch;
    }

    private Subject subject() {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Subject subject = new Subject(year, "BDA", "Big Data Analytics");
        setId(subject, 3L);
        return subject;
    }

    private Faculty faculty() {
        Faculty faculty = new Faculty("FAC-BDA", "Faculty BDA", null, null);
        setId(faculty, 4L);
        return faculty;
    }

    @BeforeEach
    void setUp() {
        factory = new SchedulingContextFactory(subjectService, facultyService, divisionService, batchService, allocationQueryService);
    }

    @Test
    void buildsContextForABatchRequestIncludingBatchAllocations() {
        Division division = division();
        Batch batch = batch(division);
        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        when(subjectService.getEntity(3L)).thenReturn(subject());
        when(facultyService.getEntity(4L)).thenReturn(faculty());
        when(divisionService.getEntity(1L)).thenReturn(division);
        when(batchService.getEntity(2L)).thenReturn(batch);
        when(allocationQueryService.findActiveForFaculty(4L, request.allocationDate())).thenReturn(List.of());
        when(allocationQueryService.findActiveForBatch(2L, request.allocationDate())).thenReturn(List.of());
        when(allocationQueryService.findActiveForDivision(1L, request.allocationDate())).thenReturn(List.of());

        SchedulingContext context = factory.build(request);

        assertThat(context.batch()).isNotNull();
        assertThat(context.batch().id()).isEqualTo(2L);
        assertThat(context.division().id()).isEqualTo(1L);
        assertThat(context.subject().code()).isEqualTo("BDA");
        assertThat(context.faculty().employeeCode()).isEqualTo("FAC-BDA");
        verify(allocationQueryService).findActiveForBatch(2L, request.allocationDate());
    }

    @Test
    void buildsContextForADivisionRequestWithNoBatchLookup() {
        Division division = division();
        SchedulingRequest request = new SchedulingRequest(
                AllocationType.REGULAR, TargetType.DIVISION, division.getId(), null, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        when(subjectService.getEntity(3L)).thenReturn(subject());
        when(facultyService.getEntity(4L)).thenReturn(faculty());
        when(divisionService.getEntity(1L)).thenReturn(division);
        when(allocationQueryService.findActiveForFaculty(4L, request.allocationDate())).thenReturn(List.of());
        when(allocationQueryService.findActiveForDivision(1L, request.allocationDate())).thenReturn(List.of());

        SchedulingContext context = factory.build(request);

        assertThat(context.batch()).isNull();
        assertThat(context.existingBatchAllocations()).isEmpty();
        verifyNoMoreInteractions(batchService);
    }
}
