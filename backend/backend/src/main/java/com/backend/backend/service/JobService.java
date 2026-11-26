package com.backend.backend.service;

import com.backend.backend.dto.JobDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.entity.Cv;
import com.backend.backend.entity.JobPosting;
import com.backend.backend.exception.ApiException;
import com.backend.backend.mapper.JobMapper;
import com.backend.backend.model.ActivityType;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.JobSource;
import com.backend.backend.repository.CvRepository;
import com.backend.backend.repository.JobPostingRepository;
import com.backend.backend.service.ai.JobParsingService;
import com.backend.backend.util.DocumentTextExtractor;
import com.backend.backend.util.PageRequests;
import com.backend.backend.validation.UploadValidator;
import com.backend.backend.validation.UploadValidator.Kind;
import com.backend.backend.validation.UploadValidator.ValidatedUpload;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobService {

    private static final int MIN_TEXT = 20;
    private static final int MAX_TEXT = 50_000;

    private final JobPostingRepository jobs;
    private final CvRepository cvs;
    private final JobMapper mapper;
    private final JobParsingService parser;
    private final UploadValidator uploads;
    private final DocumentTextExtractor extractor;
    private final ActivityService activity;
    private final TransactionTemplate tx;

    public JobService(JobPostingRepository jobs, CvRepository cvs, JobMapper mapper, JobParsingService parser,
                      UploadValidator uploads, DocumentTextExtractor extractor, ActivityService activity, TransactionTemplate tx) {
        this.jobs = jobs;
        this.cvs = cvs;
        this.mapper = mapper;
        this.parser = parser;
        this.uploads = uploads;
        this.extractor = extractor;
        this.activity = activity;
        this.tx = tx;
    }

    public JobDto.Response create(JobDto.CreateRequest request) {
        return store(request.cvId(), request.text().strip(), JobSource.PASTE, null);
    }

    public JobDto.Response upload(UUID cvId, MultipartFile file) {
        ValidatedUpload upload = uploads.validate(file, EnumSet.of(Kind.PDF, Kind.DOCX));
        String text = extractor.extract(upload.kind(), upload.bytes());
        if (text.length() < MIN_TEXT) throw ApiException.unprocessable("No readable text was found in that file.");
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        return store(cvId, text, upload.kind() == Kind.PDF ? JobSource.PDF : JobSource.DOCX, upload.fileName());
    }

    private JobDto.Response store(UUID cvId, String text, JobSource source, String fileName) {
        if (!cvs.existsById(cvId)) throw ApiException.notFound("CV not found.");
        List<JobPosition> positions = parser.parse(text);
        return tx.execute(status -> {
            Cv cv = cvs.findById(cvId).orElseThrow(() -> ApiException.notFound("CV not found."));
            JobPosting job = new JobPosting();
            job.setCv(cv);
            job.setRawText(text);
            job.setSource(source);
            job.setFileName(fileName);
            mapper.setPositions(job, positions);
            JobPosting saved = jobs.saveAndFlush(job);
            String suffix = positions.size() > 1 ? " (" + positions.size() + " positions)" : "";
            activity.log(ActivityType.JOB_CREATED, "Added job: " + positions.getFirst().title() + suffix);
            return mapper.toResponse(saved);
        });
    }

    @Transactional(readOnly = true)
    public JobDto.Response get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDto.Response> listForCv(UUID cvId, int page, int size) {
        return PageResponse.of(jobs.findByCvId(cvId, PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt"))),
                mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public JobPosting find(UUID id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job not found."));
    }
}
