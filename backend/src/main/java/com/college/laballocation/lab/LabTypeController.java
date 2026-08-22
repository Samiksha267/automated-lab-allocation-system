package com.college.laballocation.lab;

import com.college.laballocation.lab.LabTypeDtos.CreateLabTypeRequest;
import com.college.laballocation.lab.LabTypeDtos.LabTypeResponse;
import com.college.laballocation.lab.LabTypeDtos.UpdateLabTypeRequest;
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
@RequestMapping("/api/lab-types")
public class LabTypeController {

    private final LabTypeService labTypeService;

    public LabTypeController(LabTypeService labTypeService) {
        this.labTypeService = labTypeService;
    }

    @GetMapping
    public List<LabTypeResponse> list() {
        return labTypeService.list();
    }

    @GetMapping("/{id}")
    public LabTypeResponse get(@PathVariable Long id) {
        return labTypeService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public LabTypeResponse create(@Valid @RequestBody CreateLabTypeRequest request) {
        return labTypeService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public LabTypeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLabTypeRequest request) {
        return labTypeService.update(id, request);
    }
}
