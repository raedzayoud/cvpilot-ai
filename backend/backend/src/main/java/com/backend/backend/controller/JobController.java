package com.backend.backend.controller;

import com.backend.backend.dto.JobDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Jobs", description = "Job descriptions, parsed into positions and requirements")
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @Operation(summary = "Add a pasted job description", description = "Parsed with AI (keyword fallback). Rate limited.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobDto.Response create(@Valid @RequestBody JobDto.CreateRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Add a job description from a PDF or DOCX file", description = "Rate limited.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobDto.Response upload(@RequestParam UUID cvId, @RequestPart("file") MultipartFile file) {
        return service.upload(cvId, file);
    }

    @Operation(summary = "Get a job description")
    @GetMapping("/{id}")
    public JobDto.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "List job descriptions added for a CV")
    @GetMapping
    public PageResponse<JobDto.Response> list(@RequestParam UUID cvId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.listForCv(cvId, page, size);
    }
}
