package com.example.gatewayadmin.model;

import lombok.Data;

/**
 * Rate Limiter Configuration
 * 
 * @author leoli
 */
@Data
public class RateLimiterConfig {
    
    private String routeId;
    private boolean enabled = true;
    
    // Redis global rate limiting
    private int redisQps = 100;
    private int redisBurstCapacity = 200;
    private String keyPrefix = "rate_limit:";
    private String keyType = "combined";
    
    // Sentinel local rate limiting
    private int sentinelQps = 50;
    private String sentinelThresholdType = "QPS";
    private String sentinelControlStrategy = "reject";
    
    // Fallback configuration
    private boolean fallbackToSentinel = true;
    private long redisFallbackTimeoutMs = 5000;
}