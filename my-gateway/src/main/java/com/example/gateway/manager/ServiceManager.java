package com.example.gateway.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service configuration manager.
 * Responsible for loading, caching and managing service endpoint configurations.
 */
@Slf4j
@Component
public class ServiceManager {

    private volatile long lastLoadTime = 0;
    private static final long CACHE_TTL_MS = 10000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<JsonNode> serviceConfigCache = new AtomicReference<>();
    private final Map<String, ServiceEndpoint> endpointCache = new ConcurrentHashMap<>();

    /**
     * Load and cache service configuration.
     *
     * @param config Service configuration JSON string
     */
    public void loadConfig(String config) {
        try {
            JsonNode root = objectMapper.readTree(config);
            serviceConfigCache.set(root);

            // Parse and cache all service endpoints
            parseServiceEndpoints(root);

            lastLoadTime = System.currentTimeMillis();

            int serviceCount = countServices(root);
            log.info("Service configuration loaded: {} services, {} endpoints",
                    serviceCount, endpointCache.size());
        } catch (Exception e) {
            log.error("Failed to load service configuration", e);
            throw new RuntimeException("Failed to parse service config", e);
        }
    }

    /**
     * Get cached service configuration.
     *
     * @return Cached service configuration, or null if not loaded
     */
    public JsonNode getCachedConfig() {
        return serviceConfigCache.get();
    }

    /**
     * Check if cache is valid.
     *
     * @return true if cache is valid and not expired
     */
    public boolean isCacheValid() {
        JsonNode config = serviceConfigCache.get();
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
        serviceConfigCache.set(null);
        endpointCache.clear();
        lastLoadTime = 0;
        log.info("Service configuration cache cleared");
    }

    /**
     * Get service endpoint by service name.
     *
     * @param serviceName Service name
     * @return Service endpoint, or null if not found
     */
    public ServiceEndpoint getServiceEndpoint(String serviceName) {
        return endpointCache.get(serviceName);
    }

    /**
     * Count total number of services from config.
     */
    public int countServices(JsonNode root) {
        if (root == null || !root.isObject()) {
            return 0;
        }

        return root.size();
    }

    /**
     * Parse service endpoints from configuration.
     */
    private void parseServiceEndpoints(JsonNode root) {
        endpointCache.clear();

        if (root == null || !root.isObject()) {
            return;
        }

        // Expected format: {"service-name": {"ip": "127.0.0.1", "port": 8080}}
        root.fields().forEachRemaining(entry -> {
            String serviceName = entry.getKey();
            JsonNode serviceNode = entry.getValue();

            if (serviceNode.isObject()) {
                try {
                    String ip = serviceNode.has("ip") ?
                            serviceNode.get("ip").asText() : null;
                    int port = serviceNode.has("port") ?
                            serviceNode.get("port").asInt() : -1;

                    if (ip != null && !ip.isEmpty() && port > 0) {
                        endpointCache.put(serviceName, new ServiceEndpoint(ip, port));
                        log.debug("Cached service endpoint: {} -> {}:{}", serviceName, ip, port);
                    } else {
                        log.warn("Invalid service endpoint for {}: ip={}, port={}",
                                serviceName, ip, port);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse service endpoint for {}", serviceName, e);
                }
            }
        });
    }

    /**
     * Get last load time.
     *
     * @return Last load timestamp in milliseconds
     */
    public long getLastLoadTime() {
        return lastLoadTime;
    }

    /**
     * Service endpoint representation.
     */
    public static class ServiceEndpoint {
        private final String ip;
        private final int port;

        public ServiceEndpoint(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
