package com.college.laballocation.academic;

import com.college.laballocation.academic.BatchDtos.BatchResponse;
import com.college.laballocation.academic.BatchDtos.CreateBatchRequest;
import com.college.laballocation.academic.BatchDtos.UpdateBatchRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BatchService {

    private final BatchRepository batchRepository;
    private final DivisionService divisionService;

    public BatchService(BatchRepository batchRepository, DivisionService divisionService) {
        this.batchRepository = batchRepository;
        this.divisionService = divisionService;
    }

    public List<BatchResponse> listByDivision(Long divisionId) {
        return batchRepository.findByDivisionId(divisionId).stream().map(BatchResponse::from).toList();
    }

    public Batch getEntity(Long id) {
        return batchRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BATCH_NOT_FOUND", "Batch not found: " + id));
    }

    public BatchResponse get(Long id) {
        return BatchResponse.from(getEntity(id));
    }

    @Transactional
    public BatchResponse create(CreateBatchRequest request) {
        Division division = divisionService.getEntity(request.divisionId());
        if (batchRepository.existsByDivisionIdAndCode(division.getId(), request.code())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Batch code already exists for this division: " + request.code());
        }
        return BatchResponse.from(batchRepository.save(new Batch(division, request.code(), request.strength())));
    }

    @Transactional
    public BatchResponse update(Long id, UpdateBatchRequest request) {
        Batch batch = getEntity(id);
        batch.update(request.strength(), request.active());
        return BatchResponse.from(batch);
    }
}
