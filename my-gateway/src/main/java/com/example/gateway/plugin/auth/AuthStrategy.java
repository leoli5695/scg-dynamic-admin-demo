package com.example.gateway.plugin.auth;

import com.example.gateway.plugin.AbstractPlugin;
import com.example.gateway.plugin.PluginType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Authentication strategy implementation.
 * Handles JWT/API Key/OAuth2 authentication.
 */
@Slf4j
@Component
public class AuthStrategy extends AbstractPlugin {
    
    private String defaultAuthType = "JWT";
    
    @Override
    public PluginType getType() {
     return PluginType.AUTH;
    }
    
    @Override
    public void apply(Map<String, Object> context) {
        if (!isEnabled()) {
            log.trace("Auth strategy disabled, skipping");
        return;
        }
        
        String routeId = (String) context.get("routeId");
        String authType = getAuthType();
        
        // Check if authentication is required for this route
       Boolean authenticated = (Boolean) context.get("authenticated");
        
        if (authenticated == null || !authenticated) {
            log.warn("Authentication required but not provided for route {}", routeId);
            context.put("authRejected", true);
        return;
        }
        
        context.put("authType", authType);
        context.put("authRejected", false);
        
        log.debug("Auth applied: route={}, type={}", routeId, authType);
    }
    
    @Override
    public void refresh(Object config) {
        super.refresh(config);
        if (config instanceof Map) {
            Object authTypeObj = ((Map<?, ?>) config).get("defaultAuthType");
            if (authTypeObj != null) {
                this.defaultAuthType = authTypeObj.toString().toUpperCase();
                log.info("Default auth type updated to {}", defaultAuthType);
            }
        }
    }
    
    /**
     * Get configured auth type.
     */
    private String getAuthType() {
      return getConfigValue("authType", defaultAuthType);
    }
}
