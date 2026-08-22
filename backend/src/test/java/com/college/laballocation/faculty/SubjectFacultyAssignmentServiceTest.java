package com.college.laballocation.faculty;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionService;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.common.InvalidAcademicRelationshipException;
import com.college.laballocation.faculty.SubjectFacultyAssignmentDtos.CreateSubjectFacultyAssignmentRequest;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Proves "batch belongs to division" cross-table validation (PART 27 of the
 * phase brief) - this can't be a database CHECK constraint in Postgres, so it
 * must be application-enforced, and this test is the proof it actually is.
 */
@ExtendWith(MockitoExtension.class)
class SubjectFacultyAssignmentServiceTest {

    @Mock
    private SubjectFacultyAssignmentRepository assignmentRepository;

    @Mock
    private SubjectService subjectService;

    @Mock
    private FacultyService facultyService;

    @Mock
    private DivisionService divisionService;

    @Mock
    private BatchService batchService;

    @Mock
    private AcademicTermService academicTermService;

    private static void setId(Object entity, Long id) {
        try {
            Field field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        return type.getDeclaredField("id");
    }

    @Test
    void batchFromADifferentDivisionIsRejected() {
        var service = new SubjectFacultyAssignmentService(
                assignmentRepository, subjectService, facultyService, divisionService, batchService, academicTermService);

        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);

        Subject subject = new Subject(year, "BDA", "Big Data Analytics");
        Faculty faculty = new Faculty("FAC-1", "Faculty One", null, null);

        Division divisionA = new Division(year, "A", 60);
        setId(divisionA, 1L);
        Division divisionB = new Division(year, "B", 60);
        setId(divisionB, 2L);

        // Batch B1 genuinely belongs to Division B, not Division A.
        Batch batchFromDivisionB = new Batch(divisionB, "B1", 30);
        setId(batchFromDivisionB, 99L);

        AcademicTerm term = new AcademicTerm(
                "2026-27", 5, "Semester 5", java.time.LocalDate.now(), java.time.LocalDate.now().plusMonths(4));

        when(subjectService.getEntity(1L)).thenReturn(subject);
        when(facultyService.getEntity(1L)).thenReturn(faculty);
        when(divisionService.getEntity(1L)).thenReturn(divisionA);
        when(academicTermService.getEntity(1L)).thenReturn(term);
        when(batchService.getEntity(99L)).thenReturn(batchFromDivisionB);

        var request = new CreateSubjectFacultyAssignmentRequest(1L, 1L, 1L, 99L, 1L);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(InvalidAcademicRelationshipException.class);
    }
}
