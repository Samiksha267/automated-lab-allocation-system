package com.college.laballocation.academic;

import com.college.laballocation.academic.AcademicTermDtos.AcademicTermResponse;
import com.college.laballocation.academic.AcademicTermDtos.CreateAcademicTermRequest;
import com.college.laballocation.academic.AcademicTermDtos.UpdateAcademicTermStatusRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicTermService {

    private final AcademicTermRepository academicTermRepository;

    public AcademicTermService(AcademicTermRepository academicTermRepository) {
        this.academicTermRepository = academicTermRepository;
    }

    public List<AcademicTermResponse> list() {
        return academicTermRepository.findAll().stream().map(AcademicTermResponse::from).toList();
    }

    public AcademicTerm getEntity(Long id) {
        return academicTermRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ACADEMIC_TERM_NOT_FOUND", "Academic term not found: " + id));
    }

    public AcademicTermResponse get(Long id) {
        return AcademicTermResponse.from(getEntity(id));
    }

    @Transactional
    public AcademicTermResponse create(CreateAcademicTermRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }
        AcademicTerm term = new AcademicTerm(
                request.academicYearLabel(), request.termNumber(), request.displayName(), request.startDate(), request.endDate());
        return AcademicTermResponse.from(academicTermRepository.save(term));
    }

    @Transactional
    public AcademicTermResponse updateStatus(Long id, UpdateAcademicTermStatusRequest request) {
        AcademicTerm term = getEntity(id);
        term.updateStatus(request.status());
        return AcademicTermResponse.from(term);
    }
}
