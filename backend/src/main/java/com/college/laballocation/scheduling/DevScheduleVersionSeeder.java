package com.college.laballocation.scheduling;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-only seed data for Phase 8: a single published {@code ScheduleVersion}
 * (version 1) for the existing demo academic term ("2026-27" / Semester 5,
 * see {@code DevAcademicSeeder}), so the persistence layer has a real,
 * queryable baseline for manual verification and for later phases to attach
 * allocations to.
 *
 * <p>Deliberately seeds <b>no</b> {@code Allocation} rows (PART 34 of the
 * Phase 8 brief) - an allocation tied to a fixed calendar date would go
 * stale the moment "today" passes it, misleadingly suggesting a demo that
 * no longer reflects the current date. Allocation persistence is proven
 * instead by {@code AllocationPersistenceIT} and manual Docker verification
 * against freshly-created, date-relative test rows, not standing dev data.
 *
 * <p>Ordered ({@code @Order(6)}) after {@code DevFacultyAvailabilitySeeder}
 * ({@code @Order(5)}) - resolves the demo term and the Lab Assistant user by
 * natural key, never by id, matching every other seeder in this project.
 */
@Component
@Profile("dev")
@Order(6)
public class DevScheduleVersionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevScheduleVersionSeeder.class);

    private final AcademicTermRepository academicTermRepository;
    private final UserRepository userRepository;
    private final ScheduleVersionRepository scheduleVersionRepository;
    private final ScheduleVersionService scheduleVersionService;

    public DevScheduleVersionSeeder(
            AcademicTermRepository academicTermRepository,
            UserRepository userRepository,
            ScheduleVersionRepository scheduleVersionRepository,
            ScheduleVersionService scheduleVersionService) {
        this.academicTermRepository = academicTermRepository;
        this.userRepository = userRepository;
        this.scheduleVersionRepository = scheduleVersionRepository;
        this.scheduleVersionService = scheduleVersionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AcademicTerm term = academicTermRepository.findByAcademicYearLabelAndTermNumber("2026-27", 5).orElse(null);
        if (term == null) {
            log.warn("Skipping schedule version seed - demo academic term not found (DevAcademicSeeder must run first).");
            return;
        }
        if (scheduleVersionRepository.countByAcademicTermId(term.getId()) > 0) {
            return;
        }
        AppUser labAssistant = userRepository.findByEmail(AppUser.normalizeEmail("lab.assistant@example.edu")).orElse(null);
        if (labAssistant == null) {
            log.warn("Skipping schedule version seed - demo Lab Assistant user not found (DevUserSeeder must run first).");
            return;
        }

        ScheduleVersion version = scheduleVersionService.createDraft(term.getId(), null, labAssistant.getId());
        scheduleVersionService.publish(version.getId(), labAssistant.getId());
        log.info(
                "Seeded dev schedule version: term={} version={} status=PUBLISHED",
                term.getDisplayName(), version.getVersionNumber());
    }
}
