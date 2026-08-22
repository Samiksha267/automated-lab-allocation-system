package com.college.laballocation.faculty;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
 * Development-only faculty availability demo data (Phase 7). Preserves the
 * existing valid demo ("A1 BDA Monday 09:00-11:00 -> Faculty BDA", "A2 CNS
 * Monday 09:00-11:00 -> Faculty CNS" - both faculty available then) and adds
 * a documented future-conflict scenario: Faculty BDA is deliberately NOT
 * available Monday 13:00-15:00 (the gap between its two Monday windows), for
 * the future {@code FacultyAvailabilityConstraint} demo (Phase 9+).
 *
 * <p>Ordered ({@code @Order(5)}) after {@code DevSubjectRequirementSeeder}
 * ({@code @Order(4)}) - references the same demo {@code Faculty}/
 * {@code AcademicTerm} rows created by Phase 4's {@code DevAcademicSeeder},
 * resolved by natural key (employee code, term label+number), never by id.
 * Idempotent: each window is only inserted if no row with the same
 * faculty/term/day/start/end already exists, so restarting the dev profile
 * never duplicates data.
 */
@Component
@Profile("dev")
@Order(5)
public class DevFacultyAvailabilitySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevFacultyAvailabilitySeeder.class);

    private final FacultyRepository facultyRepository;
    private final AcademicTermRepository academicTermRepository;
    private final FacultyAvailabilityRepository availabilityRepository;

    public DevFacultyAvailabilitySeeder(
            FacultyRepository facultyRepository,
            AcademicTermRepository academicTermRepository,
            FacultyAvailabilityRepository availabilityRepository) {
        this.facultyRepository = facultyRepository;
        this.academicTermRepository = academicTermRepository;
        this.availabilityRepository = availabilityRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AcademicTerm term = academicTermRepository.findByAcademicYearLabelAndTermNumber("2026-27", 5).orElse(null);
        if (term == null) {
            log.warn("Skipping faculty availability seed - demo academic term not found (DevAcademicSeeder must run first).");
            return;
        }

        facultyRepository
                .findByEmployeeCode("FAC-BDA")
                .ifPresentOrElse(
                        bda -> {
                            availability(bda, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
                            availability(bda, term, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(17, 0));
                            availability(bda, term, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(15, 0));
                        },
                        () -> log.warn("Skipping Faculty BDA availability seed - faculty FAC-BDA not found."));

        facultyRepository
                .findByEmployeeCode("FAC-CNS")
                .ifPresentOrElse(
                        cns -> {
                            availability(cns, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0));
                            availability(cns, term, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
                        },
                        () -> log.warn("Skipping Faculty CNS availability seed - faculty FAC-CNS not found."));
    }

    private void availability(Faculty faculty, AcademicTerm term, DayOfWeek day, LocalTime start, LocalTime end) {
        List<FacultyAvailability> existing = availabilityRepository
                .findByFacultyIdAndAcademicTermIdAndDayOfWeekOrderByStartTimeAsc(faculty.getId(), term.getId(), day);
        boolean alreadySeeded =
                existing.stream().anyMatch(row -> row.getStartTime().equals(start) && row.getEndTime().equals(end));
        if (alreadySeeded) {
            return;
        }
        availabilityRepository.save(new FacultyAvailability(faculty, term, day, start, end));
        log.info("Seeded dev faculty availability: {} {} {}-{}", faculty.getEmployeeCode(), day, start, end);
    }
}
