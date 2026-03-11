package com.example.gatewayadmin.converter;

import com.example.gatewayadmin.model.ServiceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Converter between ServiceDefinition and ServiceEntity.
 *
 * @author leoli
 */
@Slf4j
@Component
public class ServiceConverter {

    /**
     * Convert ServiceDefinition to ServiceEntity.
     */
    public ServiceEntity toEntity(org.springframework.cloud.client.ServiceInstance service) {
        if (service == null) {
            return null;
        }

        ServiceEntity entity = new ServiceEntity();
        entity.setId(service.getInstanceId());
        entity.setName(service.getServiceId());
        entity.setUri(service.getUri() != null ? service.getUri().toString() : null);
        entity.setHost(service.getHost());
        entity.setPort(service.getPort());
        entity.setEnabled(true);
        entity.setMetadata(convertToJson(service.getMetadata()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        return entity;
    }

    /**
     * Convert ServiceEntity to ServiceDefinition.
     */
    public org.springframework.cloud.client.ServiceInstance toServiceInstance(ServiceEntity entity) {
        if (entity == null) {
            return null;
        }

        return new org.springframework.cloud.client.ServiceInstance() {
            @Override
            public String getInstanceId() {
                return entity.getId();
            }

            @Override
            public String getServiceId() {
                return entity.getName();
            }

            @Override
            public String getHost() {
                return entity.getHost();
            }

            @Override
            public int getPort() {
                return entity.getPort();
            }

            @Override
            public boolean isSecure() {
                return entity.getUri() != null && entity.getUri().startsWith("https");
            }

            @Override
            public java.net.URI getUri() {
                return entity.getUri() != null ? java.net.URI.create(entity.getUri()) : null;
            }

            @Override
            public java.util.Map<String, String> getMetadata() {
                return convertFromJson(entity.getMetadata(), java.util.Map.class);
            }

            @Override
            public String getScheme() {
                return isSecure() ? "https" : "http";
            }
        };
    }

    /**
     * Helper method to convert object to JSON string.
     */
    private String convertToJson(Object obj) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error converting object to JSON", e);
            return null;
        }
    }

    /**
     * Helper method to convert JSON string to object.
     */
    @SuppressWarnings("unchecked")
    private <T> T convertFromJson(String json, Class<T> clazz) {
        try {
            if (json == null || json.isEmpty()) {
                return null;
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Error converting JSON to object", e);
            return null;
        }
    }
}
