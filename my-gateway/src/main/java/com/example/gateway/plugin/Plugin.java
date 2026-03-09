package com.example.gateway.plugin;

import java.util.Map;

/**
 * Plugin interface for extensible gateway features.
 * Each plugin handles specific functionality (rate limiting, auth, circuit breaker, etc.)
 */
public interface Plugin {
    
    /**
     * Get plugin type.
     */
    PluginType getType();
    
    /**
     * Apply plugin logic to the request/response.
     */
    void apply(Map<String, Object> context);
    
    /**
     * Refresh plugin configuration.
     */
    void refresh(Object config);
    
    /**
     * Check if this plugin is enabled.
     */
    boolean isEnabled();
}
