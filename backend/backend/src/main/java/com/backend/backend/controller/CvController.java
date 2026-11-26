package com.backend.backend.controller;

import com.backend.backend.dto.CvDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.model.CvProfile;
import com.backend.backend.service.CvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "CVs", description = "Upload, read, edit and delete CVs")
@RestController
@RequestMapping("/api/cvs")
public class CvController {

    private final CvService service;

    public CvController(CvService service) {
        this.service = service;
    }

    @Operation(summary = "List CVs", description = "Paginated; search matches file name or language. Sort: updatedAt, createdAt, fileName, sizeBytes.")
    @GetMapping
    public PageResponse<CvDto.Summary> list(@RequestParam(required = false) String search,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String sort) {
        return service.list(search, page, size, sort);
    }

    @Operation(summary = "Upload a CV file", description = "PDF, DOCX or .tex up to 10 MB. The profile is extracted with AI. Rate limited.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CvDto.Response upload(@RequestPart("file") MultipartFile file) {
        return service.upload(file);
    }

    @Operation(summary = "Add a LaTeX CV from pasted source", description = "Rate limited (AI extraction).")
    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public CvDto.Response uploadText(@Valid @RequestBody CvDto.TextUpload input) {
        return service.uploadLatex(input);
    }

    @Operation(summary = "Get a CV with its extracted profile")
    @GetMapping("/{id}")
    public CvDto.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Replace the extracted profile")
    @PutMapping("/{id}/profile")
    public CvDto.Response updateProfile(@PathVariable UUID id, @Valid @RequestBody CvProfile profile) {
        return service.updateProfile(id, profile);
    }

    @Operation(summary = "Delete a CV", description = "Also deletes its jobs, customizations and chat history.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Download the original uploaded file, byte for byte")
    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable UUID id) {
        CvService.OriginalFile file = service.original(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(),
                        java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
