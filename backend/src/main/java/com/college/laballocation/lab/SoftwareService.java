package com.college.laballocation.lab;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.SoftwareDtos.CreateSoftwareRequest;
import com.college.laballocation.lab.SoftwareDtos.SoftwareResponse;
import com.college.laballocation.lab.SoftwareDtos.UpdateSoftwareRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SoftwareService {

    private final SoftwareRepository softwareRepository;

    public SoftwareService(SoftwareRepository softwareRepository) {
        this.softwareRepository = softwareRepository;
    }

    public List<SoftwareResponse> list() {
        return softwareRepository.findAll().stream().map(SoftwareResponse::from).toList();
    }

    public Software getEntity(Long id) {
        return softwareRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SOFTWARE_NOT_FOUND", "Software not found: " + id));
    }

    public SoftwareResponse get(Long id) {
        return SoftwareResponse.from(getEntity(id));
    }

    @Transactional
    public SoftwareResponse create(CreateSoftwareRequest request) {
        String normalizedCode = request.code().trim().toUpperCase();
        if (softwareRepository.existsByCode(normalizedCode)) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Software code already exists: " + normalizedCode);
        }
        Software saved = softwareRepository.save(new Software(normalizedCode, request.name()));
        return SoftwareResponse.from(saved);
    }

    @Transactional
    public SoftwareResponse update(Long id, UpdateSoftwareRequest request) {
        Software software = getEntity(id);
        software.update(request.name(), request.active());
        return SoftwareResponse.from(software);
    }
}
