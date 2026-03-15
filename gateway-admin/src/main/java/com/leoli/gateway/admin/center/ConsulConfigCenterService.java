package com.leoli.gateway.admin.center;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.leoli.gateway.admin.properties.GatewayAdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Consul implementation of ConfigCenterService.
 * Auto-configured when gateway.center.type=consul.
 * Uses Consul KV store for configuration management.
 * Stores values as plain UTF-8 JSON strings (Consul KV returns Base64, decoded on read).
 *
 * @author leoli
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "gateway.center.type", havingValue = "consul")
public class ConsulConfigCenterService implements ConfigCenterService {

    @Autowired
    private GatewayAdminProperties properties;

    private ConsulClient consulClient;
    private final ObjectMapper objectMapper;

    public ConsulConfigCenterService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        String host = properties.getConsul().getHost();
        int port = properties.getConsul().getPort();
        String prefix = properties.getConsul().getPrefix();

        this.consulClient = new ConsulClient(host, port);
        log.info("Consul Config Center initialized with host={}, port={}, prefix={}",
                host, port, prefix);
    }

    @PreDestroy
    public void destroy() {
        if (consulClient != null) {
            log.info("Consul Config Center shut down");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String dataId, Class<T> type) {
        try {
            String key = buildKey(dataId);
            GetValue value = consulClient.getKVValue(key).getValue();

            if (value == null) {
                log.debug("No configuration found for dataId: {} (key: {})", dataId, key);
                return null;
            }

            // Consul KV API returns value as Base64-encoded string, decode to UTF-8
            String content = new String(Base64.getDecoder().decode(value.getValue()), StandardCharsets.UTF_8);
            T config = objectMapper.readValue(content, type);
            log.debug("Loaded configuration from Consul: dataId={}, key={}, type={}",
                    dataId, key, type.getSimpleName());
            return config;
        } catch (Exception ex) {
            log.error("Failed to get configuration from Consul: dataId={}, error={}", dataId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to get config from Consul: " + dataId, ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String dataId, com.fasterxml.jackson.core.type.TypeReference<T> typeReference) {
        try {
            String key = buildKey(dataId);
            GetValue value = consulClient.getKVValue(key).getValue();

            if (value == null) {
                log.debug("No configuration found for dataId: {} (key: {})", dataId, key);
                return null;
            }

            // Consul KV API returns value as Base64-encoded string, decode to UTF-8
            String content = new String(Base64.getDecoder().decode(value.getValue()), StandardCharsets.UTF_8);
            T config = objectMapper.readValue(content, typeReference);
            log.debug("Loaded configuration from Consul: dataId={}, key={}, type={}",
                    dataId, key, typeReference.getClass().getSimpleName());
            return config;
        } catch (Exception ex) {
            log.error("Failed to get configuration from Consul: dataId={}, error={}", dataId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to get config from Consul: " + dataId, ex);
        }
    }

    @Override
    public boolean publishConfig(String dataId, Object config) {
        try {
            String key = buildKey(dataId);
            // Serialize to JSON and store as plain UTF-8 string (Consul KV supports plain strings)
            String content = objectMapper.writeValueAsString(config);
            boolean result = consulClient.setKVValue(key, content).getValue();
            if (result) {
                log.info("Published configuration to Consul: key={}, contentLength={}", key, content.length());
            } else {
                log.warn("Failed to publish configuration to Consul: key={}", key);
            }
            return result;
        } catch (Exception ex) {
            log.error("Failed to publish configuration to Consul: dataId={}, error={}", dataId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to publish config to Consul: " + dataId, ex);
        }
    }

    @Override
    public boolean removeConfig(String dataId) {
        try {
            String key = buildKey(dataId);
            consulClient.deleteKVValue(key);
            log.info("Removed configuration from Consul: key={}", key);
            return true;
        } catch (Exception ex) {
            log.error("Failed to remove configuration from Consul: dataId={}, error={}", dataId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to remove config from Consul: " + dataId, ex);
        }
    }

    @Override
    public boolean configExists(String dataId) {
        try {
            String key = buildKey(dataId);
            GetValue value = consulClient.getKVValue(key).getValue();
            boolean exists = value != null;
            log.debug("Configuration {} in Consul: key={}, dataId={}", exists ? "exists" : "not found", key, dataId);
            return exists;
        } catch (Exception ex) {
            log.error("Failed to check configuration in Consul: dataId={}, error={}", dataId, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public String getConfigCenterType() {
        return "consul";
    }

    /**
     * Build Consul KV key from dataId.
     * Example: dataId="gateway-plugins.json" -> key="config/gateway-plugins.json"
     */
    private String buildKey(String dataId) {
        return properties.getConsul().getPrefix() + "/" + dataId;
    }
}
