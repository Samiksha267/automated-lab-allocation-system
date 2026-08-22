package com.college.laballocation.lab;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.EquipmentDtos.CreateEquipmentRequest;
import com.college.laballocation.lab.EquipmentDtos.EquipmentResponse;
import com.college.laballocation.lab.EquipmentDtos.UpdateEquipmentRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    public List<EquipmentResponse> list() {
        return equipmentRepository.findAll().stream().map(EquipmentResponse::from).toList();
    }

    public Equipment getEntity(Long id) {
        return equipmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EQUIPMENT_NOT_FOUND", "Equipment not found: " + id));
    }

    public EquipmentResponse get(Long id) {
        return EquipmentResponse.from(getEntity(id));
    }

    @Transactional
    public EquipmentResponse create(CreateEquipmentRequest request) {
        String normalizedCode = request.code().trim().toUpperCase();
        if (equipmentRepository.existsByCode(normalizedCode)) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Equipment code already exists: " + normalizedCode);
        }
        Equipment saved = equipmentRepository.save(new Equipment(normalizedCode, request.name(), request.description()));
        return EquipmentResponse.from(saved);
    }

    @Transactional
    public EquipmentResponse update(Long id, UpdateEquipmentRequest request) {
        Equipment equipment = getEntity(id);
        equipment.update(request.name(), request.description(), request.active());
        return EquipmentResponse.from(equipment);
    }
}
