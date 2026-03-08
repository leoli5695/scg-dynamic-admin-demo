package com.example.gatewayadmin.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway route configuration wrapper for Nacos serialization.
 *
 * @author leoli
 */
@Data
public class GatewayRoutesConfig {
    
    /**
     * Config version.
     */
    private String version = "1.0";
    
    /**
     * Route list.
     */
    private List<RouteDefinition> routes = new ArrayList<>();
    
    public GatewayRoutesConfig() {}
    
    public GatewayRoutesConfig(List<RouteDefinition> routes) {
        this.routes = routes;
    }
}
