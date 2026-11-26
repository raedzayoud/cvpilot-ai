package com.backend.backend.service;

import com.backend.backend.dto.CvDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.entity.Cv;
import com.backend.backend.entity.CvFile;
import com.backend.backend.exception.ApiException;
import com.backend.backend.mapper.CvMapper;
import com.backend.backend.model.ActivityType;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSource;
import com.backend.backend.repository.CvFileRepository;
import com.backend.backend.repository.CvRepository;
import com.backend.backend.service.ai.CvExtractionService;
import com.backend.backend.util.DocumentTextExtractor;
import com.backend.backend.util.PageRequests;
import com.backend.backend.validation.UploadValidator;
import com.backend.backend.validation.UploadValidator.Kind;
import com.backend.backend.validation.UploadValidator.ValidatedUpload;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class CvService {

    private static final Map<String, String> SORTS = Map.of(
            "updatedAt", "updatedAt", "createdAt", "createdAt", "fileName", "fileName", "sizeBytes", "sizeBytes");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    public record OriginalFile(String fileName, String mimeType, byte[] content) {
    }

    private final CvRepository cvs;
    private final CvFileRepository files;
    private final CvMapper mapper;
    private final UploadValidator uploads;
    private final DocumentTextExtractor extractor;
    private final CvExtractionService extraction;
    private final ActivityService activity;
    private final TransactionTemplate tx;

    public CvService(CvRepository cvs, CvFileRepository files, CvMapper mapper, UploadValidator uploads,
                     DocumentTextExtractor extractor, CvExtractionService extraction, ActivityService activity,
                     TransactionTemplate tx) {
        this.cvs = cvs;
        this.files = files;
        this.mapper = mapper;
        this.uploads = uploads;
        this.extractor = extractor;
        this.extraction = extraction;
        this.activity = activity;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public PageResponse<CvDto.Summary> list(String search, int page, int size, String sort) {
        return PageResponse.of(
                cvs.search(PageRequests.likePattern(search), PageRequests.of(page, size, sort, SORTS, DEFAULT_SORT)),
                mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public CvDto.Response get(UUID id) {
        return mapper.toResponse(find(id));
    }

    /** Text extraction and the AI call run outside any transaction so no DB connection is held meanwhile. */
    public CvDto.Response upload(MultipartFile file) {
        ValidatedUpload upload = uploads.validate(file, EnumSet.allOf(Kind.class));
        String text = extractor.extract(upload.kind(), upload.bytes());
        return store(upload.fileName(), upload.kind(), upload.bytes(), text);
    }

    public CvDto.Response uploadLatex(CvDto.TextUpload input) {
        String raw = input.fileName() == null || input.fileName().isBlank() ? "CV.tex" : input.fileName().trim();
        String fileName = UploadValidator.sanitizeFileName(raw.toLowerCase().endsWith(".tex") ? raw : raw + ".tex");
        byte[] bytes = input.source().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > UploadValidator.MAX_BYTES) throw ApiException.badRequest("LaTeX source is larger than 10 MB.");
        return store(fileName, Kind.TEX, bytes, input.source().strip());
    }

    private CvDto.Response store(String fileName, Kind kind, byte[] bytes, String text) {
        CvExtractionService.Result result = extraction.extract(text);
        return tx.execute(status -> {
            Cv cv = new Cv();
            cv.setFileName(fileName);
            cv.setSourceFormat(sourceOf(kind));
            cv.setMimeType(mimeOf(kind));
            cv.setSizeBytes(bytes.length);
            cv.setLanguage(result.language());
            cv.setExtractionNote(result.note());
            mapper.setProfile(cv, result.profile());
            Cv saved = cvs.saveAndFlush(cv);
            files.save(new CvFile(saved.getId(), bytes, text));
            activity.log(ActivityType.CV_UPLOADED, "Uploaded " + fileName);
            log.info("Stored CV {} ({} bytes, {}, extraction {})", saved.getId(), bytes.length, kind,
                    result.note() == null ? "ai" : "fallback");
            return mapper.toResponse(saved);
        });
    }

    @Transactional
    public CvDto.Response updateProfile(UUID id, CvProfile profile) {
        Cv cv = find(id);
        mapper.setProfile(cv, profile.withItemIds());
        cv.setExtractionNote(null);
        activity.log(ActivityType.CV_UPDATED, "Updated profile for " + cv.getFileName());
        return mapper.toResponse(cvs.saveAndFlush(cv));
    }

    /** Jobs, analyses, chat history and the stored file are removed by ON DELETE CASCADE. */
    @Transactional
    public void delete(UUID id) {
        Cv cv = find(id);
        cvs.delete(cv);
        activity.log(ActivityType.CV_DELETED, "Deleted " + cv.getFileName());
    }

    @Transactional(readOnly = true)
    public OriginalFile original(UUID id) {
        Cv cv = find(id);
        CvFile file = files.findById(id).orElseThrow(() -> ApiException.notFound("The original file is no longer stored."));
        return new OriginalFile(cv.getFileName(), cv.getMimeType(), file.getContent());
    }

    @Transactional(readOnly = true)
    public Cv find(UUID id) {
        return cvs.findById(id).orElseThrow(() -> ApiException.notFound("CV not found."));
    }

    private static CvSource sourceOf(Kind kind) {
        return switch (kind) {
            case PDF -> CvSource.PDF;
            case DOCX -> CvSource.DOCX;
            case TEX -> CvSource.TEX;
        };
    }

    private static String mimeOf(Kind kind) {
        return switch (kind) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case TEX -> "application/x-tex";
        };
    }
}
