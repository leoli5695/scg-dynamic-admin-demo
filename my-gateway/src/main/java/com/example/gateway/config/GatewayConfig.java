package com.example.gateway.config;

import com.example.gateway.filter.CustomLoadBalancerGatewayFilterFactory;
import com.example.gateway.manager.RouteManager;
import com.example.gateway.route.DynamicRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class GatewayConfig {

    @Bean
    public CustomLoadBalancerGatewayFilterFactory customLoadBalancerGatewayFilterFactory(Environment environment) {
        return new CustomLoadBalancerGatewayFilterFactory(environment);
    }

    @Bean
    public DynamicRouteDefinitionLocator dynamicRouteDefinitionLocator(RouteManager routeManager,
                                                                        ApplicationEventPublisher eventPublisher) {
        return new DynamicRouteDefinitionLocator(routeManager, eventPublisher);
    }
}
