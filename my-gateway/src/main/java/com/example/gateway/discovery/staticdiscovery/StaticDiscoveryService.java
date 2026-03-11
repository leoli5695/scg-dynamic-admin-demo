package com.example.gateway.discovery.staticdiscovery;

import com.example.gateway.manager.ServiceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * Static Discovery Service implementation.
 * Supports both static configuration (gateway-services.json) and dynamic discovery (Nacos).
 */
@Slf4j
@Component
public class StaticDiscoveryService {

    private final ServiceManager serviceManager;
    private final DiscoveryClient discoveryClient;

    public StaticDiscoveryService(ServiceManager serviceManager, DiscoveryClient discoveryClient) {
        this.serviceManager = serviceManager;
        this.discoveryClient = discoveryClient;
        log.info("StaticDiscoveryService initialized with ServiceManager and Nacos DiscoveryClient");
    }

    /**
     * Get all healthy instances for a service.
     * Priority:
     * 1. Static configuration from gateway-services.json (via ServiceManager)
     * 2. Dynamic discovery from Nacos (via DiscoveryClient)
     *
     * @param serviceId service name
     * @return list of service instances
     */
    public List<ServiceInstance> getInstances(String serviceId) {
        // First, try to get from static configuration (gateway-services.json)
        if (serviceManager.isCacheValid()) {
            List<ServiceManager.ServiceInstance> staticInstances =
                    serviceManager.getServiceInstances(serviceId);

            if (staticInstances != null && !staticInstances.isEmpty()) {
                log.debug("Found {} static instance(s) for service: {}", staticInstances.size(), serviceId);
                return staticInstances.stream()
                        .map(StaticServiceInstance::new)
                        .collect(java.util.stream.Collectors.toList());
            }
        }

        // Fallback: Get from Nacos DiscoveryClient
        log.debug("No static config found, getting instances for service: {} from Nacos", serviceId);
        List<ServiceInstance> nacosInstances = discoveryClient.getInstances(serviceId);

        if (nacosInstances != null && !nacosInstances.isEmpty()) {
            log.debug("Found {} instance(s) for service: {} from Nacos", nacosInstances.size(), serviceId);
        }

        return nacosInstances != null ? nacosInstances : Collections.emptyList();
    }

    /**
     * Create a load balancer for the given service.
     * This allows SCG to use its built-in load balancing strategies.
     */
    public ReactorServiceInstanceLoadBalancer createLoadBalancer(String serviceId) {
        return new StaticLoadBalancer(serviceId, this);
    }

    /**
     * Wrapper for ServiceManager.ServiceInstance to implement Spring Cloud ServiceInstance
     */
    private static class StaticServiceInstance implements ServiceInstance {

        private final ServiceManager.ServiceInstance delegate;

        StaticServiceInstance(ServiceManager.ServiceInstance delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getServiceId() {
            return delegate.getServiceName();
        }

        @Override
        public String getHost() {
            return delegate.getIp();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public java.net.URI getUri() {
            try {
                return new java.net.URI("http://" + delegate.getIp() + ":" + delegate.getPort());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create URI", e);
            }
        }

        @Override
        public java.util.Map<String, String> getMetadata() {
            return java.util.Map.of(
                    "weight", String.valueOf(delegate.getWeight()),
                    "healthy", String.valueOf(delegate.isHealthy()),
                    "enabled", String.valueOf(delegate.isEnabled())
            );
        }

        @Override
        public String getInstanceId() {
            return delegate.getIp() + ":" + delegate.getPort();
        }
    }

    /**
     * Simple load balancer that delegates to SCG's load balancer.
     */
    private static class StaticLoadBalancer implements ReactorServiceInstanceLoadBalancer {

        private final String serviceId;
        private final StaticDiscoveryService discoveryService;

        StaticLoadBalancer(String serviceId, StaticDiscoveryService discoveryService) {
            this.serviceId = serviceId;
            this.discoveryService = discoveryService;
        }

        @Override
        public Mono<Response<ServiceInstance>> choose(Request request) {
            List<ServiceInstance> instances = discoveryService.getInstances(serviceId);

            if (instances.isEmpty()) {
                return Mono.just(new EmptyResponse<>());
            }

            // If only one instance, return it directly
            if (instances.size() == 1) {
                return Mono.just(new SimpleResponse<>(instances.get(0)));
            }

            // For multiple instances, use round-robin (simple implementation)
            // In production, you would delegate to SCG's configured load balancer
            int index = (int) (System.currentTimeMillis() / 1000) % instances.size();
            return Mono.just(new SimpleResponse<>(instances.get(index)));
        }
    }

    /**
     * Simple Response implementation
     */
    private static class SimpleResponse<T> implements Response<T> {
        private final T server;

        SimpleResponse(T server) {
            this.server = server;
        }

        @Override
        public boolean hasServer() {
            return server != null;
        }

        @Override
        public T getServer() {
            return server;
        }
    }

    /**
     * Empty Response when no instances available
     */
    private static class EmptyResponse<T> implements Response<T> {
        @Override
        public boolean hasServer() {
            return false;
        }

        @Override
        public T getServer() {
            return null;
        }
    }
}
