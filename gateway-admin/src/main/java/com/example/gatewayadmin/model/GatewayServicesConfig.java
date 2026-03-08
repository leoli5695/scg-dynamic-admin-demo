package com.example.gatewayadmin.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway service configuration wrapper for Nacos serialization.
 *
 * @author leoli
 */
@Data
public class GatewayServicesConfig {
    
    /**
     * Config version.
     */
    private String version = "1.0";
    
    /**
     * Service list.
     */
    private List<ServiceDefinition> services = new ArrayList<>();
    
    public GatewayServicesConfig() {}
    
    public GatewayServicesConfig(List<ServiceDefinition> services) {
        this.services = services;
    }
}
