package com.example.gatewayadmin.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway plugin configuration wrapper for Nacos serialization.
 *
 * @author leoli
 */
@Data
public class GatewayPluginsConfig {
    
    /**
     * Config version.
     */
    private String version = "1.0";
    
    /**
     * Plugin configuration.
     */
    private PluginConfig plugins = new PluginConfig();
    
    public GatewayPluginsConfig() {}
    
    public GatewayPluginsConfig(PluginConfig plugins) {
        this.plugins = plugins;
    }
}
