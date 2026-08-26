package com.college.laballocation.timetableimport;

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
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.faculty.SubjectFacultyAssignmentRepository;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.subject.Subject;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableMappingServiceTest {

    @Mock
    private SubjectFacultyAssignmentRepository subjectFacultyAssignmentRepository;

    @Mock
    private LabRepository labRepository;

    private TimetableMappingService service;
    private AcademicTerm term;
    private Subject subject;
    private Faculty faculty;
    private Division division;
    private Batch batch;
    private Lab lab;

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
        service = new TimetableMappingService(subjectFacultyAssignmentRepository, labRepository);

        term = new AcademicTerm("2026-27", 5, "Semester 5", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 12, 15));
        term.updateStatus(TermStatus.ACTIVE);
        setId(term, 10L);

        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        division = new Division(year, "A", 60);
        setId(division, 1L);
        batch = new Batch(division, "A1", 30);
        setId(batch, 2L);
        subject = new Subject(year, "BDA", "Big Data Analytics");
        setId(subject, 3L);
        faculty = new Faculty("FAC-001", "Dr. S. Sharma", null, null);
        setId(faculty, 4L);
        LabType labType = new LabType("COMPUTER", "Computer Lab", null);
        lab = new Lab("B-204", "Lab B-204", 30, labType, "B", "2", "204");
        setId(lab, 5L);
    }

    private TimetableMappingService.MappingContext contextWith(SubjectFacultyAssignment... assignments) {
        when(subjectFacultyAssignmentRepository.findByAcademicTermIdAndActiveTrue(10L)).thenReturn(List.of(assignments));
        when(labRepository.findAll()).thenReturn(List.of(lab));
        return service.buildContext(10L);
    }

    @Test
    void exactCodeMatchResolvesSubjectFacultyDivisionBatchAndLabTogether() {
        SubjectFacultyAssignment assignment = new SubjectFacultyAssignment(subject, faculty, division, batch, term);
        TimetableMappingService.MappingContext context = contextWith(assignment);
        ParsedTimetableRow raw = new ParsedTimetableRow(1, "MONDAY", "09:00", "11:00", "BDA", "Dr. S. Sharma", "B-204", "A", "A1");

        TimetableMappingService.MappingResult result = service.mapRow(raw, context);

        assertThat(result.subject()).isEqualTo(subject);
        assertThat(result.faculty()).isEqualTo(faculty);
        assertThat(result.division()).isEqualTo(division);
        assertThat(result.batch()).isEqualTo(batch);
        assertThat(result.lab()).isEqualTo(lab);
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void noMatchingAssignmentProducesUnresolvedAcademicAssignmentErrorNeverAnAutoCreatedEntity() {
        TimetableMappingService.MappingContext context = contextWith();
        ParsedTimetableRow raw = new ParsedTimetableRow(1, "MONDAY", "09:00", "11:00", "UNKNOWN101", "Nobody", "B-204", "Z", "Z1");

        TimetableMappingService.MappingResult result = service.mapRow(raw, context);

        assertThat(result.subject()).isNull();
        assertThat(result.messages()).anySatisfy(m -> assertThat(m.code()).isEqualTo("UNRESOLVED_ACADEMIC_ASSIGNMENT"));
    }

    @Test
    void unknownLabCodeProducesUnknownLabErrorNeverAnAutoCreatedLab() {
        SubjectFacultyAssignment assignment = new SubjectFacultyAssignment(subject, faculty, division, batch, term);
        TimetableMappingService.MappingContext context = contextWith(assignment);
        ParsedTimetableRow raw = new ParsedTimetableRow(1, "MONDAY", "09:00", "11:00", "BDA", "Dr. S. Sharma", "Z-999", "A", "A1");

        TimetableMappingService.MappingResult result = service.mapRow(raw, context);

        assertThat(result.lab()).isNull();
        assertThat(result.messages()).anySatisfy(m -> assertThat(m.code()).isEqualTo("UNKNOWN_LAB"));
    }

    @Test
    void facultyNameMismatchIsAWarningNotAnErrorAndDoesNotBlockResolution() {
        SubjectFacultyAssignment assignment = new SubjectFacultyAssignment(subject, faculty, division, batch, term);
        TimetableMappingService.MappingContext context = contextWith(assignment);
        ParsedTimetableRow raw = new ParsedTimetableRow(1, "MONDAY", "09:00", "11:00", "BDA", "Dr. Someone Else", "B-204", "A", "A1");

        TimetableMappingService.MappingResult result = service.mapRow(raw, context);

        assertThat(result.faculty()).isEqualTo(faculty);
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).severity()).isEqualTo(ImportRowStatus.WARNING);
        assertThat(result.messages().get(0).code()).isEqualTo("FACULTY_NAME_MISMATCH");
    }
}
