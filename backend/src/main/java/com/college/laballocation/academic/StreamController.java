package com.college.laballocation.academic;

import com.college.laballocation.academic.StreamDtos.CreateStreamRequest;
import com.college.laballocation.academic.StreamDtos.StreamResponse;
import com.college.laballocation.academic.StreamDtos.UpdateStreamRequest;
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
@RequestMapping("/api/streams")
public class StreamController {

    private final StreamService streamService;

    public StreamController(StreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping
    public List<StreamResponse> listByProgram(@RequestParam Long programId) {
        return streamService.listByProgram(programId);
    }

    @GetMapping("/{id}")
    public StreamResponse get(@PathVariable Long id) {
        return streamService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public StreamResponse create(@Valid @RequestBody CreateStreamRequest request) {
        return streamService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public StreamResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStreamRequest request) {
        return streamService.update(id, request);
    }
}
