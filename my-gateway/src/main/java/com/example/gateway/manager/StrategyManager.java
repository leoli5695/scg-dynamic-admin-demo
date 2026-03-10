package com.example.gateway.manager;

import com.example.gateway.enums.StrategyType;
import com.example.gateway.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy Configuration Manager
 * <p>
 * Centralized manager for all plugin strategies.
 * Provides unified access to rate limiters, timeouts, circuit breakers, custom headers, and IP filters.
 * <p>
 * Usage:
 * - Get strategy by type and routeId: getConfig(StrategyType.RATE_LIMITER, "user-service")
 * - All strategies are loaded from gateway-plugins.json via StrategyRefresher
 *
 * @author leoli
 * @version 1.0
 */
@Slf4j
@Component
public class StrategyManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Strategy configuration stores
    private final Map<String, AuthConfig> authConfigs = new ConcurrentHashMap<>();
    private final Map<String, TimeoutConfig> timeoutConfigs = new ConcurrentHashMap<>();
    private final Map<String, IPFilterConfig> ipFilterConfigs = new ConcurrentHashMap<>();
    private final Map<String, RateLimiterConfig> rateLimiterConfigs = new ConcurrentHashMap<>();
    private final Map<String, CustomHeaderConfig> customHeaderConfigs = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerConfig> circuitBreakerConfigs = new ConcurrentHashMap<>();

    /**
     * Load strategy configuration from JSON
     */
    public void loadConfig(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Clear old configurations
            clearAllConfigs();

            // Parse and load new configurations
            if (root.has("plugins")) {
                JsonNode pluginsNode = root.get("plugins");

                // Load rate limiters
                if (pluginsNode.has("rateLimiters") && pluginsNode.get("rateLimiters").isArray()) {
                    pluginsNode.get("rateLimiters").forEach(node -> {
                        String routeId = node.get("routeId").asText();
                        RateLimiterConfig config = parseRateLimiterConfig(node);
                        if (config != null) {
                            rateLimiterConfigs.put(routeId, config);
                        }
                    });
                }

                // Load timeouts
                if (pluginsNode.has("timeouts") && pluginsNode.get("timeouts").isArray()) {
                    pluginsNode.get("timeouts").forEach(node -> {
                        String routeId = node.get("routeId").asText();
                        TimeoutConfig config = parseTimeoutConfig(node);
                        if (config != null) {
                            timeoutConfigs.put(routeId, config);
                        }
                    });
                }

                // Load circuit breakers
                if (pluginsNode.has("circuitBreakers") && pluginsNode.get("circuitBreakers").isArray()) {
                    pluginsNode.get("circuitBreakers").forEach(node -> {
                        String routeId = node.get("routeId").asText();
                        CircuitBreakerConfig config = parseCircuitBreakerConfig(node);
                        if (config != null) {
                            circuitBreakerConfigs.put(routeId, config);
                        }
                    });
                }

                // Load custom headers
                if (pluginsNode.has("customHeaders") && pluginsNode.get("customHeaders").isArray()) {
                    pluginsNode.get("customHeaders").forEach(node -> {
                        String routeId = node.get("routeId").asText();
                        CustomHeaderConfig config = parseCustomHeaderConfig(node);
                        if (config != null) {
                            customHeaderConfigs.put(routeId, config);
                        }
                    });
                }

                // Load IP filters
                if (pluginsNode.has("ipFilters") && pluginsNode.get("ipFilters").isArray()) {
                    pluginsNode.get("ipFilters").forEach(node -> {
                        String routeId = node.get("routeId").asText();
                        IPFilterConfig config = parseIPFilterConfig(node);
                        if (config != null) {
                            ipFilterConfigs.put(routeId, config);
                        }
                    });
                }

                // Load auth configs
                if (pluginsNode.has("authConfigs") && pluginsNode.get("authConfigs").isArray()) {
                    pluginsNode.get("authConfigs").forEach(node -> {
                        String routeId = node.get("routeId").asText();
                        AuthConfig config = parseAuthConfig(node);
                        if (config != null) {
                            authConfigs.put(routeId, config);
                        }
                    });
                }
            }

            log.info("Strategy config loaded: {} rate limiters, {} timeouts, {} circuit breakers, {} custom headers, {} IP filters, {} auth configs",
                    rateLimiterConfigs.size(), timeoutConfigs.size(), circuitBreakerConfigs.size(),
                    customHeaderConfigs.size(), ipFilterConfigs.size(), authConfigs.size());

        } catch (Exception e) {
            log.error("Failed to load strategy config: {}", e.getMessage(), e);
        }
    }

    /**
     * Get strategy configuration by type and route ID
     *
     * @param strategyType The type of strategy
     * @param routeId      The route ID
     * @return The strategy configuration, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(StrategyType strategyType, String routeId) {
        switch (strategyType) {
            case RATE_LIMITER:
                return (T) rateLimiterConfigs.get(routeId);
            case TIMEOUT:
                return (T) timeoutConfigs.get(routeId);
            case CIRCUIT_BREAKER:
                return (T) circuitBreakerConfigs.get(routeId);
            case CUSTOM_HEADER:
                return (T) customHeaderConfigs.get(routeId);
            case IP_FILTER:
                return (T) ipFilterConfigs.get(routeId);
            default:
                log.warn("Unknown strategy type: {}", strategyType);
                return null;
        }
    }

    /**
     * Check if a strategy is enabled for a route
     */
    public boolean isStrategyEnabled(StrategyType strategyType, String routeId) {
        Object config = getConfig(strategyType, routeId);
        if (config == null) {
            return false;
        }

        // Check enabled field based on config type
        if (config instanceof RateLimiterConfig) {
            return ((RateLimiterConfig) config).isEnabled();
        } else if (config instanceof TimeoutConfig) {
            return ((TimeoutConfig) config).isEnabled();
        } else if (config instanceof CircuitBreakerConfig) {
            return ((CircuitBreakerConfig) config).isEnabled();
        } else if (config instanceof CustomHeaderConfig) {
            return ((CustomHeaderConfig) config).isEnabled();
        } else if (config instanceof IPFilterConfig) {
            return ((IPFilterConfig) config).isEnabled();
        }

        return false;
    }

    /**
     * Clear all configurations
     */
    private void clearAllConfigs() {
        rateLimiterConfigs.clear();
        timeoutConfigs.clear();
        circuitBreakerConfigs.clear();
        customHeaderConfigs.clear();
        ipFilterConfigs.clear();
        log.debug("Cleared all strategy configurations");
    }

    // Parser methods

    private RateLimiterConfig parseRateLimiterConfig(JsonNode node) {
        try {
            int qps = node.has("qps") ? node.get("qps").asInt(0) : 0;
            String timeUnit = node.has("timeUnit") ? node.get("timeUnit").asText("second") : "second";
            int burstCapacity = node.has("burstCapacity") ? node.get("burstCapacity").asInt(0) : 0;
            String keyResolver = node.has("keyResolver") ? node.get("keyResolver").asText("ip") : "ip";
            String headerName = node.has("headerName") ? node.get("headerName").asText(null) : null;
            String keyType = node.has("keyType") ? node.get("keyType").asText("combined") : "combined";
            String keyPrefix = node.has("keyPrefix") ? node.get("keyPrefix").asText("rate_limit:") : "rate_limit:";
            boolean enabled = !node.has("enabled") || node.get("enabled").asBoolean(true);

            if (qps <= 0) {
                return null; // Invalid QPS
            }

            return new RateLimiterConfig(null, qps, timeUnit, burstCapacity,
                    keyResolver, headerName, keyType, keyPrefix, enabled);
        } catch (Exception e) {
            log.error("Failed to parse rate limiter config: {}", e.getMessage(), e);
            return null;
        }
    }

    private TimeoutConfig parseTimeoutConfig(JsonNode node) {
        try {
            int connectTimeout = node.has("connectTimeout") ? node.get("connectTimeout").asInt(5000) : 5000;
            int readTimeout = node.has("readTimeout") ? node.get("readTimeout").asInt(10000) : 10000;
            int responseTimeout = node.has("responseTimeout") ? node.get("responseTimeout").asInt(30000) : 30000;
            boolean enabled = !node.has("enabled") || node.get("enabled").asBoolean(true);

            return new TimeoutConfig(null, connectTimeout, responseTimeout, enabled);
        } catch (Exception e) {
            log.error("Failed to parse timeout config: {}", e.getMessage(), e);
            return null;
        }
    }

    private CircuitBreakerConfig parseCircuitBreakerConfig(JsonNode node) {
        try {
            float failureRateThreshold = node.has("failureRateThreshold") ? (float) node.get("failureRateThreshold").asDouble(50.0) : 50.0f;
            long waitDurationInOpenState = node.has("waitDurationInOpenState") ? node.get("waitDurationInOpenState").asLong(30000L) : 30000L;
            boolean enabled = !node.has("enabled") || node.get("enabled").asBoolean(true);

            return new CircuitBreakerConfig(null, failureRateThreshold, waitDurationInOpenState);
        } catch (Exception e) {
            log.error("Failed to parse circuit breaker config: {}", e.getMessage(), e);
            return null;
        }
    }

    private CustomHeaderConfig parseCustomHeaderConfig(JsonNode node) {
        try {
            Map<String, String> headers = new ConcurrentHashMap<>();
            if (node.has("headers") && node.get("headers").isObject()) {
                node.get("headers").fields().forEachRemaining(entry -> {
                    headers.put(entry.getKey(), entry.getValue().asText());
                });
            }
            boolean enabled = !node.has("enabled") || node.get("enabled").asBoolean(true);

            return new CustomHeaderConfig(headers, enabled);
        } catch (Exception e) {
            log.error("Failed to parse custom header config: {}", e.getMessage(), e);
            return null;
        }
    }

    private IPFilterConfig parseIPFilterConfig(JsonNode node) {
        try {
            String mode = node.has("mode") ? node.get("mode").asText("blacklist") : "blacklist";
            java.util.List<String> ipList = new java.util.ArrayList<>();
            if (node.has("ipList") && node.get("ipList").isArray()) {
                node.get("ipList").forEach(ipNode -> {
                    ipList.add(ipNode.asText());
                });
            }
            boolean enabled = !node.has("enabled") || node.get("enabled").asBoolean(true);

            return new IPFilterConfig(mode, ipList, enabled);
        } catch (Exception e) {
            log.error("Failed to parse IP filter config: {}", e.getMessage(), e);
            return null;
        }
    }

    private AuthConfig parseAuthConfig(JsonNode node) {
        try {
            AuthConfig config = new AuthConfig();

            String routeId = node.has("routeId") ? node.get("routeId").asText() : null;
            String authType = node.has("authType") ? node.get("authType").asText() : null;
            boolean enabled = !node.has("enabled") || node.get("enabled").asBoolean(true);

            if (routeId == null || routeId.isEmpty() || authType == null || authType.isEmpty()) {
                return null; // Invalid config
            }

            config.setRouteId(routeId);
            config.setAuthType(authType);
            config.setEnabled(enabled);

            // Optional fields
            if (node.has("secretKey")) {
                config.setSecretKey(node.get("secretKey").asText());
            }
            if (node.has("apiKey")) {
                config.setApiKey(node.get("apiKey").asText());
            }
            if (node.has("clientId")) {
                config.setClientId(node.get("clientId").asText());
            }
            if (node.has("clientSecret")) {
                config.setClientSecret(node.get("clientSecret").asText());
            }
            if (node.has("tokenEndpoint")) {
                config.setTokenEndpoint(node.get("tokenEndpoint").asText());
            }
            if (node.has("customConfig")) {
                config.setCustomConfig(node.get("customConfig").asText());
            }

            return config;
        } catch (Exception e) {
            log.error("Failed to parse auth config: {}", e.getMessage(), e);
            return null;
        }
    }
}
