package com.college.laballocation.subject;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.AcademicYearRepository;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.subject.SubjectDtos.CreateSubjectRequest;
import com.college.laballocation.subject.SubjectDtos.SubjectResponse;
import com.college.laballocation.subject.SubjectDtos.UpdateSubjectRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final AcademicYearRepository academicYearRepository;

    public SubjectService(SubjectRepository subjectRepository, AcademicYearRepository academicYearRepository) {
        this.subjectRepository = subjectRepository;
        this.academicYearRepository = academicYearRepository;
    }

    public List<SubjectResponse> listByAcademicYear(Long academicYearId) {
        return subjectRepository.findByAcademicYearId(academicYearId).stream().map(SubjectResponse::from).toList();
    }

    public Subject getEntity(Long id) {
        return subjectRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SUBJECT_NOT_FOUND", "Subject not found: " + id));
    }

    public SubjectResponse get(Long id) {
        return SubjectResponse.from(getEntity(id));
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        AcademicYear year = academicYearRepository
                .findById(request.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ACADEMIC_YEAR_NOT_FOUND", "Academic year not found: " + request.academicYearId()));
        if (subjectRepository.existsByAcademicYearIdAndCode(year.getId(), request.code())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Subject code already exists for this academic year: " + request.code());
        }
        return SubjectResponse.from(subjectRepository.save(new Subject(year, request.code(), request.name())));
    }

    @Transactional
    public SubjectResponse update(Long id, UpdateSubjectRequest request) {
        Subject subject = getEntity(id);
        subject.update(request.name(), request.active());
        return SubjectResponse.from(subject);
    }
}
