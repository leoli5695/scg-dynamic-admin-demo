package com.leoli.gateway.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway plugin configuration wrapper for Nacos serialization.
 *
 * @author leoli
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayStrategyConfig {

    /**
     * Config version.
     */
    private String version = "1.0";

    /**
     * Plugin configuration.
     */
    private StrategyConfig plugins = new StrategyConfig();
}
