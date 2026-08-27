package com.speedvagas;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple per-service minimum-interval limiter to avoid aggressive external API use. */
public final class ExternalRateLimiter {
    private ExternalRateLimiter() {}
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    public static void acquire(String service, Duration minInterval) throws InterruptedException {
        long wait;
        synchronized (LAST) {
            long now = System.currentTimeMillis();
            long last = LAST.getOrDefault(service, 0L);
            wait = Math.max(0L, minInterval.toMillis() - (now - last));
            LAST.put(service, now + wait);
        }
        if (wait > 0) Thread.sleep(wait);
    }
}
