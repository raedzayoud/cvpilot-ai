package com.backend.backend.util;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter keyed by client. Good for a single instance; behind several
 * instances, move this to Redis or the API gateway.
 */
public class SlidingWindowRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int limit;
    private final long windowNanos;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.windowNanos = window.toNanos();
    }

    /** Returns 0 when allowed, otherwise the seconds to wait before the next request is allowed. */
    public long tryAcquire(String key) {
        long now = System.nanoTime();
        if (hits.size() > MAX_TRACKED_CLIENTS) evictIdle(now);
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowNanos) window.pollFirst();
            if (window.size() >= limit) {
                long waitNanos = windowNanos - (now - window.peekFirst());
                return Math.max(1, Duration.ofNanos(waitNanos).toSeconds());
            }
            window.addLast(now);
            return 0;
        }
    }

    private void evictIdle(long now) {
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || now - last >= windowNanos;
            }
        });
    }
}
