package com.backend.backend.controller;

import com.backend.backend.dto.AgentDto;
import com.backend.backend.dto.PageResponse;
import com.backend.backend.service.agent.AgentSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "AI CV Agent", description = "Conversational CV editing with explainable, approvable proposals")
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentSessionService service;

    public AgentController(AgentSessionService service) {
        this.service = service;
    }

    @Operation(summary = "List agent sessions (optionally for one CV)")
    @GetMapping("/sessions")
    public PageResponse<AgentDto.SessionSummary> list(@RequestParam(required = false) UUID cvId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(cvId, page, size);
    }

    @Operation(summary = "Start a session on a working copy of a CV")
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDto.Workspace create(@Valid @RequestBody AgentDto.CreateSession request) {
        return service.create(request.cvId());
    }

    @Operation(summary = "Get the full workspace: conversation, proposals, working CV")
    @GetMapping("/sessions/{id}")
    public AgentDto.Workspace get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Delete a session (the CV is not affected)")
    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Send an instruction to the agent", description = "Runs the AI agent. Rate limited.")
    @PostMapping("/sessions/{id}/messages")
    public AgentDto.Workspace send(@PathVariable UUID id, @Valid @RequestBody AgentDto.SendMessage request) {
        return service.send(id, request.text());
    }

    @Operation(summary = "Accept every pending change")
    @PostMapping("/sessions/{id}/accept-all")
    public AgentDto.Workspace acceptAll(@PathVariable UUID id) {
        return service.acceptAll(id);
    }

    @Operation(summary = "Write the working copy (accepted changes) into the CV")
    @PostMapping("/sessions/{id}/save")
    public AgentDto.Workspace save(@PathVariable UUID id) {
        return service.saveToCv(id);
    }

    @Operation(summary = "Discard all accepted changes of this session")
    @PostMapping("/sessions/{id}/reset")
    public AgentDto.Workspace reset(@PathVariable UUID id) {
        return service.reset(id);
    }

    @Operation(summary = "Download the working copy as PDF, LaTeX or Word")
    @PostMapping("/sessions/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @Valid @RequestBody AgentDto.ExportRequest request) {
        AgentSessionService.ExportResult file = service.export(id, request.format());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @Operation(summary = "Accept a proposed change, optionally with your own edit")
    @PostMapping("/proposals/{id}/accept")
    public AgentDto.Workspace accept(@PathVariable UUID id, @Valid @RequestBody(required = false) AgentDto.AcceptRequest edit) {
        return service.accept(id, edit);
    }

    @Operation(summary = "Reject a proposed change")
    @PostMapping("/proposals/{id}/reject")
    public AgentDto.Workspace reject(@PathVariable UUID id) {
        return service.reject(id);
    }

    @Operation(summary = "Ask the agent for a new version of a proposed change", description = "Runs the AI agent. Rate limited.")
    @PostMapping("/proposals/{id}/regenerate")
    public AgentDto.Workspace regenerate(@PathVariable UUID id) {
        return service.regenerate(id);
    }
}
