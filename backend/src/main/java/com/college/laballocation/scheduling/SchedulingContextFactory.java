package com.college.laballocation.scheduling;

import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionService;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyService;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a {@link SchedulingContext} for a {@link SchedulingRequest} by
 * calling the already-existing Phase 4/5 services and this phase's own
 * {@link AllocationQueryService} - the one place that does the "load once,
 * reuse across every candidate" work described in {@link SchedulingContext}'s
 * javadoc. No constraint evaluation happens here; this only resolves data.
 */
@Service
@Transactional(readOnly = true)
public class SchedulingContextFactory {

    private final SubjectService subjectService;
    private final FacultyService facultyService;
    private final DivisionService divisionService;
    private final BatchService batchService;
    private final AllocationQueryService allocationQueryService;

    public SchedulingContextFactory(
            SubjectService subjectService,
            FacultyService facultyService,
            DivisionService divisionService,
            BatchService batchService,
            AllocationQueryService allocationQueryService) {
        this.subjectService = subjectService;
        this.facultyService = facultyService;
        this.divisionService = divisionService;
        this.batchService = batchService;
        this.allocationQueryService = allocationQueryService;
    }

    public SchedulingContext build(SchedulingRequest request) {
        Subject subject = subjectService.getEntity(request.subjectId());
        Faculty faculty = facultyService.getEntity(request.facultyId());
        Division division = divisionService.getEntity(request.divisionId());
        Batch batch = request.batchId() != null ? batchService.getEntity(request.batchId()) : null;

        List<ExistingAllocationSnapshot> facultyAllocations = snapshot(
                allocationQueryService.findActiveForFaculty(faculty.getId(), request.allocationDate()));
        List<ExistingAllocationSnapshot> batchAllocations = batch == null
                ? List.of()
                : snapshot(allocationQueryService.findActiveForBatch(batch.getId(), request.allocationDate()));
        List<ExistingAllocationSnapshot> divisionAllocations = snapshot(
                allocationQueryService.findActiveForDivision(division.getId(), request.allocationDate()));

        Long requiredLabTypeId = subject.getRequiredLabType() != null ? subject.getRequiredLabType().getId() : null;
        Long preferredLabTypeId = subject.getPreferredLabType() != null ? subject.getPreferredLabType().getId() : null;

        return new SchedulingContext(
                request,
                new SubjectRef(
                        subject.getId(),
                        subject.getCode(),
                        subject.getName(),
                        subject.getAcademicYear().getId(),
                        requiredLabTypeId,
                        preferredLabTypeId),
                new FacultyRef(faculty.getId(), faculty.getEmployeeCode(), faculty.getName(), faculty.isActive()),
                new DivisionRef(division.getId(), division.getCode(), division.getStrength(), division.getAcademicYear().getId()),
                batch == null
                        ? null
                        : new BatchRef(batch.getId(), batch.getCode(), batch.getStrength(), batch.getDivision().getId()),
                facultyAllocations,
                batchAllocations,
                divisionAllocations);
    }

    private List<ExistingAllocationSnapshot> snapshot(List<Allocation> allocations) {
        return allocations.stream().map(ExistingAllocationSnapshot::from).toList();
    }
}
