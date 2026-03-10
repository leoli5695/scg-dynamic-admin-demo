package com.example.gateway.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Route configuration manager.
 * Responsible for loading, caching and managing route configurations.
 */
@Slf4j
@Component
public class RouteManager {

    private volatile long lastLoadTime = 0;
    private static final long CACHE_TTL_MS = 10000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<JsonNode> routeConfigCache = new AtomicReference<>();

    /**
     * Load and cache route configuration.
     *
     * @param config Route configuration JSON string
     */
    public void loadConfig(String config) {
        try {
            JsonNode root = objectMapper.readTree(config);
            routeConfigCache.set(root);
            lastLoadTime = System.currentTimeMillis();

            int routeCount = countRoutes(root);
            log.info("Route configuration loaded: {} routes", routeCount);
        } catch (Exception e) {
            log.error("Failed to load route configuration", e);
            throw new RuntimeException("Failed to parse route config", e);
        }
    }

    /**
     * Get cached route configuration.
     *
     * @return Cached route configuration, or null if not loaded
     */
    public JsonNode getCachedConfig() {
        return routeConfigCache.get();
    }

    /**
     * Check if cache is valid.
     *
     * @return true if cache is valid and not expired
     */
    public boolean isCacheValid() {
        JsonNode config = routeConfigCache.get();
        if (config == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        return (now - lastLoadTime) < CACHE_TTL_MS;
    }

    /**
     * Clear cached configuration.
     */
    public void clearCache() {
        routeConfigCache.set(null);
        lastLoadTime = 0;
        log.info("Route configuration cache cleared");
    }

    /**
     * Count total number of routes from config.
     */
    public int countRoutes(JsonNode root) {
        if (root == null) {
            return 0;
        }

        if (root.has("routes")) {
            // Format: {"version": "1.0", "routes": [...]}
            JsonNode routesNode = root.get("routes");
            if (routesNode.isArray()) {
                return routesNode.size();
            }
        } else if (root.isArray()) {
            // Format: [...]
            return root.size();
        } else if (root.isObject()) {
            // Format: {routeId: {...}, ...}
            return root.size();
        }

        return 0;
    }

    /**
     * Get last load time.
     *
     * @return Last load timestamp in milliseconds
     */
    public long getLastLoadTime() {
        return lastLoadTime;
    }
}
