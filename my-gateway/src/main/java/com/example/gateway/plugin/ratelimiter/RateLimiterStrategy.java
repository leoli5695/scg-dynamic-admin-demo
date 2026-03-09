package com.example.gateway.plugin.ratelimiter;

import com.example.gateway.plugin.AbstractPlugin;
import com.example.gateway.plugin.PluginType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter strategy implementation.
 * Uses Redis sliding window algorithm for distributed rate limiting.
 */
@Slf4j
@Component
public class RateLimiterStrategy extends AbstractPlugin {
    
    private int defaultQps = 100;
    private int windowSize = 60; // 60 seconds
    
   private final RedisTemplate<String, String> redisTemplate;
    
    public RateLimiterStrategy(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public PluginType getType() {
      return PluginType.RATE_LIMITER;
    }
    
    @Override
    public void apply(Map<String, Object> context) {
        if (!isEnabled()) {
            log.trace("Rate limiter strategy disabled, skipping");
          return;
        }
        
        String routeId = (String) context.get("routeId");
        String clientId = (String) context.get("clientId");
        
        if (routeId == null) {
            log.warn("No routeId in context, cannot apply rate limiter");
          return;
        }
        
        // Build rate limit key
        String key = buildRateLimitKey(routeId, clientId);
        
        // Check if request should be limited
        boolean allowed = tryAcquire(key);
        context.put("rateLimitAllowed", allowed);
        
        if (!allowed) {
            log.warn("Rate limit exceeded for route {}, client {}", routeId, clientId);
        }
        
        log.debug("Rate limiter check: route={}, allowed={}", routeId, allowed);
    }
    
    @Override
    public void refresh(Object config) {
        super.refresh(config);
        if (config instanceof Map) {
            Object qpsObj = ((Map<?, ?>) config).get("defaultQps");
            Object windowObj = ((Map<?, ?>) config).get("windowSize");
            
            if (qpsObj != null) {
                this.defaultQps = Integer.parseInt(qpsObj.toString());
            }
            if (windowObj != null) {
                this.windowSize = Integer.parseInt(windowObj.toString());
            }
            
            log.info("Rate limiter config updated: QPS={}, Window={}s", defaultQps, windowSize);
        }
    }
    
    /**
     * Try to acquire permission using Redis sliding window.
     */
    private boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSize * 1000L);
        
        // Remove expired entries
       redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        
        // Count current requests in window
        Long count = redisTemplate.opsForZSet().zCard(key);
        
        if (count != null && count >= (long) defaultQps * windowSize) {
          return false; // Rate limit exceeded
        }
        
        // Add current request
       redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
       redisTemplate.expire(key, windowSize +1, TimeUnit.SECONDS);
        
     return true;
    }
    
    /**
     * Build rate limit cache key.
     */
    private String buildRateLimitKey(String routeId, String clientId) {
        if (clientId != null && !clientId.isEmpty()) {
          return "rate_limit:" + routeId + ":" + clientId;
        }
      return "rate_limit:" + routeId;
    }
}
