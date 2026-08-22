package com.college.laballocation.academic;

import com.college.laballocation.academic.DivisionDtos.CreateDivisionRequest;
import com.college.laballocation.academic.DivisionDtos.DivisionResponse;
import com.college.laballocation.academic.DivisionDtos.UpdateDivisionRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DivisionService {

    private final DivisionRepository divisionRepository;
    private final AcademicYearService academicYearService;

    public DivisionService(DivisionRepository divisionRepository, AcademicYearService academicYearService) {
        this.divisionRepository = divisionRepository;
        this.academicYearService = academicYearService;
    }

    public List<DivisionResponse> listByAcademicYear(Long academicYearId) {
        return divisionRepository.findByAcademicYearId(academicYearId).stream().map(DivisionResponse::from).toList();
    }

    public Division getEntity(Long id) {
        return divisionRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DIVISION_NOT_FOUND", "Division not found: " + id));
    }

    public DivisionResponse get(Long id) {
        return DivisionResponse.from(getEntity(id));
    }

    @Transactional
    public DivisionResponse create(CreateDivisionRequest request) {
        AcademicYear year = academicYearService.getEntity(request.academicYearId());
        if (divisionRepository.existsByAcademicYearIdAndCode(year.getId(), request.code())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Division code already exists for this academic year: " + request.code());
        }
        return DivisionResponse.from(divisionRepository.save(new Division(year, request.code(), request.strength())));
    }

    @Transactional
    public DivisionResponse update(Long id, UpdateDivisionRequest request) {
        Division division = getEntity(id);
        division.update(request.strength(), request.active());
        return DivisionResponse.from(division);
    }
}
