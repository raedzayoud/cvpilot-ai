package com.backend.backend.controller;

import com.backend.backend.dto.DashboardDto;
import com.backend.backend.dto.SystemStatusResponse;
import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiUsageGuard;
import com.backend.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Overview statistics and system status")
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboard;
    private final AiChatClient ai;
    private final AiUsageGuard usage;

    public DashboardController(DashboardService dashboard, AiChatClient ai, AiUsageGuard usage) {
        this.dashboard = dashboard;
        this.ai = ai;
        this.usage = usage;
    }

    @Operation(summary = "Counts, average score, recent jobs, recent customizations and activity")
    @GetMapping("/dashboard")
    public DashboardDto.Response get() {
        return dashboard.get();
    }

    @Operation(summary = "Whether AI features are available (never exposes the key)")
    @GetMapping("/system/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(ai.isConfigured(), ai.providerName(), ai.model(), usage.remaining());
    }
}
