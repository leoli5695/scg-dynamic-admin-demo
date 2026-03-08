package com.example.gatewayadmin.service;

import com.example.gatewayadmin.config.NacosConfigManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Nacos configuration publisher utility
 * 
 * @author leoli
 */
@Slf4j
public class NacosPublisher {

    private final NacosConfigManager nacosConfigManager;
    private final String dataId;

    public NacosPublisher(NacosConfigManager nacosConfigManager, String dataId) {
        this.nacosConfigManager = nacosConfigManager;
        this.dataId = dataId;
    }

    /**
     * Publish configuration to Nacos (JSON type)
     */
    public boolean publish(Object config) {
        try {
            return nacosConfigManager.publishConfig(dataId, config);
        } catch (Exception e) {
            log.error("Error publishing config to Nacos, dataId: {}", dataId, e);
            return false;
        }
    }

    /**
     * Remove configuration from Nacos
     */
    public boolean remove() {
        try {
            return nacosConfigManager.removeConfig(dataId);
        } catch (Exception e) {
            log.error("Error removing config from Nacos, dataId: {}", dataId, e);
            return false;
        }
    }

    /**
     * Get Data ID
     */
    public String getDataId() {
        return dataId;
    }
}
