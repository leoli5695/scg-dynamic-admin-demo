package com.example.gatewayadmin.controller;

import com.example.gatewayadmin.center.NacosConfigCenterService;
import com.example.gatewayadmin.model.ServiceDefinition;
import com.example.gatewayadmin.service.ServiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service management controller.
 *
 * @author leoli
 */
@Slf4j
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    @Autowired
    private ServiceService serviceManager;

    @Autowired
    private NacosConfigCenterService nacosConfigCenterService;

    /**
     * Get all services.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllServices() {
        List<ServiceDefinition> services = serviceManager.getAllServices();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", services);
        return ResponseEntity.ok(result);
    }

    /**
     * Get service by name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getServiceByName(@PathVariable String name) {
        ServiceDefinition service = serviceManager.getServiceByName(name);
        Map<String, Object> result = new HashMap<>();
        if (service != null) {
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", service);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "Service not found: " + name);
            return ResponseEntity.status(404).body(result);
        }
    }

    /**
     * Register a service.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createService(@RequestBody ServiceDefinition service) {
        log.info("Creating service: {}", service.getName());
        boolean success = serviceManager.createService(service);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Service registered successfully");
            result.put("data", service);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to register service");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Update a service.
     */
    @PutMapping("/{name}")
    public ResponseEntity<Map<String, Object>> updateService(@PathVariable String name,
                                                             @RequestBody ServiceDefinition service) {
        log.info("Updating service: {}", name);
        boolean success = serviceManager.updateService(name, service);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Service updated successfully");
            result.put("data", service);
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "Service not found: " + name);
            return ResponseEntity.status(404).body(result);
        }
    }

    /**
     * Delete a service.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable String name) {
        log.info("Deleting service: {}", name);
        boolean success = serviceManager.deleteService(name);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Service deleted successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to delete service");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Add a service instance.
     */
    @PostMapping("/{name}/instances")
    public ResponseEntity<Map<String, Object>> addServiceInstance(
            @PathVariable String name,
            @RequestBody ServiceDefinition.ServiceInstance instance) {
        log.info("Adding instance to service {}: {}:{}", name, instance.getIp(), instance.getPort());
        boolean success = serviceManager.addServiceInstance(name, instance);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Instance added successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "Service not found: " + name);
            return ResponseEntity.status(404).body(result);
        }
    }

    /**
     * Remove a service instance.
     */
    @DeleteMapping("/{name}/instances/{instanceId}")
    public ResponseEntity<Map<String, Object>> removeServiceInstance(
            @PathVariable String name,
            @PathVariable String instanceId) {
        log.info("Removing instance {} from service {}", instanceId, name);
        boolean success = serviceManager.removeServiceInstance(name, instanceId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Instance removed successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 500);
            result.put("message", "Failed to remove instance");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Update service instance status (healthy/enabled).
     */
    @PutMapping("/{name}/instances/{instanceId}/status")
    public ResponseEntity<Map<String, Object>> updateInstanceStatus(
            @PathVariable String name,
            @PathVariable String instanceId,
            @RequestParam boolean healthy,
            @RequestParam boolean enabled) {
        log.info("Updating instance {} status: healthy={}, enabled={}", instanceId, healthy, enabled);
        boolean success = serviceManager.updateInstanceStatus(name, instanceId, healthy, enabled);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Instance status updated successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("code", 404);
            result.put("message", "Service or instance not found");
            return ResponseEntity.status(404).body(result);
        }
    }

    /**
     * Get service statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getServiceStats() {
        ServiceService.ServiceStats stats = serviceManager.getServiceStats();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", stats);
        return ResponseEntity.ok(result);
    }

    /**
     * Get registered service names from Nacos service discovery.
     */
    @GetMapping("/nacos-discovery")
    public ResponseEntity<Map<String, Object>> getNacosDiscoveryServices() {
        List<String> services = nacosConfigCenterService.getDiscoveryServiceNames();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", services);
        return ResponseEntity.ok(result);
    }
}
