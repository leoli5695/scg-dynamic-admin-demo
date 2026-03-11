package com.example.gateway.cache;

import com.example.gateway.monitor.AlertService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Unified cache manager for all configuration types (routes, services, strategies).
 * Provides dual-cache mechanism with automatic fallback and retry logic.
 *
 * @param <T> Configuration type
 */
@Slf4j
@Component
public class GenericCacheManager<T> {

    private final ObjectMapper objectMapper = new ObjectMapper();
        
    @Autowired(required = false)
    private AlertService alertService;
        
    // Cache TTL: configurable, default 5 minutes
    @Value("${gateway.cache.ttl-ms:300000}")
    private long cacheTtlMs = 300000; // 5 minutes default
        
    // Per-type caches
    private final ConcurrentHashMap<String, Long> lastLoadTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<JsonNode>> primaryCaches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<JsonNode>> fallbackCaches = new ConcurrentHashMap<>();

    /**
     * Load and cache configuration.
     * Updates both primary and fallback caches.
     *
     * @param cacheKey Unique key for this configuration type (e.g., "routes", "services", "strategies")
     * @param config   JSON configuration string
     */
    public void loadConfig(String cacheKey, String config) {
        try {
            JsonNode root = objectMapper.readTree(config);

            // Update primary cache
            primaryCaches.computeIfAbsent(cacheKey, k -> new AtomicReference<>()).set(root);

            // Update fallback cache (for disaster recovery)
            fallbackCaches.computeIfAbsent(cacheKey, k -> new AtomicReference<>()).set(root);

            // Update load time
            lastLoadTimes.put(cacheKey, System.currentTimeMillis());

            log.info("Configuration loaded for {}: {}", cacheKey, summarizeConfig(root));
        } catch (Exception e) {
            log.error("Failed to load configuration for {}", cacheKey, e);
            throw new RuntimeException("Failed to parse config for " + cacheKey, e);
        }
    }

    /**
     * Get cached configuration.
     * Returns null if cache is empty or expired.
     *
     * @param cacheKey Configuration type key
     * @return Cached configuration, or null if not available
     */
    public JsonNode getCachedConfig(String cacheKey) {
        AtomicReference<JsonNode> cache = primaryCaches.get(cacheKey);
        return cache != null ? cache.get() : null;
    }

    /**
     * Get fallback configuration (last valid config).
     * Used when primary cache is expired and Nacos is unavailable.
     *
     * @param cacheKey Configuration type key
     * @return Last valid configuration, or null if never loaded
     */
    public JsonNode getFallbackConfig(String cacheKey) {
        AtomicReference<JsonNode> cache = fallbackCaches.get(cacheKey);
        return cache != null ? cache.get() : null;
    }

    /**
     * Check if cache is valid (not null and not expired).
     *
     * @param cacheKey Configuration type key
     * @return true if cache is loaded and not expired
     */
    public boolean isCacheValid(String cacheKey) {
        JsonNode config = getCachedConfig(cacheKey);
        if (config == null) {
            return false;
        }

        Long lastLoadTime = lastLoadTimes.get(cacheKey);
        if (lastLoadTime == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        return (now - lastLoadTime) < cacheTtlMs;
    }

    /**
     * Clear cache for a specific configuration type.
     *
     * @param cacheKey Configuration type key
     */
    public void clearCache(String cacheKey) {
        AtomicReference<JsonNode> primaryCache = primaryCaches.get(cacheKey);
        if (primaryCache != null) {
            primaryCache.set(null);
        }

        AtomicReference<JsonNode> fallbackCache = fallbackCaches.get(cacheKey);
        if (fallbackCache != null) {
            fallbackCache.set(null);
        }

        lastLoadTimes.remove(cacheKey);
        log.info("Cache cleared for {}", cacheKey);
    }

    /**
     * Execute a loader function with retry logic and fallback.
     * This is the core method that implements the retry + fallback pattern.
     *
     * @param cacheKey Configuration type key
     * @param loader   Function to load configuration from Nacos
     * @return Loaded or fallback configuration
     */
    public JsonNode getConfigWithFallback(String cacheKey, Function<String, String> loader) {
        // Try to get from primary cache first
        JsonNode cachedConfig = getCachedConfig(cacheKey);

        if (cachedConfig != null && isCacheValid(cacheKey)) {
            log.debug("Using valid cached config for {}", cacheKey);
            return cachedConfig;
        }

        log.warn("Cache expired or invalid for {}, attempting reload from Nacos", cacheKey);

        // Try to reload from Nacos with retry mechanism
        boolean reloaded = false;
        int maxRetries = 3;
        int retryCount = 0;

        while (!reloaded && retryCount < maxRetries) {
            try {
                retryCount++;
                log.info("Attempting to reload {} from Nacos (attempt {}/{})", 
                        cacheKey, retryCount, maxRetries);
                        
                String config = loader.apply(cacheKey);
                        
                if (config != null && !config.isBlank()) {
                    // Nacos 正常返回配置 → 加载并更新缓存
                    loadConfig(cacheKey, config);
                    cachedConfig = getCachedConfig(cacheKey);
                    reloaded = true;
                    log.info("Successfully reloaded {} from Nacos", cacheKey);
                } else {
                    // Nacos 正常返回空 → 说明配置被删除了，直接清空缓存，不再重试
                    log.warn("Nacos returned empty config for {}, clearing cache", cacheKey);
                    clearCache(cacheKey);
                    reloaded = true; // Mark as reloaded (but cache is cleared)
                    cachedConfig = null;
                    
                    // Send alert when using fallback cache
                    if (alertService != null) {
                        alertService.sendWarn("Configuration deleted in Nacos for " + cacheKey + ", cache cleared");
                    }
                }
            } catch (Exception e) {
                // Nacos 网络故障/异常 → 重试，不更新缓存
                log.error("Failed to reload {} from Nacos (attempt {}/{}): {}", 
                        cacheKey, retryCount, maxRetries, e.getMessage());
                
                // Send alert on first failure
                if (retryCount == 1 && alertService != null) {
                    alertService.sendError("Nacos unavailable for " + cacheKey + ", will retry...");
                }
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // Fallback: use last valid config if Nacos is completely unavailable
        if (!reloaded) {
            log.error("All Nacos reload attempts failed for {}, using fallback cache", cacheKey);
            cachedConfig = getFallbackConfig(cacheKey);
                    
            if (cachedConfig != null) {
                log.warn("Using fallback configuration for {} (last valid config from Nacos)", cacheKey);
                        
                // Send alert when falling back to disaster recovery cache
                if (alertService != null) {
                    alertService.sendError("Using fallback cache for " + cacheKey + " - Nacos is completely unavailable!");
                }
                        
                return cachedConfig;
            } else {
                log.error("No fallback configuration available for {}", cacheKey);
                        
                if (alertService != null) {
                    alertService.sendError("No fallback cache for " + cacheKey + " - Service may be affected!");
                }
                        
                return null;
            }
        }

        return cachedConfig;
    }

    /**
     * Summarize configuration for logging purposes.
     */
    private String summarizeConfig(JsonNode root) {
        if (root.has("routes") && root.get("routes").isArray()) {
            return root.get("routes").size() + " routes";
        } else if (root.has("services") && root.get("services").isArray()) {
            return root.get("services").size() + " services";
        } else if (root.has("plugins") || root.has("strategies")) {
            return "plugins/strategies configured";
        } else {
            return "configuration loaded";
        }
    }
}
