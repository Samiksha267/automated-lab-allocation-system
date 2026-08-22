package com.college.laballocation.academic;

import com.college.laballocation.academic.BatchDtos.BatchResponse;
import com.college.laballocation.academic.BatchDtos.CreateBatchRequest;
import com.college.laballocation.academic.BatchDtos.UpdateBatchRequest;
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
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public List<BatchResponse> listByDivision(@RequestParam Long divisionId) {
        return batchService.listByDivision(divisionId);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable Long id) {
        return batchService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public BatchResponse create(@Valid @RequestBody CreateBatchRequest request) {
        return batchService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public BatchResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBatchRequest request) {
        return batchService.update(id, request);
    }
}
