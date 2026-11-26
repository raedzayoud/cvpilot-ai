package com.backend.backend.integration.ai;

import com.backend.backend.config.AppProperties;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Server-wide daily cap on AI calls, independent of the per-client rate limit, so a misbehaving
 * client or a script cannot burn through the provider quota. In-memory: resets on restart and is
 * per instance; use a shared store if the API is scaled horizontally.
 */
@Component
public class AiUsageGuard {

    private final int dailyLimit;
    private LocalDate day = LocalDate.now(ZoneOffset.UTC);
    private int used;

    public AiUsageGuard(AppProperties properties) {
        this.dailyLimit = properties.ai().dailyRequestLimit();
    }

    public synchronized void acquire() {
        rollOver();
        if (used >= dailyLimit) {
            throw new AiException(AiException.Kind.BUDGET_EXHAUSTED, "Daily AI request limit of " + dailyLimit + " reached");
        }
        used++;
    }

    public synchronized int remaining() {
        rollOver();
        return Math.max(0, dailyLimit - used);
    }

    private void rollOver() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
    }
}
