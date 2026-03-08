package com.example.mygateway.ratelimiter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Rate Limiter with Fallback Support
 * <p>
 * Uses sliding window algorithm for distributed rate limiting
 * Supports auto fallback to Sentinel when Redis fails
 * 
 * @author leoli
 */
@Slf4j
@Component
@EnableScheduling
public class RedisRateLimiter {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * Redis availability status
     */
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    /**
     * Fallback timeout in milliseconds
     */
    private long fallbackTimeoutMs = 5000;

    /**
     * Last fallback timestamp
     */
    private volatile long lastFallbackTime = 0;

    /**
     * Redis health check interval in milliseconds
     */
    private long healthCheckIntervalMs = 10000;

    /**
     * Lua script for sliding window rate limiting
     */
    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
                        
            -- Remove expired entries
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
                        
            -- Count current requests
            local count = redis.call('ZCARD', key)
                        
            if count < limit then
                -- Add current request
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
                return 1
            end
                        
            return 0
            """;

    private DefaultRedisScript<Long> rateLimitScript;

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptText(RATE_LIMIT_SCRIPT);
        rateLimitScript.setResultType(Long.class);

        // Check Redis availability on startup
        checkRedisAvailability();
    }

    /**
     * Scheduled health check: ping Redis every 10 seconds
     */
    @Scheduled(fixedDelayString = "${spring.redis.ratelimiter.health-check-interval:10000}")
    public void scheduledHealthCheck() {
        checkRedisAvailability();
    }

    /**
     * Check Redis availability with sync ping
     */
    public void checkRedisAvailability() {
        if (Objects.isNull(redisTemplate)) {
            redisAvailable.set(false);
            log.warn("RedisTemplate is null, Redis rate limiting disabled");
            return;
        }

        try {
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(result)) {
                if (!redisAvailable.get()) {
                    log.info("Redis connection recovered, global rate limiting enabled");
                }
                redisAvailable.set(true);
            } else {
                redisAvailable.set(false);
                log.warn("Redis ping unexpected response: {}", result);
            }
        } catch (Exception e) {
            if (redisAvailable.get()) {
                log.warn("Redis connection lost: {}, will fallback to Sentinel", e.getMessage());
            }
            redisAvailable.set(false);
            // Trigger fallback
            lastFallbackTime = System.currentTimeMillis();
        }
    }

    /**
     * Check if Redis is currently available
     */
    public boolean isRedisAvailable() {
        if (Objects.isNull(redisTemplate)) {
            return false;
        }

        // Check fallback timeout
        if (System.currentTimeMillis() - lastFallbackTime < fallbackTimeoutMs) {
            return false;
        }

        return redisAvailable.get();
    }

    /**
     * Mark to fallback to Sentinel
     */
    public void triggerFallback() {
        lastFallbackTime = System.currentTimeMillis();
        redisAvailable.set(false);
    }

    /**
     * Manually recover Redis connection
     */
    public void recoverRedis() {
        checkRedisAvailability();
        lastFallbackTime = 0;
    }

    /**
     * Try to acquire a token from Redis rate limiter
     */
    public RateLimitResult tryAcquire(String key, int qps, int burstCapacity) {
        if (!isRedisAvailable()) {
            return RateLimitResult.fallback("Redis unavailable");
        }

        if (redisTemplate == null) {
            triggerFallback();
            return RateLimitResult.fallback("RedisTemplate is null");
        }

        try {
            long now = System.currentTimeMillis();
            long windowSize = 1000;

            List<String> keys = Collections.singletonList(key);
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    keys,
                    String.valueOf(qps),
                    String.valueOf(windowSize),
                    String.valueOf(now)
            );

            if (result != null && result == 1L) {
                return RateLimitResult.allow();
            } else {
                return RateLimitResult.reject("Rate limit exceeded");
            }
        } catch (Exception e) {
            log.error("Redis rate limit error: {}", e.getMessage());
            triggerFallback();
            return RateLimitResult.fallback("Redis error: " + e.getMessage());
        }
    }

    public void setFallbackTimeoutMs(long timeoutMs) {
        this.fallbackTimeoutMs = timeoutMs;
    }

    /**
     * Rate limit result holder
     * 
     * @author leoli
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final boolean fallback;
        private final String message;

        private RateLimitResult(boolean allowed, boolean fallback, String message) {
            this.allowed = allowed;
            this.fallback = fallback;
            this.message = message;
        }

        public static RateLimitResult allow() {
            return new RateLimitResult(true, false, "Allowed");
        }

        public static RateLimitResult reject(String message) {
            return new RateLimitResult(false, false, message);
        }

        public static RateLimitResult fallback(String message) {
            return new RateLimitResult(false, true, message);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean isFallback() {
            return fallback;
        }

        public String getMessage() {
            return message;
        }
    }
}