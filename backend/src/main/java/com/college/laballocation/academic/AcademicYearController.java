package com.college.laballocation.academic;

import com.college.laballocation.academic.AcademicYearDtos.AcademicYearResponse;
import com.college.laballocation.academic.AcademicYearDtos.CreateAcademicYearRequest;
import com.college.laballocation.academic.AcademicYearDtos.UpdateAcademicYearRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic-years")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    public AcademicYearController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping
    public List<AcademicYearResponse> listByStream(@RequestParam Long streamId) {
        return academicYearService.listByStream(streamId);
    }

    @GetMapping("/{id}")
    public AcademicYearResponse get(@PathVariable Long id) {
        return academicYearService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public AcademicYearResponse create(@Valid @RequestBody CreateAcademicYearRequest request) {
        return academicYearService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public AcademicYearResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAcademicYearRequest request) {
        return academicYearService.update(id, request);
    }
}
