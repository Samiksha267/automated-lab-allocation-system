package com.college.laballocation.academic;

import com.college.laballocation.academic.ProgramDtos.CreateProgramRequest;
import com.college.laballocation.academic.ProgramDtos.ProgramResponse;
import com.college.laballocation.academic.ProgramDtos.UpdateProgramRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProgramService {

    private final ProgramRepository programRepository;

    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    public List<ProgramResponse> list() {
        return programRepository.findAll().stream().map(ProgramResponse::from).toList();
    }

    Program getEntity(Long id) {
        return programRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PROGRAM_NOT_FOUND", "Program not found: " + id));
    }

    public ProgramResponse get(Long id) {
        return ProgramResponse.from(getEntity(id));
    }

    @Transactional
    public ProgramResponse create(CreateProgramRequest request) {
        if (programRepository.existsByCode(request.code())) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Program code already exists: " + request.code());
        }
        Program saved = programRepository.save(new Program(request.code(), request.name(), request.durationYears()));
        return ProgramResponse.from(saved);
    }

    @Transactional
    public ProgramResponse update(Long id, UpdateProgramRequest request) {
        Program program = getEntity(id);
        program.update(request.name(), request.durationYears(), request.active());
        return ProgramResponse.from(program);
    }
}
