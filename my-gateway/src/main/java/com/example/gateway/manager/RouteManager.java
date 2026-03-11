package com.example.gateway.manager;

import com.example.gateway.cache.GenericCacheManager;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Route configuration manager (uses GenericCacheManager).
 */
@Slf4j
@Component
public class RouteManager {

    private static final String CACHE_KEY = "routes";
    
    @Autowired
    private GenericCacheManager<JsonNode> cacheManager;
    
    /**
     * Load and cache route configuration.
     *
     * @param config Route configuration JSON string
     */
    public void loadConfig(String config) {
        cacheManager.loadConfig(CACHE_KEY, config);
    }

    /**
     * Get cached route configuration.
     *
     * @return Cached route configuration, or null if not loaded
     */
    public JsonNode getCachedConfig() {
        return cacheManager.getCachedConfig(CACHE_KEY);
    }

    /**
     * Check if cache is valid.
     *
     * @return true if cache is loaded and not expired
     */
    public boolean isCacheValid() {
        return cacheManager.isCacheValid(CACHE_KEY);
    }

    /**
     * Get fallback configuration (last valid config from Nacos).
     *
     * @return Last valid configuration, or null if never loaded
     */
    public JsonNode getFallbackConfig() {
        return cacheManager.getFallbackConfig(CACHE_KEY);
    }

    /**
     * Clear cached configuration.
     */
    public void clearCache() {
        cacheManager.clearCache(CACHE_KEY);
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
}
