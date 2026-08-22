package com.college.laballocation.faculty;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.faculty.FacultyDtos.CreateFacultyRequest;
import com.college.laballocation.faculty.FacultyDtos.FacultyResponse;
import com.college.laballocation.faculty.FacultyDtos.UpdateFacultyRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FacultyService {

    private final FacultyRepository facultyRepository;

    public FacultyService(FacultyRepository facultyRepository) {
        this.facultyRepository = facultyRepository;
    }

    public List<FacultyResponse> list() {
        return facultyRepository.findAll().stream().map(FacultyResponse::from).toList();
    }

    public Faculty getEntity(Long id) {
        return facultyRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FACULTY_NOT_FOUND", "Faculty not found: " + id));
    }

    public FacultyResponse get(Long id) {
        return FacultyResponse.from(getEntity(id));
    }

    @Transactional
    public FacultyResponse create(CreateFacultyRequest request) {
        if (facultyRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Faculty employee code already exists: " + request.employeeCode());
        }
        Faculty saved = facultyRepository.save(
                new Faculty(request.employeeCode(), request.name(), request.email(), request.department()));
        return FacultyResponse.from(saved);
    }

    @Transactional
    public FacultyResponse update(Long id, UpdateFacultyRequest request) {
        Faculty faculty = getEntity(id);
        faculty.update(request.name(), request.email(), request.department(), request.active());
        return FacultyResponse.from(faculty);
    }
}
