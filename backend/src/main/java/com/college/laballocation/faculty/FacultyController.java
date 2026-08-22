package com.college.laballocation.faculty;

import com.college.laballocation.faculty.FacultyDtos.CreateFacultyRequest;
import com.college.laballocation.faculty.FacultyDtos.FacultyResponse;
import com.college.laballocation.faculty.FacultyDtos.UpdateFacultyRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faculty")
public class FacultyController {

    private final FacultyService facultyService;

    public FacultyController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @GetMapping
    public List<FacultyResponse> list() {
        return facultyService.list();
    }

    @GetMapping("/{id}")
    public FacultyResponse get(@PathVariable Long id) {
        return facultyService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public FacultyResponse create(@Valid @RequestBody CreateFacultyRequest request) {
        return facultyService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public FacultyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateFacultyRequest request) {
        return facultyService.update(id, request);
    }
}
