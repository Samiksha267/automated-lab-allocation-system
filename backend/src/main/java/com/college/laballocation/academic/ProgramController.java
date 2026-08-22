package com.college.laballocation.academic;

import com.college.laballocation.academic.ProgramDtos.CreateProgramRequest;
import com.college.laballocation.academic.ProgramDtos.ProgramResponse;
import com.college.laballocation.academic.ProgramDtos.UpdateProgramRequest;
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

/** Read: any authenticated user. Write: LAB_ASSISTANT only (docs/09-AUTHORIZATION-RBAC.md). */
@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping
    public List<ProgramResponse> list() {
        return programService.list();
    }

    @GetMapping("/{id}")
    public ProgramResponse get(@PathVariable Long id) {
        return programService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ProgramResponse create(@Valid @RequestBody CreateProgramRequest request) {
        return programService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ProgramResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProgramRequest request) {
        return programService.update(id, request);
    }
}
