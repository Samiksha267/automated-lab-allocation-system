package com.college.laballocation.lab;

import com.college.laballocation.lab.SoftwareDtos.CreateSoftwareRequest;
import com.college.laballocation.lab.SoftwareDtos.SoftwareResponse;
import com.college.laballocation.lab.SoftwareDtos.UpdateSoftwareRequest;
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
@RequestMapping("/api/software")
public class SoftwareController {

    private final SoftwareService softwareService;

    public SoftwareController(SoftwareService softwareService) {
        this.softwareService = softwareService;
    }

    @GetMapping
    public List<SoftwareResponse> list() {
        return softwareService.list();
    }

    @GetMapping("/{id}")
    public SoftwareResponse get(@PathVariable Long id) {
        return softwareService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public SoftwareResponse create(@Valid @RequestBody CreateSoftwareRequest request) {
        return softwareService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public SoftwareResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSoftwareRequest request) {
        return softwareService.update(id, request);
    }
}
