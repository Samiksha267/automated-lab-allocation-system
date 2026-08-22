package com.college.laballocation.academic;

import com.college.laballocation.academic.StreamDtos.CreateStreamRequest;
import com.college.laballocation.academic.StreamDtos.StreamResponse;
import com.college.laballocation.academic.StreamDtos.UpdateStreamRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class-level {@code @Transactional(readOnly = true)}: response DTOs
 * dereference lazy associations (e.g. {@code stream.getProgram()}) - with
 * {@code spring.jpa.open-in-view: false} (deliberate, see docs/15-DESIGN-DECISIONS.md),
 * those must be initialized before the Hibernate session closes, not in the
 * controller after the service call returns. Mutating methods override with
 * their own {@code @Transactional} for read-write.
 */
@Service
@Transactional(readOnly = true)
public class StreamService {

    private final StreamRepository streamRepository;
    private final ProgramService programService;

    public StreamService(StreamRepository streamRepository, ProgramService programService) {
        this.streamRepository = streamRepository;
        this.programService = programService;
    }

    public List<StreamResponse> listByProgram(Long programId) {
        return streamRepository.findByProgramId(programId).stream().map(StreamResponse::from).toList();
    }

    Stream getEntity(Long id) {
        return streamRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("STREAM_NOT_FOUND", "Stream not found: " + id));
    }

    public StreamResponse get(Long id) {
        return StreamResponse.from(getEntity(id));
    }

    @Transactional
    public StreamResponse create(CreateStreamRequest request) {
        Program program = programService.getEntity(request.programId());
        if (streamRepository.existsByProgramIdAndCode(program.getId(), request.code())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Stream code already exists for this program: " + request.code());
        }
        return StreamResponse.from(streamRepository.save(new Stream(program, request.code(), request.name())));
    }

    @Transactional
    public StreamResponse update(Long id, UpdateStreamRequest request) {
        Stream stream = getEntity(id);
        stream.update(request.name(), request.active());
        return StreamResponse.from(stream);
    }
}
