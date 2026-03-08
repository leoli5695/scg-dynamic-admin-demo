package com.example.gatewayadmin.service;

import com.example.gatewayadmin.config.GatewayAdminProperties;
import com.example.gatewayadmin.config.NacosConfigManager;
import com.example.gatewayadmin.model.GatewayRoutesConfig;
import com.example.gatewayadmin.model.RouteDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Route configuration service
 *
 * @author leoli
 */
@Slf4j
@Service
public class RouteService {

    private String routesDataId;
    private NacosPublisher publisher;
    @Autowired
    private GatewayAdminProperties properties;
    @Autowired
    private NacosConfigManager nacosConfigManager;
    // Local cache
    private final ConcurrentHashMap<String, RouteDefinition> routeCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        routesDataId = properties.getNacos().getDataIds().getRoutes();
        publisher = new NacosPublisher(nacosConfigManager, routesDataId);
        // Load initial route config from Nacos
        loadRoutesFromNacos();
    }

    /**
     * Get all routes
     */
    public List<RouteDefinition> getAllRoutes() {
        return new ArrayList<>(routeCache.values());
    }

    /**
     * Get route by ID.
     * Reloads from Nacos on cache miss to ensure data is not lost.
     */
    public RouteDefinition getRouteById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        // Try cache first
        RouteDefinition route = routeCache.get(id);
        if (route != null) {
            return route;
        }
        
        // Cache miss, reload from Nacos
        log.info("Route not found in cache, reloading from Nacos: {}", id);
        reloadRoutes();

        return routeCache.get(id);
    }

    /**
     * Create route
     */
    public boolean createRoute(RouteDefinition route) {
        if (route == null || route.getId() == null || route.getId().isEmpty()) {
            log.warn("Invalid route definition");
            return false;
        }

        if (routeCache.containsKey(route.getId())) {
            log.warn("Route already exists: {}", route.getId());
            return false;
        }

        routeCache.put(route.getId(), route);
        return publisher.publish(new GatewayRoutesConfig(new ArrayList<>(routeCache.values())));
    }

    /**
     * Update route
     */
    public boolean updateRoute(String id, RouteDefinition route) {
        if (route == null || id == null || id.isEmpty()) {
            log.warn("Invalid route definition");
            return false;
        }

        if (!routeCache.containsKey(id)) {
            log.warn("Route not found: {}", id);
            return false;
        }

        route.setId(id);
        routeCache.put(id, route);
        return publisher.publish(new GatewayRoutesConfig(new ArrayList<>(routeCache.values())));
    }

    /**
     * Delete route
     */
    public boolean deleteRoute(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        log.info("Deleting route from cache: {}", id);
        routeCache.remove(id);

        // If cache is empty, remove config from Nacos directly
        if (routeCache.isEmpty()) {
            log.info("Route cache is empty, removing config from Nacos: {}", routesDataId);
            return publisher.remove();
        }

        // Otherwise publish updated config
        boolean result = publisher.publish(new GatewayRoutesConfig(new ArrayList<>(routeCache.values())));
        if (result) {
            log.info("Successfully deleted route '{}' and published to Nacos", id);
        } else {
            log.error("Failed to publish route deletion to Nacos for route: {}", id);
        }
        return result;
    }

    /**
     * Batch create/update routes
     */
    public boolean batchUpdateRoutes(List<RouteDefinition> routes) {
        if (routes == null) {
            return false;
        }

        for (RouteDefinition route : routes) {
            if (route.getId() != null && !route.getId().isEmpty()) {
                routeCache.put(route.getId(), route);
            }
        }

        return publisher.publish(new GatewayRoutesConfig(routes));
    }

    /**
     * Reload route configuration
     */
    public void reloadRoutes() {
        loadRoutesFromNacos();
    }

    /**
     * Load route configuration from Nacos
     */
    private void loadRoutesFromNacos() {
        try {
            GatewayRoutesConfig config = nacosConfigManager.getConfig(routesDataId, GatewayRoutesConfig.class);
            if (config != null && config.getRoutes() != null) {
                routeCache.clear();
                for (RouteDefinition route : config.getRoutes()) {
                    if (route.getId() != null) {
                        routeCache.put(route.getId(), route);
                    }
                }
                log.info("Loaded {} routes from Nacos", routeCache.size());
            } else {
                log.info("No routes config found in Nacos, using empty config");
            }
        } catch (Exception e) {
            log.error("Error loading routes from Nacos", e);
        }
    }

    /**
     * Find routes by service name
     */
    public List<RouteDefinition> getRoutesByService(String serviceName) {
        return routeCache.values().stream()
                .filter(route -> route.getUri() != null && route.getUri().contains(serviceName))
                .collect(Collectors.toList());
    }

    /**
     * Get route statistics
     */
    public RouteStats getRouteStats() {
        RouteStats stats = new RouteStats();
        stats.setTotalCount(routeCache.size());
        stats.setLbRoutes((int) routeCache.values().stream()
                .filter(r -> r.getUri() != null && r.getUri().startsWith("lb://"))
                .count());
        stats.setHttpRoutes((int) routeCache.values().stream()
                .filter(r -> r.getUri() != null && r.getUri().startsWith("http"))
                .count());
        return stats;
    }

    /**
     * Force refresh cache from Nacos
     */
    public List<RouteDefinition> refreshFromNacos() {
        log.info("Force refreshing routes from Nacos");
        loadRoutesFromNacos();
        return getAllRoutes();
    }

    /**
     * Route statistics
     */
    public static class RouteStats {
        private int totalCount;
        private int lbRoutes;
        private int httpRoutes;

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getLbRoutes() {
            return lbRoutes;
        }

        public void setLbRoutes(int lbRoutes) {
            this.lbRoutes = lbRoutes;
        }

        public int getHttpRoutes() {
            return httpRoutes;
        }

        public void setHttpRoutes(int httpRoutes) {
            this.httpRoutes = httpRoutes;
        }
    }
}
