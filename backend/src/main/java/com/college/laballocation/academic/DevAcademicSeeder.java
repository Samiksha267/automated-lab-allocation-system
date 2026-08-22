package com.college.laballocation.academic;

import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyRepository;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.faculty.SubjectFacultyAssignmentRepository;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-only academic demo data (PART 36/37 of the phase brief) - the
 * required demo scenario (B.Tech/CS/Year 3/Division A, batches A1-A3,
 * subjects BDA/CNS, faculty, and assignments enabling the documented
 * "A1+BDA -> Faculty BDA, A2+CNS -> Faculty CNS" example). Gated by
 * {@code @Profile("dev")}, same as {@code DevUserSeeder} (never runs in any
 * other profile); ordered to run after it ({@code @Order(2)}) since it links
 * the existing demo CR user to Division A via {@link CrAssignment}.
 *
 * <p>Idempotent via natural-key existence checks (codes, employee codes,
 * term label+number) - never relies on auto-increment ids, so restarting the
 * dev profile repeatedly does not duplicate data.
 */
@Component
@Profile("dev")
@Order(2)
public class DevAcademicSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAcademicSeeder.class);

    private final ProgramRepository programRepository;
    private final StreamRepository streamRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicTermRepository academicTermRepository;
    private final DivisionRepository divisionRepository;
    private final BatchRepository batchRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectFacultyAssignmentRepository assignmentRepository;
    private final CrAssignmentRepository crAssignmentRepository;
    private final UserRepository userRepository;

    public DevAcademicSeeder(
            ProgramRepository programRepository,
            StreamRepository streamRepository,
            AcademicYearRepository academicYearRepository,
            AcademicTermRepository academicTermRepository,
            DivisionRepository divisionRepository,
            BatchRepository batchRepository,
            SubjectRepository subjectRepository,
            FacultyRepository facultyRepository,
            SubjectFacultyAssignmentRepository assignmentRepository,
            CrAssignmentRepository crAssignmentRepository,
            UserRepository userRepository) {
        this.programRepository = programRepository;
        this.streamRepository = streamRepository;
        this.academicYearRepository = academicYearRepository;
        this.academicTermRepository = academicTermRepository;
        this.divisionRepository = divisionRepository;
        this.batchRepository = batchRepository;
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
        this.assignmentRepository = assignmentRepository;
        this.crAssignmentRepository = crAssignmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Program btech = program("BTECH", "B.Tech", 4);
        Program mbatech = program("MBATECH", "MBA Tech", 3);

        List<Stream> btechStreams = List.of(
                stream(btech, "CE", "Computer Engineering"),
                stream(btech, "CS", "Computer Science"),
                stream(btech, "IT", "Information Technology"),
                stream(btech, "AIML", "AI & Machine Learning"));
        for (Stream s : btechStreams) {
            for (int y = 1; y <= 4; y++) {
                academicYear(s, y);
            }
        }

        List<Stream> mbaStreams =
                List.of(stream(mbatech, "CE", "Computer Engineering"), stream(mbatech, "DS", "Data Science"));
        for (Stream s : mbaStreams) {
            for (int y = 1; y <= 3; y++) {
                academicYear(s, y);
            }
        }

        AcademicTerm term = academicTerm();

        Stream csStream = streamRepository.findByProgramIdAndCode(btech.getId(), "CS").orElseThrow();
        AcademicYear csYear3 = academicYearRepository.findByStreamIdAndYearNumber(csStream.getId(), 3).orElseThrow();
        Division divisionA = division(csYear3, "A", 68);
        Batch a1 = batch(divisionA, "A1", 23);
        Batch a2 = batch(divisionA, "A2", 23);
        batch(divisionA, "A3", 22);

        Subject bda = subject(csYear3, "BDA", "Big Data Analytics");
        Subject cns = subject(csYear3, "CNS", "Cryptography & Network Security");

        Faculty facultyBda = faculty("FAC-BDA", "Faculty BDA", "faculty.bda@example.edu", "Computer Science");
        Faculty facultyCns = faculty("FAC-CNS", "Faculty CNS", "faculty.cns@example.edu", "Computer Science");

        assignFaculty(bda, facultyBda, divisionA, a1, term);
        assignFaculty(cns, facultyCns, divisionA, a2, term);

        userRepository.findByEmail(AppUser.normalizeEmail("cr@example.edu")).ifPresent(crUser -> {
            userRepository.findByEmail(AppUser.normalizeEmail("lab.assistant@example.edu")).ifPresent(labAssistant -> {
                assignCr(crUser, divisionA, term, labAssistant);
            });
        });
    }

    private Program program(String code, String name, int durationYears) {
        return programRepository.findByCode(code).orElseGet(() -> {
            Program saved = programRepository.save(new Program(code, name, durationYears));
            log.info("Seeded dev program: {}", code);
            return saved;
        });
    }

    private Stream stream(Program program, String code, String name) {
        return streamRepository.findByProgramIdAndCode(program.getId(), code).orElseGet(() -> {
            Stream saved = streamRepository.save(new Stream(program, code, name));
            log.info("Seeded dev stream: {}/{}", program.getCode(), code);
            return saved;
        });
    }

    private AcademicYear academicYear(Stream stream, int yearNumber) {
        return academicYearRepository
                .findByStreamIdAndYearNumber(stream.getId(), yearNumber)
                .orElseGet(() -> academicYearRepository.save(new AcademicYear(stream, yearNumber)));
    }

    private AcademicTerm academicTerm() {
        return academicTermRepository.findByAcademicYearLabelAndTermNumber("2026-27", 5).orElseGet(() -> {
            AcademicTerm term = new AcademicTerm(
                    "2026-27", 5, "Semester 5 (2026-27)", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 12, 15));
            term.updateStatus(TermStatus.ACTIVE);
            AcademicTerm saved = academicTermRepository.save(term);
            log.info("Seeded dev academic term: {}", saved.getDisplayName());
            return saved;
        });
    }

    private Division division(AcademicYear year, String code, int strength) {
        return divisionRepository.findByAcademicYearIdAndCode(year.getId(), code).orElseGet(() -> {
            Division saved = divisionRepository.save(new Division(year, code, strength));
            log.info("Seeded dev division: {}", code);
            return saved;
        });
    }

    private Batch batch(Division division, String code, int strength) {
        return batchRepository
                .findByDivisionIdAndCode(division.getId(), code)
                .orElseGet(() -> batchRepository.save(new Batch(division, code, strength)));
    }

    private Subject subject(AcademicYear year, String code, String name) {
        return subjectRepository.findByAcademicYearIdAndCode(year.getId(), code).orElseGet(() -> {
            Subject saved = subjectRepository.save(new Subject(year, code, name));
            log.info("Seeded dev subject: {}", code);
            return saved;
        });
    }

    private Faculty faculty(String employeeCode, String name, String email, String department) {
        return facultyRepository.findByEmployeeCode(employeeCode).orElseGet(() -> {
            Faculty saved = facultyRepository.save(new Faculty(employeeCode, name, email, department));
            log.info("Seeded dev faculty: {}", employeeCode);
            return saved;
        });
    }

    private void assignFaculty(Subject subject, Faculty faculty, Division division, Batch batch, AcademicTerm term) {
        assignmentRepository
                .findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
                        subject.getId(), division.getId(), batch.getId(), term.getId())
                .orElseGet(() -> {
                    SubjectFacultyAssignment saved = assignmentRepository.save(
                            new SubjectFacultyAssignment(subject, faculty, division, batch, term));
                    log.info("Seeded dev faculty assignment: {} + {} -> {}", subject.getCode(), batch.getCode(), faculty.getName());
                    return saved;
                });
    }

    private void assignCr(AppUser crUser, Division division, AcademicTerm term, AppUser labAssistant) {
        crAssignmentRepository
                .findByUserIdAndAcademicTermIdAndStatus(crUser.getId(), term.getId(), CrAssignmentStatus.ACTIVE)
                .ifPresentOrElse(
                        existing -> {},
                        () -> {
                            crAssignmentRepository.save(new CrAssignment(crUser, division, term, labAssistant));
                            log.info("Seeded dev CR assignment: {} -> Division {}", crUser.getEmail(), division.getCode());
                        });
    }
}
