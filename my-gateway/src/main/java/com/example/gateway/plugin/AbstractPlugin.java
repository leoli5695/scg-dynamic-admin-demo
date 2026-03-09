package com.example.gateway.plugin;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Abstract plugin with common functionality.
 */
@Slf4j
public abstract class AbstractPlugin implements Plugin {
    
   protected volatile boolean enabled = true;
   protected Map<String, Object> config;
    
    @Override
    public void refresh(Object config) {
        if (config instanceof Map) {
            this.config = (Map<String, Object>) config;
            Object enabledObj = ((Map<?, ?>) config).get("enabled");
            this.enabled = enabledObj == null || Boolean.parseBoolean(enabledObj.toString());
            
            log.info("Plugin {} refreshed, enabled: {}", getType().getDisplayName(), this.enabled);
        }
    }
    
    @Override
    public boolean isEnabled() {
      return enabled;
    }
    
    /**
     * Get configuration value by key.
     */
    @SuppressWarnings("unchecked")
   protected <T> T getConfigValue(String key, T defaultValue) {
        if (config == null) {
          return defaultValue;
        }
        Object value = config.get(key);
       return value != null ? (T) value : defaultValue;
    }
}
