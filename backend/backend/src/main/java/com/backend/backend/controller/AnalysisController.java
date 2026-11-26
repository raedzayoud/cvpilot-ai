package com.backend.backend.controller;

import com.backend.backend.dto.AnalysisDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analyses", description = "CV customizations: plan, sections, AI chat and export")
@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @Operation(summary = "List customizations", description = "search matches job title or CV file name; status = all|exported|draft; sort = createdAt|jobTitle|score")
    @GetMapping
    public PageResponse<AnalysisDto.Summary> list(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return service.list(search, status, page, size, sort);
    }

    @Operation(summary = "Start a customization of a CV for a job")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisDto.Response create(@Valid @RequestBody AnalysisDto.CreateRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Get a customization")
    @GetMapping("/{id}")
    public AnalysisDto.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Create a fresh customization from the same CV and job")
    @PostMapping("/{id}/regenerate")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisDto.Response regenerate(@PathVariable UUID id) {
        return service.regenerate(id);
    }

    @Operation(summary = "Delete a customization and its chat history")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Get proposed sections and the keep/highlight/remove plan")
    @GetMapping("/{id}/customization")
    public AnalysisDto.CustomizationResponse customization(@PathVariable UUID id) {
        return service.customization(id);
    }

    @Operation(summary = "Get the conversation, customization and match insights")
    @GetMapping("/{id}/chat")
    public AnalysisDto.ChatTurnResponse chat(@PathVariable UUID id) {
        return service.chat(id);
    }

    @Operation(summary = "Send a message to the AI coach", description = "Rate limited.")
    @PostMapping("/{id}/chat")
    public AnalysisDto.ChatTurnResponse sendChat(@PathVariable UUID id, @Valid @RequestBody AnalysisDto.ChatRequest request) {
        return service.sendChat(id, request.text());
    }

    @Operation(summary = "Save and check a section", description = "Approved only if it names nothing absent from the CV.")
    @PostMapping("/{id}/sections/{key}")
    public AnalysisDto.CustomizationResponse approveSection(
            @PathVariable UUID id,
            @PathVariable @Pattern(regexp = "[a-z]{1,30}", message = "Unknown section.") String key,
            @Valid @RequestBody AnalysisDto.SectionRequest request) {
        return service.approveSection(id, key, request.text());
    }

    @Operation(summary = "Change the action for one plan item")
    @PostMapping("/{id}/decisions/{decisionId}")
    public AnalysisDto.CustomizationResponse setDecision(
            @PathVariable UUID id,
            @PathVariable @Pattern(regexp = "[\\w:.-]{1,80}", message = "Unknown suggestion.") String decisionId,
            @Valid @RequestBody AnalysisDto.DecisionRequest request) {
        return service.setDecision(id, decisionId, request.action());
    }

    @Operation(summary = "Download the approved CV", description = "docx or pdf; LaTeX CVs export as tex.")
    @PostMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @Valid @RequestBody AnalysisDto.ExportRequest request) {
        AnalysisService.ExportResult file = service.export(id, request.format());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
