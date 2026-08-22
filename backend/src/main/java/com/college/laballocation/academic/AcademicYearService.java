package com.college.laballocation.academic;

import com.college.laballocation.academic.AcademicYearDtos.AcademicYearResponse;
import com.college.laballocation.academic.AcademicYearDtos.CreateAcademicYearRequest;
import com.college.laballocation.academic.AcademicYearDtos.UpdateAcademicYearRequest;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final StreamService streamService;

    public AcademicYearService(AcademicYearRepository academicYearRepository, StreamService streamService) {
        this.academicYearRepository = academicYearRepository;
        this.streamService = streamService;
    }

    public List<AcademicYearResponse> listByStream(Long streamId) {
        return academicYearRepository.findByStreamId(streamId).stream().map(AcademicYearResponse::from).toList();
    }

    AcademicYear getEntity(Long id) {
        return academicYearRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ACADEMIC_YEAR_NOT_FOUND", "Academic year not found: " + id));
    }

    public AcademicYearResponse get(Long id) {
        return AcademicYearResponse.from(getEntity(id));
    }

    @Transactional
    public AcademicYearResponse create(CreateAcademicYearRequest request) {
        Stream stream = streamService.getEntity(request.streamId());
        if (academicYearRepository.existsByStreamIdAndYearNumber(stream.getId(), request.yearNumber())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Year " + request.yearNumber() + " already exists for this stream");
        }
        return AcademicYearResponse.from(academicYearRepository.save(new AcademicYear(stream, request.yearNumber())));
    }

    @Transactional
    public AcademicYearResponse update(Long id, UpdateAcademicYearRequest request) {
        AcademicYear year = getEntity(id);
        year.update(request.active());
        return AcademicYearResponse.from(year);
    }
}
