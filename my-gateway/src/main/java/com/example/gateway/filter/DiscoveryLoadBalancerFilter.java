package com.example.gateway.filter;

import com.example.gateway.discovery.spi.DiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

/**
 * Load Balancer Filter based on Service Discovery
 * Replaces Spring Cloud Gateway's built-in ReactiveLoadBalancerClientFilter
 *
 * @author leoli
 * @version 1.0
 */
@Slf4j
@Component
public class DiscoveryLoadBalancerFilter implements GlobalFilter, Ordered {

    private final LoadBalancerClientFactory clientFactory;
    private final DiscoveryService discoveryService;

    public DiscoveryLoadBalancerFilter(LoadBalancerClientFactory clientFactory, DiscoveryService discoveryService) {
        this.clientFactory = clientFactory;
        this.discoveryService = discoveryService;
        log.info("DiscoveryLoadBalancerFilter initialized with DiscoveryService: {}", discoveryService.getCenterType());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = (URI) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String schemePrefix = (String) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR);

        // Only process requests with lb:// protocol
        if (url != null && ("lb".equals(url.getScheme()) || "lb".equals(schemePrefix))) {
            ServerWebExchangeUtils.addOriginalRequestUrl(exchange, url);

            if (log.isTraceEnabled()) {
                log.trace("DiscoveryLoadBalancerFilter url before: " + url);
            }

            URI requestUri = (URI) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            String serviceId = requestUri.getHost();

            // Get service instances from DiscoveryService
            return chooseFromDiscovery(serviceId, exchange)
                    .flatMap(response -> {
                        if (!response.hasServer()) {
                            throw NotFoundException.create(true,
                                    "Unable to find instance for " + url.getHost());
                        }

                        ServiceInstance retrievedInstance = response.getServer();
                        URI uri = exchange.getRequest().getURI();
                        String overrideScheme = retrievedInstance.isSecure() ? "https" : "http";
                        if (schemePrefix != null) {
                            overrideScheme = url.getScheme();
                        }

                        DelegatingServiceInstance serviceInstance =
                                new DelegatingServiceInstance(retrievedInstance, overrideScheme);
                        URI requestUrl = reconstructURI(serviceInstance, uri);

                        if (log.isTraceEnabled()) {
                            log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
                        }

                        exchange.getAttributes().put(
                                ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, requestUrl);

                        return chain.filter(exchange);
                    });
        } else {
            return chain.filter(exchange);
        }
    }

    /**
     * Choose service instance from DiscoveryService
     */
    private Mono<Response<ServiceInstance>> chooseFromDiscovery(String serviceId, ServerWebExchange exchange) {
        log.debug("Choosing instance for service: {} from {}", serviceId, discoveryService.getCenterType());

        try {
            // Get healthy instances directly from DiscoveryService
            List<DiscoveryService.ServiceInstance> instances = discoveryService.getHealthyInstances(serviceId);

            if (CollectionUtils.isEmpty(instances)) {
                log.warn("No instances found for service: {} in {}", serviceId, discoveryService.getCenterType());
                return Mono.empty();
            }

            log.info("Found {} instance(s) for service: {} in {}", instances.size(), serviceId, discoveryService.getCenterType());

            // Convert our ServiceInstance to Spring Cloud ServiceInstance
            List<ServiceInstance> serviceInstances = instances.stream()
                    .map(this::convertToSpringServiceInstance)
                    .toList();

            if (serviceInstances.isEmpty()) {
                log.warn("No healthy/enabled instances for service: {}", serviceId);
                return Mono.empty();
            }

            // Use SCG's built-in load balancer to select instance
            ReactorServiceInstanceLoadBalancer loadBalancer =
                    clientFactory.getInstance(serviceId, ReactorServiceInstanceLoadBalancer.class);

            if (loadBalancer == null) {
                log.warn("No loadbalancer available for {}, using weighted round-robin", serviceId);
                // If no load balancer is configured, use weighted round-robin strategy
                DiscoveryService.ServiceInstance selected = selectByWeightedRoundRobin(instances);
                ServiceInstance serviceInstance = convertToSpringServiceInstance(selected);
                return Mono.just(new SimpleResponse<>(serviceInstance));
            }

            // Build Request object for the load balancer
            RequestData requestData = new RequestData(exchange.getRequest());
            RequestDataContext requestContext = new RequestDataContext(requestData, getHint(serviceId));
            DefaultRequest<RequestDataContext> lbRequest = new DefaultRequest<>(requestContext);

            // Use SCG's load balancer
            return loadBalancer.choose(lbRequest)
                    .switchIfEmpty(Mono.fromSupplier(() -> {
                        // If load balancer returns empty, use weighted round-robin
                        DiscoveryService.ServiceInstance selected = selectByWeightedRoundRobin(instances);
                        return new SimpleResponse<>(convertToSpringServiceInstance(selected));
                    }));

        } catch (Exception e) {
            log.error("Error choosing instance from {} for service: {}", discoveryService.getCenterType(), serviceId, e);
            return Mono.error(e);
        }
    }

    /**
     * Convert our ServiceInstance to Spring Cloud ServiceInstance
     */
    private ServiceInstance convertToSpringServiceInstance(DiscoveryService.ServiceInstance instance) {
        return new ServiceInstance() {
            @Override
            public String getServiceId() {
                return instance.getServiceId();
            }

            @Override
            public String getHost() {
                return instance.getHost();
            }

            @Override
            public int getPort() {
                return instance.getPort();
            }

            @Override
            public boolean isSecure() {
                return false; // Can be extended based on metadata
            }

            @Override
            public URI getUri() {
                try {
                    return new URI("http://" + instance.getHost() + ":" + instance.getPort());
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to create URI", e);
                }
            }

            @Override
            public java.util.Map<String, String> getMetadata() {
                return null; // Metadata not supported in new ServiceInstance
            }

            @Override
            public String getInstanceId() {
                return instance.getHost() + ":" + instance.getPort(); // Generate instanceId from host:port
            }
        };
    }

    /**
     * Select instance by weighted round-robin strategy
     */
    private DiscoveryService.ServiceInstance selectByWeightedRoundRobin(List<DiscoveryService.ServiceInstance> instances) {
        if (instances.size() == 1) {
            return instances.get(0);
        }

        // Calculate total weight
        double totalWeight = instances.stream()
                .mapToDouble(DiscoveryService.ServiceInstance::getWeight)
                .sum();

        // Calculate average weight
        double avgWeight = totalWeight / instances.size();

        // If all weights are the same, use normal round-robin
        boolean sameWeight = instances.stream()
                .allMatch(inst -> Math.abs(inst.getWeight() - avgWeight) < 0.01);
        if (sameWeight) {
            int index = (int) (System.currentTimeMillis() / 1000) % instances.size();
            return instances.get(index);
        }

        // Select randomly based on weight proportion
        double random = Math.random() * totalWeight;
        double weightSum = 0;

        for (DiscoveryService.ServiceInstance instance : instances) {
            weightSum += instance.getWeight();
            if (random <= weightSum) {
                log.debug("Selected instance {}:{} with weight {} (total: {})",
                        instance.getHost(), instance.getPort(), instance.getWeight(), totalWeight);
                return instance;
            }
        }

        return instances.get(instances.size() - 1);
    }

    /**
     * Reconstruct URI
     */
    protected URI reconstructURI(ServiceInstance serviceInstance, URI original) {
        return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
    }

    /**
     * Get Hint
     */
    private String getHint(String serviceId) {
        LoadBalancerProperties loadBalancerProperties = clientFactory.getProperties(serviceId);
        java.util.Map<String, String> hints = loadBalancerProperties.getHint();
        String defaultHint = hints.getOrDefault("default", "default");
        String hintPropertyValue = hints.get(serviceId);
        return hintPropertyValue != null ? hintPropertyValue : defaultHint;
    }

    @Override
    public int getOrder() {
        return 10150; // Same priority as native Filter
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
     * Wrapper for ServiceInstance with overrideable scheme
     */
    private static class DelegatingServiceInstance implements ServiceInstance {
        private final ServiceInstance delegate;
        private final String scheme;

        DelegatingServiceInstance(ServiceInstance delegate, String scheme) {
            this.delegate = delegate;
            this.scheme = scheme;
        }

        @Override
        public String getServiceId() {
            return delegate.getServiceId();
        }

        @Override
        public String getHost() {
            return delegate.getHost();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public boolean isSecure() {
            return "https".equals(scheme);
        }

        @Override
        public URI getUri() {
            return delegate.getUri();
        }

        @Override
        public java.util.Map<String, String> getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public String getInstanceId() {
            return delegate.getInstanceId();
        }
    }
}
