package com.leoli.gateway.admin.service;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos 元数据同步服务
 */
@Service
@Slf4j
public class NacosMetadataSyncer {
    
    @Autowired(required = false)
    private NamingService namingService;
    
    @Autowired
    private NacosDiscoveryProperties nacosDiscoveryProperties;
    
    @Value("${gateway.health.nacos-sync-enabled:false}")
    private boolean nacosSyncEnabled;
    
    /**
     * 同步健康状态到 Nacos
     */
    public void syncToNacos(String serviceId, String ip, int port, boolean healthy, 
                           String unhealthyReason, String gatewayId) {
        if (!nacosSyncEnabled || namingService == null) {
            log.debug("Nacos sync disabled or NamingService not available");
            return;
        }
        
        try {
            // 构建元数据
            Map<String, String> metadata = new HashMap<>();
            metadata.put("healthy", String.valueOf(healthy));
            metadata.put("unhealthy-source", "GATEWAY_HEALTH_CHECK");
            metadata.put("unhealthy-time", String.valueOf(System.currentTimeMillis()));
            metadata.put("unhealthy-gateway", gatewayId);
            
            if (!healthy && unhealthyReason != null) {
                metadata.put("unhealthy-reason", unhealthyReason);
            }
            
            // 获取当前实例
            List<Instance> instances = namingService.selectInstances(serviceId, true);
            
            // 查找匹配的实例
            for (Instance instance : instances) {
                if (instance.getIp().equals(ip) && instance.getPort() == port) {
                    // 更新元数据
                    updateInstanceMetadata(serviceId, instance, metadata);
                    log.info("Synced instance {}:{}:{} to Nacos with health status: {}", 
                             ip, port, serviceId, healthy ? "HEALTHY" : "UNHEALTHY");
                    return;
                }
            }
            
            log.warn("Instance {}:{} not found in Nacos for service {}", ip, port, serviceId);
            
        } catch (Exception e) {
            log.error("Failed to sync instance {}:{} to Nacos", ip, port, e);
            // 不抛出异常，保证主流程正常
        }
    }
    
    /**
     * 更新实例元数据
     */
    private void updateInstanceMetadata(String serviceName, Instance instance, 
                                       Map<String, String> newMetadata) throws Exception {
        // 合并现有元数据
        Map<String, String> mergedMetadata = new HashMap<>(instance.getMetadata());
        mergedMetadata.putAll(newMetadata);
        
        // 创建新实例对象并设置元数据
        Instance newInstance = new Instance();
        newInstance.setIp(instance.getIp());
        newInstance.setPort(instance.getPort());
        newInstance.setWeight(instance.getWeight());
        newInstance.setHealthy(instance.isHealthy());
        newInstance.setEphemeral(instance.isEphemeral());
        newInstance.setClusterName(instance.getClusterName());
        newInstance.setServiceName(serviceName);
        newInstance.setMetadata(mergedMetadata);
        
        // 注册实例（会覆盖原有元数据）
        namingService.registerInstance(serviceName, newInstance);
    }
    
    /**
     * 批量同步多个实例
     */
    public void batchSyncToNacos(List<Map<String, Object>> instances, String gatewayId) {
        if (!nacosSyncEnabled || namingService == null) {
            return;
        }
        
        for (Map<String, Object> instance : instances) {
            try {
                String serviceId = (String) instance.get("serviceId");
                String ip = (String) instance.get("ip");
                Integer port = (Integer) instance.get("port");
                Boolean healthy = (Boolean) instance.get("healthy");
                String reason = (String) instance.get("unhealthyReason");
                
                syncToNacos(serviceId, ip, port, healthy != null ? healthy : true, 
                           reason, gatewayId);
            } catch (Exception e) {
                log.error("Failed to sync instance to Nacos", e);
            }
        }
    }
}
