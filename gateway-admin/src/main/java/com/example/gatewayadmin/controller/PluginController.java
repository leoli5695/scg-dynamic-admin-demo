package com.example.gatewayadmin.controller;

import com.example.gatewayadmin.model.PluginConfig;
import com.example.gatewayadmin.service.PluginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plugin management controller.
 *
 * @author leoli
 */
@Slf4j
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    @Autowired
    private PluginService pluginService;

    // ==================== Rate Limiter Plugin API ====================

    /**
     * Get all rate limiter configurations.
     */
    @GetMapping("/rate-limiters")
    public ResponseEntity<Map<String, Object>> getAllRateLimiters() {
        List<PluginConfig.RateLimiterConfig> configs = pluginService.getAllRateLimiters();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", configs);
        return ResponseEntity.ok(result);
    }

    /**
     * Get rate limiter configuration by route ID.
     */
    @GetMapping("/rate-limiters/{routeId}")
    public ResponseEntity<Map<String, Object>> getRateLimiterByRouteId(@PathVariable String routeId) {
        PluginConfig.RateLimiterConfig config = pluginService.getRateLimiterByRouteId(routeId);
        Map<String, Object> result = new HashMap<>();
        if (config != null) {
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "Rate limiter config not found for route: " + routeId);
            return ResponseEntity.status(404).body(result);
        }
    }

    /**
     * Create rate limiter configuration.
     */
    @PostMapping("/rate-limiters")
    public ResponseEntity<Map<String, Object>> createRateLimiter(@RequestBody PluginConfig.RateLimiterConfig config) {
        log.info("Creating rate limiter for route: {}", config.getRouteId());
        boolean success = pluginService.createRateLimiter(config);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Rate limiter created successfully");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to create rate limiter");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Update rate limiter configuration.
     */
    @PutMapping("/rate-limiters/{routeId}")
    public ResponseEntity<Map<String, Object>> updateRateLimiter(
            @PathVariable String routeId,
            @RequestBody PluginConfig.RateLimiterConfig config) {
        log.info("Updating rate limiter for route: {}", routeId);
        boolean success = pluginService.updateRateLimiter(routeId, config);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Rate limiter updated successfully");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to update rate limiter");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Delete rate limiter configuration.
     */
    @DeleteMapping("/rate-limiters/{routeId}")
    public ResponseEntity<Map<String, Object>> deleteRateLimiter(@PathVariable String routeId) {
        log.info("Deleting rate limiter for route: {}", routeId);
        boolean success = pluginService.deleteRateLimiter(routeId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Rate limiter deleted successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to delete rate limiter");
            return ResponseEntity.status(500).body(result);
        }
    }

    // ==================== Custom Header Plugin API (removed) ====================
    // Note: Custom Header APIs removed - use SCG native AddRequestHeader filter instead
    // Route config example:
    // filters:
    //   - AddRequestHeader=X-Forwarded-For, 10.0.0.1
    //   - AddRequestHeader=X-Custom-Header, ${CUSTOM_VALUE}

    // ==================== IP Filter Plugin API ====================

    /**
     * Get all IP filter configurations.
     */
    @GetMapping("/ip-filters")
    public ResponseEntity<Map<String, Object>> getAllIPFilters() {
        List<PluginConfig.IPFilterConfig> configs = pluginService.getAllIPFilters();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", configs);
        return ResponseEntity.ok(result);
    }

    /**
     * Get IP filter configuration by route ID.
     */
    @GetMapping("/ip-filters/{routeId}")
    public ResponseEntity<Map<String, Object>> getIPFilterByRoute(@PathVariable String routeId) {
        PluginConfig.IPFilterConfig config = pluginService.getIPFilterByRoute(routeId);
        Map<String, Object> result = new HashMap<>();
        if (config != null) {
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "IP filter config not found for route: " + routeId);
            return ResponseEntity.status(404).body(result);
        }
    }

    /**
     * Create IP filter configuration.
     */
    @PostMapping("/ip-filters")
    public ResponseEntity<Map<String, Object>> createIPFilter(@RequestBody PluginConfig.IPFilterConfig config) {
        log.info("Creating IP filter for route: {}", config.getRouteId());
        boolean success = pluginService.createIPFilter(config);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "IP filter created successfully");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to create IP filter");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Update IP filter configuration.
     */
    @PutMapping("/ip-filters/{routeId}")
    public ResponseEntity<Map<String, Object>> updateIPFilter(
            @PathVariable String routeId,
            @RequestBody PluginConfig.IPFilterConfig config) {
        log.info("Updating IP filter for route: {}", routeId);
        boolean success = pluginService.updateIPFilter(routeId, config);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "IP filter updated successfully");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to update IP filter");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Delete IP filter configuration.
     */
    @DeleteMapping("/ip-filters/{routeId}")
    public ResponseEntity<Map<String, Object>> deleteIPFilter(@PathVariable String routeId) {
        log.info("Deleting IP filter for route: {}", routeId);
        boolean success = pluginService.deleteIPFilter(routeId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "IP filter deleted successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to delete IP filter");
            return ResponseEntity.status(500).body(result);
        }
    }
    
    // ==================== Timeout Plugin API ====================
    
    /**
     * Get all timeout configurations.
     */
    @GetMapping("/timeouts")
    public ResponseEntity<Map<String, Object>> getAllTimeouts() {
        List<PluginConfig.TimeoutConfig> timeouts = pluginService.getAllTimeouts();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", timeouts);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get timeout configuration by route ID.
     */
    @GetMapping("/timeouts/{routeId}")
    public ResponseEntity<Map<String, Object>> getTimeoutByRoute(@PathVariable String routeId) {
        PluginConfig.TimeoutConfig config = pluginService.getTimeoutByRoute(routeId);
        Map<String, Object> result = new HashMap<>();
        if (config != null) {
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "Timeout config not found for route: " + routeId);
            return ResponseEntity.status(404).body(result);
        }
    }
    
    /**
     * Create timeout configuration.
     */
    @PostMapping("/timeouts")
    public ResponseEntity<Map<String, Object>> createTimeout(@RequestBody PluginConfig.TimeoutConfig config) {
        log.info("Creating timeout config for route: {}", config.getRouteId());
        boolean success = pluginService.createTimeout(config);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Timeout config created successfully");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to create timeout config");
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * Update timeout configuration.
     */
    @PutMapping("/timeouts/{routeId}")
    public ResponseEntity<Map<String, Object>> updateTimeout(
            @PathVariable String routeId,
            @RequestBody PluginConfig.TimeoutConfig config) {
        log.info("Updating timeout config for route: {}", routeId);
        boolean success = pluginService.updateTimeout(routeId, config);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Timeout config updated successfully");
            result.put("data", config);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to update timeout config");
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * Delete timeout configuration.
     */
    @DeleteMapping("/timeouts/{routeId}")
    public ResponseEntity<Map<String, Object>> deleteTimeout(@PathVariable String routeId) {
        log.info("Deleting timeout config for route: {}", routeId);
        boolean success = pluginService.deleteTimeout(routeId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Timeout config deleted successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to delete timeout config");
            return ResponseEntity.status(500).body(result);
        }
    }

    // ==================== Common API ====================

    /**
     * Get all plugin configurations.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPlugins() {
        PluginConfig plugins = pluginService.getAllPlugins();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", plugins);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch update plugin configurations.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchUpdatePlugins(@RequestBody PluginConfig plugins) {
        log.info("Batch updating plugins config");
        boolean success = pluginService.batchUpdatePlugins(plugins);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Plugins updated successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to update plugins");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Get plugin statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPluginStats() {
        PluginService.PluginStats stats = pluginService.getPluginStats();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", stats);
        return ResponseEntity.ok(result);
    }
}
