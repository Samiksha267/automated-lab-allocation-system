package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.SUBJECT_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.lab;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.lab.Software;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectSoftwareRequirement;
import com.college.laballocation.subject.SubjectSoftwareRequirementRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequiredSoftwareConstraintTest {

    @Mock
    private SubjectSoftwareRequirementRepository requirementRepository;

    private RequiredSoftwareConstraint constraint;

    private Subject subject() {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        return new Subject(year, "BDA", "Big Data Analytics");
    }

    private SubjectSoftwareRequirement requirement(String code) {
        return new SubjectSoftwareRequirement(subject(), new Software(code, code));
    }

    private LabRef labWithSoftware(String... codes) {
        return lab(70, 20L, "COMPUTER", Set.of(codes), Map.of(), List.of(), List.of());
    }

    @Test
    void idIsHc08() {
        constraint = new RequiredSoftwareConstraint(requirementRepository);
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_08_REQUIRED_SOFTWARE);
    }

    @Test
    void emptyRequirementsAlwaysPass() {
        when(requirementRepository.findBySubjectIdOrderBySoftware_Code(SUBJECT_ID)).thenReturn(List.of());
        constraint = new RequiredSoftwareConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithSoftware()));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    /** BDA/Cloudera demo, PART 27 - lab has Cloudera. */
    @Test
    void passesWhenLabHasAllRequiredSoftware() {
        when(requirementRepository.findBySubjectIdOrderBySoftware_Code(SUBJECT_ID)).thenReturn(List.of(requirement("CLOUDERA")));
        constraint = new RequiredSoftwareConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithSoftware("CLOUDERA")));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    /** BDA/Cloudera demo, PART 27 - lab lacks Cloudera. */
    @Test
    void failsWhenLabIsMissingRequiredSoftware() {
        when(requirementRepository.findBySubjectIdOrderBySoftware_Code(SUBJECT_ID)).thenReturn(List.of(requirement("CLOUDERA")));
        constraint = new RequiredSoftwareConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithSoftware()));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("SOFTWARE_MISMATCH");
        assertThat(result.violation().details()).containsEntry("missingSoftware", List.of("CLOUDERA"));
    }

    @Test
    void failsWhenLabHasOnlySomeOfMultipleRequiredSoftware() {
        when(requirementRepository.findBySubjectIdOrderBySoftware_Code(SUBJECT_ID))
                .thenReturn(List.of(requirement("CLOUDERA"), requirement("SPARK")));
        constraint = new RequiredSoftwareConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithSoftware("CLOUDERA")));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) result.violation().details().get("missingSoftware");
        assertThat(missing).containsExactly("SPARK");
    }

    @Test
    void passesWhenLabHasAllRequiredPlusExtraSoftware() {
        when(requirementRepository.findBySubjectIdOrderBySoftware_Code(SUBJECT_ID))
                .thenReturn(List.of(requirement("CLOUDERA"), requirement("SPARK")));
        constraint = new RequiredSoftwareConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithSoftware("CLOUDERA", "SPARK", "HADOOP")));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
