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
        // Don't set ID - let database auto-generate it
        entity.setServiceName(service.getServiceId());  // Use serviceId as business name
        // Store all configuration in metadata field
        entity.setEnabled(true);
        entity.setMetadata(convertToJson(service.getMetadata()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        return entity;
    }

    /**
     * Convert ServiceEntity to ServiceDefinition.
     * Note: This method is kept for backward compatibility but may not be used in current architecture.
     */
    public org.springframework.cloud.client.ServiceInstance toServiceInstance(ServiceEntity entity) {
        if (entity == null) {
            return null;
        }

        // Return a minimal ServiceInstance implementation
        // Actual service instances are loaded from Nacos, not from database
        return new org.springframework.cloud.client.ServiceInstance() {
            @Override
            public String getInstanceId() {
                return entity.getServiceId() != null ? entity.getServiceId() : String.valueOf(entity.getId());
            }

            @Override
            public String getServiceId() {
                return entity.getServiceName();
            }

            @Override
            public String getHost() {
                return null;  // Not stored in database anymore
            }

            @Override
            public int getPort() {
                return 0;  // Not stored in database anymore
            }

            @Override
            public boolean isSecure() {
                return false;  // Not stored in database anymore
            }

            @Override
            public java.net.URI getUri() {
                return null;  // Not stored in database anymore
            }

            @Override
            public java.util.Map<String, String> getMetadata() {
                return convertFromJson(entity.getMetadata(), java.util.Map.class);
            }

            @Override
            public String getScheme() {
                return "http";
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
