package com.college.laballocation.faculty;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionService;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.InvalidAcademicRelationshipException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.faculty.SubjectFacultyAssignmentDtos.CreateSubjectFacultyAssignmentRequest;
import com.college.laballocation.faculty.SubjectFacultyAssignmentDtos.SubjectFacultyAssignmentResponse;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubjectFacultyAssignmentService {

    private final SubjectFacultyAssignmentRepository assignmentRepository;
    private final SubjectService subjectService;
    private final FacultyService facultyService;
    private final DivisionService divisionService;
    private final BatchService batchService;
    private final AcademicTermService academicTermService;

    public SubjectFacultyAssignmentService(
            SubjectFacultyAssignmentRepository assignmentRepository,
            SubjectService subjectService,
            FacultyService facultyService,
            DivisionService divisionService,
            BatchService batchService,
            AcademicTermService academicTermService) {
        this.assignmentRepository = assignmentRepository;
        this.subjectService = subjectService;
        this.facultyService = facultyService;
        this.divisionService = divisionService;
        this.batchService = batchService;
        this.academicTermService = academicTermService;
    }

    /**
     * Validates every relationship explicitly (PART 26/27 of the phase brief) -
     * foreign keys alone don't catch a batch that exists but belongs to a
     * different division than the one specified.
     */
    @Transactional
    public SubjectFacultyAssignmentResponse create(CreateSubjectFacultyAssignmentRequest request) {
        Subject subject = subjectService.getEntity(request.subjectId());
        Faculty faculty = facultyService.getEntity(request.facultyId());
        Division division = divisionService.getEntity(request.divisionId());
        AcademicTerm term = academicTermService.getEntity(request.academicTermId());

        Batch batch = null;
        if (request.batchId() != null) {
            batch = batchService.getEntity(request.batchId());
            if (!batch.getDivision().getId().equals(division.getId())) {
                throw new InvalidAcademicRelationshipException(
                        "Batch " + batch.getId() + " does not belong to division " + division.getId() + ".");
            }
        }

        try {
            SubjectFacultyAssignment saved =
                    assignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
            assignmentRepository.flush();
            return SubjectFacultyAssignmentResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    "AMBIGUOUS_FACULTY_ASSIGNMENT", HttpStatus.CONFLICT,
                    "An active faculty assignment already exists for this subject/division/batch/term.");
        }
    }

    public SubjectFacultyAssignmentResponse get(Long id) {
        return SubjectFacultyAssignmentResponse.from(assignmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND", "Assignment not found: " + id)));
    }

    @Transactional
    public void deactivate(Long id) {
        SubjectFacultyAssignment assignment = assignmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND", "Assignment not found: " + id));
        assignment.deactivate();
    }
}
