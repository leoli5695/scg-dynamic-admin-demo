package com.example.gateway.autoconfig;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.example.gateway.center.nacos.NacosConfigService;
import com.example.gateway.discovery.nacos.NacosDiscoveryService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos auto-configuration for both Config Center and Service Discovery.
 */
@Slf4j
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.cloud.nacos.config")
@ConditionalOnProperty(name = "gateway.center.type", havingValue = "nacos", matchIfMissing = true)
public class NacosCenterAutoConfiguration {

    private String namespace;
    private String serverAddr;

    /**
     * Create Nacos ConfigService bean.
     */
    @Bean
    public ConfigService nacosConfigService() throws Exception {
        Properties props = new Properties();
        props.setProperty("serverAddr", serverAddr);
        if (!namespace.isEmpty()) {
            props.setProperty("namespace", namespace);
        }

        ConfigService configService = NacosFactory.createConfigService(props);
        log.info("Nacos ConfigService initialized with server: {}", serverAddr);
        return configService;
    }

    /**
     * Create Nacos NamingService bean.
     */
    @Bean
    public NamingService nacosNamingService() throws Exception {
        Properties props = new Properties();
        props.setProperty("serverAddr", serverAddr);
        if (!namespace.isEmpty()) {
            props.setProperty("namespace", namespace);
        }

        NamingService namingService = NacosFactory.createNamingService(props);
        log.info("Nacos NamingService initialized with server: {}", serverAddr);
        return namingService;
    }

    /**
     * Create NacosConfigService wrapper bean.
     */
    @Bean
    public NacosConfigService nacosConfigServiceWrapper(ConfigService nacosConfigService) {
        return new NacosConfigService(nacosConfigService);
    }

    /**
     * Create NacosDiscoveryService wrapper bean.
     */
    @Bean
    public NacosDiscoveryService nacosDiscoveryService(NamingService nacosNamingService) {
        return new NacosDiscoveryService(nacosNamingService);
    }
}
