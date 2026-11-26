package com.backend.backend.mapper;

import com.backend.backend.dto.JobDto;
import com.backend.backend.entity.JobPosting;
import com.backend.backend.model.JobPosition;
import com.backend.backend.util.JsonCodec;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class JobMapper {

    private static final TypeReference<List<JobPosition>> POSITIONS = new TypeReference<>() {
    };

    private final JsonCodec json;

    public JobMapper(JsonCodec json) {
        this.json = json;
    }

    public List<JobPosition> positionsOf(JobPosting job) {
        return json.read(job.getPositions(), POSITIONS);
    }

    public void setPositions(JobPosting job, List<JobPosition> positions) {
        job.setPositions(json.write(positions));
    }

    public JobDto.Response toResponse(JobPosting job) {
        return new JobDto.Response(job.getId(), job.getCv().getId(), job.getRawText(), job.getSource(), job.getFileName(),
                job.getCreatedAt(), positionsOf(job));
    }
}
