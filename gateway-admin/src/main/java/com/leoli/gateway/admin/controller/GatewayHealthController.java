package com.leoli.gateway.admin.controller;

import com.leoli.gateway.admin.dto.InstanceHealthDTO;
import com.leoli.gateway.admin.service.InstanceHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 网关健康状态管理 Controller
 */
@RestController
@RequestMapping("/api/gateway")
@Slf4j
public class GatewayHealthController {
    
    @Autowired
    private InstanceHealthService instanceHealthService;
    
    /**
     * 接收网关同步的健康状态
     */
    @PostMapping("/health/sync")
    public ResponseEntity<Void> syncHealth(
            @RequestBody List<InstanceHealthDTO> healthList,
            @RequestHeader(value = "X-Gateway-Id", required = false) String gatewayId) {
        
        log.info("Received {} instance health status from gateway {}", 
                 healthList.size(), gatewayId != null ? gatewayId : "unknown");
        
        for (InstanceHealthDTO health : healthList) {
            if (!health.isHealthy()) {
                // 处理不健康实例
                instanceHealthService.handleUnhealthyInstance(health, gatewayId);
            }
        }
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * 查询服务的实例健康状态（供前端调用）
     */
    @GetMapping("/services/{serviceId}/instances/health")
    public ResponseEntity<List<InstanceHealthDTO>> getServiceHealth(
            @PathVariable String serviceId) {
        
        List<InstanceHealthDTO> healthList = 
            instanceHealthService.getServiceInstanceHealth(serviceId);
        
        return ResponseEntity.ok(healthList);
    }
    
    /**
     * 获取所有服务的健康状态概览
     */
    @GetMapping("/health/overview")
    public ResponseEntity<Map<String, Object>> getHealthOverview() {
        Map<String, Object> overview = instanceHealthService.getHealthOverview();
        return ResponseEntity.ok(overview);
    }
}
