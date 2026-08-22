package com.college.laballocation.subject;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.AcademicYearRepository;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.ProgramRepository;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.StreamRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeRepository;
import com.college.laballocation.lab.Software;
import com.college.laballocation.lab.SoftwareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-only subject-requirement demo data (PART 27-29 of the phase
 * brief) - BDA requires Cloudera and prefers a Data Engineering lab (never
 * requires it - that would make every non-Data-Engineering lab an invalid
 * candidate even though only the software actually matters for this demo,
 * see docs/06-CONSTRAINTS.md/docs/07-ALLOCATION-SCORING.md's required-vs-preferred
 * distinction); CNS deliberately has zero special requirements, proving the
 * optional-requirements path works with real seeded data, not just a test.
 *
 * <p>Ordered ({@code @Order(4)}) after {@code DevLabSeeder} ({@code @Order(3)}),
 * since it references {@code Software}/{@code LabType} rows that seeder
 * creates - split into its own seeder specifically so each phase's dev data
 * stays in its own file, rather than reaching back into {@code DevAcademicSeeder}
 * (Phase 4) to add Phase 6 concerns.
 */
@Component
@Profile("dev")
@Order(4)
public class DevSubjectRequirementSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSubjectRequirementSeeder.class);

    private final ProgramRepository programRepository;
    private final StreamRepository streamRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SubjectRepository subjectRepository;
    private final SoftwareRepository softwareRepository;
    private final LabTypeRepository labTypeRepository;
    private final SubjectSoftwareRequirementRepository softwareRequirementRepository;

    public DevSubjectRequirementSeeder(
            ProgramRepository programRepository,
            StreamRepository streamRepository,
            AcademicYearRepository academicYearRepository,
            SubjectRepository subjectRepository,
            SoftwareRepository softwareRepository,
            LabTypeRepository labTypeRepository,
            SubjectSoftwareRequirementRepository softwareRequirementRepository) {
        this.programRepository = programRepository;
        this.streamRepository = streamRepository;
        this.academicYearRepository = academicYearRepository;
        this.subjectRepository = subjectRepository;
        this.softwareRepository = softwareRepository;
        this.labTypeRepository = labTypeRepository;
        this.softwareRequirementRepository = softwareRequirementRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Program btech = programRepository.findByCode("BTECH").orElse(null);
        if (btech == null) {
            log.warn("BTECH program not found - skipping subject requirement seed (DevAcademicSeeder may not have run)");
            return;
        }
        Stream csStream = streamRepository.findByProgramIdAndCode(btech.getId(), "CS").orElse(null);
        if (csStream == null) {
            return;
        }
        AcademicYear csYear3 = academicYearRepository.findByStreamIdAndYearNumber(csStream.getId(), 3).orElse(null);
        if (csYear3 == null) {
            return;
        }

        Subject bda = subjectRepository.findByAcademicYearIdAndCode(csYear3.getId(), "BDA").orElse(null);
        if (bda != null) {
            softwareRepository.findByCode("CLOUDERA").ifPresent(cloudera -> requireSoftware(bda, cloudera));
            labTypeRepository.findByCode("DATA_ENGINEERING").ifPresent(dataEngineering -> preferLabType(bda, dataEngineering));
        }

        // CNS intentionally receives no requirements at all - proves the
        // "zero special requirements" path with real seeded data (PART 28).
    }

    private void requireSoftware(Subject subject, Software software) {
        if (!softwareRequirementRepository.existsBySubjectIdAndSoftwareId(subject.getId(), software.getId())) {
            softwareRequirementRepository.save(new SubjectSoftwareRequirement(subject, software));
            log.info("Seeded dev subject requirement: {} requires {}", subject.getCode(), software.getCode());
        }
    }

    private void preferLabType(Subject subject, LabType labType) {
        if (subject.getRequiredLabType() == null && subject.getPreferredLabType() == null) {
            subject.setLabTypeRequirement(null, labType);
            log.info("Seeded dev subject preference: {} prefers lab type {}", subject.getCode(), labType.getCode());
        }
    }
}
