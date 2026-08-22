package com.college.laballocation.lab;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.LabTypeDtos.CreateLabTypeRequest;
import com.college.laballocation.lab.LabTypeDtos.LabTypeResponse;
import com.college.laballocation.lab.LabTypeDtos.UpdateLabTypeRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LabTypeService {

    private final LabTypeRepository labTypeRepository;

    public LabTypeService(LabTypeRepository labTypeRepository) {
        this.labTypeRepository = labTypeRepository;
    }

    public List<LabTypeResponse> list() {
        return labTypeRepository.findAll().stream().map(LabTypeResponse::from).toList();
    }

    public LabType getEntity(Long id) {
        return labTypeRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LAB_TYPE_NOT_FOUND", "Lab type not found: " + id));
    }

    public LabTypeResponse get(Long id) {
        return LabTypeResponse.from(getEntity(id));
    }

    @Transactional
    public LabTypeResponse create(CreateLabTypeRequest request) {
        if (labTypeRepository.existsByCode(request.code())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Lab type code already exists: " + request.code());
        }
        LabType saved = labTypeRepository.save(new LabType(request.code(), request.name(), request.description()));
        return LabTypeResponse.from(saved);
    }

    @Transactional
    public LabTypeResponse update(Long id, UpdateLabTypeRequest request) {
        LabType labType = getEntity(id);
        labType.update(request.name(), request.description(), request.active());
        return LabTypeResponse.from(labType);
    }
}
