package com.example.gateway.schedule;

import com.example.gateway.monitor.AlertService;
import com.example.gateway.refresher.RouteRefresher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to periodically sync route configuration from Nacos.
 * Ensures routes are refreshed even if no requests trigger the reload.
 */
@Slf4j
@Component
public class RouteSyncScheduler {

    @Autowired
    private RouteRefresher routeRefresher;
    
    @Autowired(required = false)
    private AlertService alertService;
    
    @Value("${gateway.cache.sync-interval-ms:1800000}")
    private long syncIntervalMs = 1800000; // 30 minutes default

    /**
     * Sync routes from Nacos every 30 minutes (configurable).
     * - If successful: updates cache
     * - If config is empty: clears cache (intentional deletion)
     * - If network error: ignores and keeps current cache
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes, configurable via application.yml
    public void syncRoutesFromNacos() {
        log.info("Starting scheduled route synchronization from Nacos");
        
        try {
            routeRefresher.reloadConfigFromNacos();
            log.info("✅ Scheduled route synchronization completed successfully");
            
            if (alertService != null) {
                alertService.send("INFO", "Route sync successful at " + new java.util.Date());
            }
        } catch (Exception e) {
            log.warn("⚠️  Scheduled route synchronization failed (will continue with current cache): {}", 
                    e.getMessage());
            // Don't propagate exception - keep using current cache
            
            if (alertService != null) {
                alertService.sendWarn("Route sync failed: " + e.getMessage());
            }
        }
    }
}
