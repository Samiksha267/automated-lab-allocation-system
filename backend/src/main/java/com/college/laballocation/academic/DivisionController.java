package com.college.laballocation.academic;

import com.college.laballocation.academic.DivisionDtos.CreateDivisionRequest;
import com.college.laballocation.academic.DivisionDtos.DivisionResponse;
import com.college.laballocation.academic.DivisionDtos.UpdateDivisionRequest;
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
@RequestMapping("/api/divisions")
public class DivisionController {

    private final DivisionService divisionService;

    public DivisionController(DivisionService divisionService) {
        this.divisionService = divisionService;
    }

    @GetMapping
    public List<DivisionResponse> listByAcademicYear(@RequestParam Long academicYearId) {
        return divisionService.listByAcademicYear(academicYearId);
    }

    @GetMapping("/{id}")
    public DivisionResponse get(@PathVariable Long id) {
        return divisionService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public DivisionResponse create(@Valid @RequestBody CreateDivisionRequest request) {
        return divisionService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public DivisionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDivisionRequest request) {
        return divisionService.update(id, request);
    }
}
