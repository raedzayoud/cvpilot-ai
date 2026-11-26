package com.backend.backend.config;

import com.backend.backend.exception.RateLimitExceededException;
import com.backend.backend.util.SlidingWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-client limit on endpoints that call the AI provider. Keyed by remote address; when deployed
 * behind a reverse proxy set {@code server.forward-headers-strategy=native} so this sees the real client.
 */
@Slf4j
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private final SlidingWindowRateLimiter limiter;

    public AiRateLimitInterceptor(AppProperties properties) {
        this.limiter = new SlidingWindowRateLimiter(properties.rateLimit().aiRequests(), properties.rateLimit().aiWindow());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        long wait = limiter.tryAcquire(request.getRemoteAddr());
        if (wait > 0) {
            log.info("AI rate limit hit for a client on {}", request.getRequestURI());
            throw new RateLimitExceededException(
                    "Too many AI requests. Try again in " + wait + " second" + (wait == 1 ? "" : "s") + ".", wait);
        }
        return true;
    }
}
