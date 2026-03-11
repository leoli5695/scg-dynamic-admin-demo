package com.example.gateway.refresher;

import com.example.gateway.center.spi.ConfigCenterService;
import com.example.gateway.manager.RouteManager;
import com.example.gateway.route.DynamicRouteDefinitionLocator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Route configuration refresher.
 * Listens to gateway-routes.json changes and refreshes routes.
 *
 * @author leoli
 * @version 1.0
 */
@Slf4j
@Component
public class RouteRefresher extends AbstractRefresher {

    private final RouteManager routeManager;
    private final ConfigCenterService configService;
    private final DynamicRouteDefinitionLocator routeLocator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GROUP = "DEFAULT_GROUP";
    private static final String DATA_ID = "gateway-routes.json";

    @Autowired
    public RouteRefresher(RouteManager routeManager,
                          ConfigCenterService confgService,
                          DynamicRouteDefinitionLocator routeLocator) {
        this.routeManager = routeManager;
        this.routeLocator = routeLocator;
        this.configService = confgService;
        log.info("RouteRefresher initialized for config center: {}", configService.getCenterType());
    }

    /**
     * Initialize listener after bean construction
     */
    @PostConstruct
    public void init() {
        // Register listener to Nacos config center
        ConfigCenterService.ConfigListener listener = (dataId, group, newContent) -> {
            log.info("Route config change detected: {}, content={}", dataId, 
                    newContent == null ? "null" : (newContent.isBlank() ? "empty" : "has content"));
            if (newContent == null || newContent.isBlank()) {
                log.warn("Route config deleted or empty, clearing cache");
                clearCache();
            } else {
                onConfigChange(dataId, newContent);
            }
        };
        configService.addListener(DATA_ID, GROUP, listener);
        log.info("RouteRefresher registered listener for {}", DATA_ID);

        // Load initial configuration
        loadInitialConfig();
        
        // Warmup: proactively sync from Nacos on startup
        warmupCache();
    }
    
    /**
     * Warmup cache on startup to ensure routes are available immediately.
     */
    private void warmupCache() {
        log.info("🔥 Warming up route cache on startup...");
        try {
            reloadConfigFromNacos();
            log.info("✅ Route cache warmed up successfully");
        } catch (Exception e) {
            log.warn("⚠️  Route cache warmup failed, will load on first request: {}", e.getMessage());
        }
    }

    /**
     * Clear route cache when configuration is deleted
     */
    private void clearCache() {
        try {
            // Clear RouteManager cache
            routeManager.loadConfig("[]"); // Empty array
            
            // Trigger SCG to refresh and remove all routes
            routeLocator.refresh();
            
            log.info("Route cache cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear route cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup before bean destruction
     */
    @PreDestroy
    public void destroy() {
        ConfigCenterService.ConfigListener listener = (dataId, group, newContent) -> {
            log.info("Route config change detected: {}", dataId);
            onConfigChange(dataId, newContent);
        };
        configService.removeListener(DATA_ID, GROUP, listener);
        log.info("RouteRefresher removed listener for {}", DATA_ID);
    }

    /**
     * Load initial configuration on startup
     */
    private void loadInitialConfig() {
        try {
            String initialConfig = configService.getConfig(DATA_ID, GROUP);
            if (initialConfig != null && !initialConfig.isBlank()) {
                log.info("Loading initial route configuration");
                onConfigChange(DATA_ID, initialConfig);
            } else {
                log.warn("No initial route configuration found");
            }
        } catch (Exception e) {
            log.error("Failed to load initial route configuration: {}", e.getMessage(), e);
        }
    }

    /**
     * Reload configuration from Nacos manually (fallback when cache is invalid)
     */
    public void reloadConfigFromNacos() {
        log.info("Manually reloading route configuration from Nacos");
        try {
            String config = configService.getConfig(DATA_ID, GROUP);
            if (config != null && !config.isBlank()) {
                log.info("Successfully reloaded route configuration from Nacos");
                onConfigChange(DATA_ID, config);
            } else {
                log.warn("No route configuration found in Nacos during manual reload");
                clearCache();
            }
        } catch (Exception e) {
            log.error("Failed to reload route configuration from Nacos: {}", e.getMessage(), e);
        }
    }

    @Override
    protected Object parseConfig(String json) {
        try {
            // Parse JSON to JsonNode for validation
            JsonNode root = objectMapper.readTree(json);

            // Validate configuration structure
            validateConfig(root);

            // Load config into RouteManager
            routeManager.loadConfig(json);

            log.debug("Route config parsed successfully");
            return root;
        } catch (Exception e) {
            log.error("Failed to parse route config: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse route config", e);
        }
    }

    @Override
    protected void updateCache(Object config) {
        // Cache is managed by RouteManager
        log.debug("Route config cache updated by RouteManager");
    }

    @Override
    protected void doRefresh(Object config) {
        if (config == null || !(config instanceof JsonNode)) {
            log.warn("Invalid route config, skipping refresh");
            return;
        }

        JsonNode root = (JsonNode) config;
        int routeCount = routeManager.countRoutes(root);

        log.info("Route config refreshed: {} routes", routeCount);

        // Trigger SCG to reload routes immediately
        routeLocator.refresh();

        // Log detailed route information
        logRouteDetails(root);
    }

    /**
     * Validate configuration structure
     */
    private void validateConfig(JsonNode root) {
        if (!root.has("routes")) {
            log.warn("Configuration missing 'routes' node");
        }

        if (root.has("routes") && !root.get("routes").isArray()) {
            throw new IllegalArgumentException("'routes' must be an array");
        }

        // Validate each route has required fields
        if (root.has("routes")) {
            root.get("routes").forEach(route -> {
                if (!route.has("id")) {
                    throw new IllegalArgumentException("Route missing required 'id' field");
                }
                if (!route.has("uri")) {
                    throw new IllegalArgumentException("Route '" + route.get("id").asText() +
                            "' missing required 'uri' field");
                }
            });
        }

        log.debug("Route config validation passed");
    }

    /**
     * Log detailed route information
     */
    private void logRouteDetails(JsonNode root) {
        if (!root.has("routes")) {
            return;
        }

        root.get("routes").forEach(route -> {
            String routeId = route.has("id") ? route.get("id").asText() : "unknown";
            String uri = route.has("uri") ? route.get("uri").asText() : "unknown";
            boolean enabled = !route.has("enabled") || route.get("enabled").asBoolean(true);

            log.info("  Route '{}': {} -> {} (enabled={})",
                    routeId, route.has("predicates") ? route.get("predicates").toString() : "*", uri, enabled);

            if (route.has("filters") && route.get("filters").isArray()) {
                log.debug("    Filters: {}", route.get("filters").size());
                route.get("filters").forEach(filter -> {
                    log.debug("      - {}", filter);
                });
            }

            if (route.has("metadata") && route.get("metadata").isObject()) {
                log.debug("    Metadata: {} entries", route.get("metadata").size());
            }
        });
    }
}
