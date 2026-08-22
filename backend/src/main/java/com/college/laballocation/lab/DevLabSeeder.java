package com.college.laballocation.lab;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-only laboratory demo data (PART 38-44 of the phase brief) - 15
 * labs across wings B/C/D with varied capacities, a small software/equipment
 * catalog, and deliberately only a *subset* of labs with Cloudera installed
 * so Phase 6/9's "BDA requires Cloudera" filtering demo has real labs to
 * exclude. Gated by {@code @Profile("dev")}, ordered after {@code DevUserSeeder}
 * (1) and {@code DevAcademicSeeder} (2) - this seeder doesn't actually depend
 * on either, but keeping dev seeders in one predictable order avoids surprises.
 *
 * <p>No seeded {@code LabUnavailability}: any fixed date becomes permanently
 * stale, and a date relative to application startup would make seed data
 * non-deterministic across restarts (PART 44) - deliberately omitted here;
 * create one via the API for a live demo instead.
 */
@Component
@Profile("dev")
@Order(3)
public class DevLabSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevLabSeeder.class);

    private final LabTypeRepository labTypeRepository;
    private final LabRepository labRepository;
    private final SoftwareRepository softwareRepository;
    private final EquipmentRepository equipmentRepository;
    private final LabSoftwareRepository labSoftwareRepository;
    private final LabEquipmentRepository labEquipmentRepository;

    public DevLabSeeder(
            LabTypeRepository labTypeRepository,
            LabRepository labRepository,
            SoftwareRepository softwareRepository,
            EquipmentRepository equipmentRepository,
            LabSoftwareRepository labSoftwareRepository,
            LabEquipmentRepository labEquipmentRepository) {
        this.labTypeRepository = labTypeRepository;
        this.labRepository = labRepository;
        this.softwareRepository = softwareRepository;
        this.equipmentRepository = equipmentRepository;
        this.labSoftwareRepository = labSoftwareRepository;
        this.labEquipmentRepository = labEquipmentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LabType computer = labType("COMPUTER", "Computer Lab", "General-purpose computing lab");
        LabType networking = labType("NETWORKING", "Networking Lab", "Networking/infrastructure lab");
        LabType dataEngineering = labType("DATA_ENGINEERING", "Data Engineering Lab", "Big data / distributed systems lab");
        LabType general = labType("GENERAL", "General Lab", "General-purpose lab");

        Software cloudera = software("CLOUDERA", "Cloudera");
        Software hadoop = software("HADOOP", "Hadoop");
        Software spark = software("SPARK", "Spark");
        Software mysql = software("MYSQL", "MySQL");
        Software oracle = software("ORACLE", "Oracle");
        Software python = software("PYTHON", "Python");
        Software java = software("JAVA", "Java");

        Equipment router = equipment("ROUTER", "Router", "Network router");
        Equipment switchEq = equipment("SWITCH", "Switch", "Network switch");
        Equipment gpuWorkstation = equipment("GPU_WORKSTATION", "GPU Workstation", "GPU-equipped workstation");
        Equipment projector = equipment("PROJECTOR", "Projector", "Ceiling-mounted projector");

        // code -> (name, capacity, type, wing, floor, room)
        Lab b101 = lab("B-101", "General Lab 1", 30, general, "B", "1", "101");
        Lab b102 = lab("B-102", "Computer Lab 1", 40, computer, "B", "1", "102");
        Lab b201 = lab("B-201", "Computer Lab 2", 50, computer, "B", "2", "201");
        Lab b202 = lab("B-202", "Computer Lab 3", 60, computer, "B", "2", "202");
        Lab b301 = lab("B-301", "Computer Lab 4", 70, computer, "B", "3", "301");

        Lab c101 = lab("C-101", "Networking Lab 1", 40, networking, "C", "1", "101");
        Lab c102 = lab("C-102", "Networking Lab 2", 50, networking, "C", "1", "102");
        Lab c201 = lab("C-201", "Data Engineering Lab 1", 60, dataEngineering, "C", "2", "201");
        Lab c202 = lab("C-202", "Data Engineering Lab 2", 72, dataEngineering, "C", "2", "202");
        Lab c304 = lab("C-304", "Data Engineering Lab", 72, dataEngineering, "C", "3", "304");

        Lab d101 = lab("D-101", "General Lab 2", 30, general, "D", "1", "101");
        Lab d102 = lab("D-102", "Computer Lab 5", 40, computer, "D", "1", "102");
        Lab d201 = lab("D-201", "Computer Lab 6", 50, computer, "D", "2", "201");
        Lab d202 = lab("D-202", "Computer Lab 7", 60, computer, "D", "2", "202");
        Lab d301 = lab("D-301", "Computer Lab 8", 80, computer, "D", "3", "301");

        // Cloudera installed in only a subset (PART 42) - B-201 (50, below Division A's
        // strength of 68 - a "smaller Cloudera lab"), B-301 (70) and C-202 (72) both
        // satisfy capacity >= 68 WITH Cloudera; C-304 (72) deliberately does NOT have
        // Cloudera despite matching capacity - the "Lab Y" scenario PART 42 asks for.
        installSoftware(b201, cloudera);
        installSoftware(b301, cloudera);
        installSoftware(b301, spark);
        installSoftware(c202, cloudera);
        installSoftware(c202, hadoop);
        installSoftware(c202, spark);
        installSoftware(c201, hadoop);
        installSoftware(c201, spark);
        installSoftware(d102, mysql);
        installSoftware(d102, oracle);
        installSoftware(d201, python);
        installSoftware(d201, java);
        installSoftware(d301, python);
        installSoftware(d301, java);

        assignEquipment(c101, router, 10);
        assignEquipment(c101, switchEq, 5);
        assignEquipment(c102, router, 6);
        assignEquipment(c202, gpuWorkstation, 4);
        assignEquipment(c304, gpuWorkstation, 2);
        assignEquipment(b101, projector, 1);
        assignEquipment(d101, projector, 1);

        log.info("Dev lab seed complete: 15 labs across wings B/C/D");
    }

    private LabType labType(String code, String name, String description) {
        return labTypeRepository.findByCode(code).orElseGet(() -> labTypeRepository.save(new LabType(code, name, description)));
    }

    private Software software(String code, String name) {
        return softwareRepository.findByCode(code).orElseGet(() -> softwareRepository.save(new Software(code, name)));
    }

    private Equipment equipment(String code, String name, String description) {
        return equipmentRepository.findByCode(code).orElseGet(() -> equipmentRepository.save(new Equipment(code, name, description)));
    }

    private Lab lab(String code, String name, int capacity, LabType type, String wing, String floor, String room) {
        return labRepository
                .findByCode(code)
                .orElseGet(() -> labRepository.save(new Lab(code, name, capacity, type, wing, floor, room)));
    }

    private void installSoftware(Lab lab, Software software) {
        if (!labSoftwareRepository.existsByLabIdAndSoftwareId(lab.getId(), software.getId())) {
            labSoftwareRepository.save(new LabSoftware(lab, software, null));
        }
    }

    private void assignEquipment(Lab lab, Equipment equipment, int quantity) {
        if (!labEquipmentRepository.existsByLabIdAndEquipmentId(lab.getId(), equipment.getId())) {
            labEquipmentRepository.save(new LabEquipment(lab, equipment, quantity));
        }
    }
}
