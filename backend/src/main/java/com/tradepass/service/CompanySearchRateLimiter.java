package com.tradepass.service;

import com.tradepass.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 单实例应用层搜索频控；多实例部署时仍应在网关或共享存储层增加全局频控。 */
@Component
public class CompanySearchRateLimiter {
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final int maxRequestsPerMinute;
    private final ConcurrentMap<Long, Window> windows = new ConcurrentHashMap<>();

    public CompanySearchRateLimiter(
            @Value("${tradepass.security.company-search.max-requests-per-minute:30}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    public void check(long userId) {
        long now = System.nanoTime();
        windows.compute(userId, (ignored, current) -> {
            if (current == null || now - current.startedAtNanos() >= WINDOW_NANOS) {
                return new Window(now, 1);
            }
            if (current.requestCount() >= maxRequestsPerMinute) {
                throw new BusinessException("企业搜索过于频繁，请稍后再试");
            }
            return new Window(current.startedAtNanos(), current.requestCount() + 1);
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtNanos() >= WINDOW_NANOS);
        }
    }

    private record Window(long startedAtNanos, int requestCount) {
    }
}
