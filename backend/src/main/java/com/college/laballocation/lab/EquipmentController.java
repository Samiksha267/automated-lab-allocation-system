package com.college.laballocation.lab;

import com.college.laballocation.lab.EquipmentDtos.CreateEquipmentRequest;
import com.college.laballocation.lab.EquipmentDtos.EquipmentResponse;
import com.college.laballocation.lab.EquipmentDtos.UpdateEquipmentRequest;
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
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public List<EquipmentResponse> list() {
        return equipmentService.list();
    }

    @GetMapping("/{id}")
    public EquipmentResponse get(@PathVariable Long id) {
        return equipmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public EquipmentResponse create(@Valid @RequestBody CreateEquipmentRequest request) {
        return equipmentService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEquipmentRequest request) {
        return equipmentService.update(id, request);
    }
}
