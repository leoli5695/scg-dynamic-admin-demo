package com.leoli.gateway.admin.converter;

import com.leoli.gateway.admin.model.RouteEntity;
import com.leoli.gateway.admin.model.RouteDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Converter between RouteDefinition and RouteEntity.
 *
 * @author leoli
 */
@Slf4j
@Component
public class RouteConverter {

    /**
     * Convert RouteDefinition to RouteEntity.
     */
    public RouteEntity toEntity(RouteDefinition route) {
        if (route == null) {
            return null;
        }

        RouteEntity entity = new RouteEntity();
        // Don't set ID - let database auto-generate it
        entity.setRouteName(route.getId());  // Use route ID as business route name
        
        // Store complete configuration in metadata field
        try {
            String configJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(route);
            entity.setMetadata(configJson);
        } catch (Exception e) {
            log.warn("Failed to serialize route config to JSON", e);
        }
        
        entity.setEnabled(true); // Default to enabled
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        return entity;
    }

    /**
     * Convert RouteEntity to RouteDefinition.
     */
    public RouteDefinition toDefinition(RouteEntity entity) {
        if (entity == null) {
            return null;
        }

        // Try to restore from metadata JSON first
        if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
            try {
                RouteDefinition route = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(entity.getMetadata(), RouteDefinition.class);
                if (route != null) {
                    return route;
                }
            } catch (Exception e) {
                log.warn("Failed to deserialize route config from JSON, using fallback", e);
            }
        }
        
        // Fallback: create minimal definition
        RouteDefinition route = new RouteDefinition();
        route.setId(entity.getRouteName());
        return route;
    }

    /**
     * Batch convert RouteDefinitions to RouteEntities.
     */
    public List<RouteEntity> toEntities(List<RouteDefinition> routes) {
        if (routes == null) {
            return new ArrayList<>();
        }
        List<RouteEntity> entities = new ArrayList<>(routes.size());
        for (RouteDefinition route : routes) {
            entities.add(toEntity(route));
        }
        return entities;
    }

    /**
     * Batch convert RouteEntities to RouteDefinitions.
     */
    public List<RouteDefinition> toDefinitions(List<RouteEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        List<RouteDefinition> routes = new ArrayList<>(entities.size());
        for (RouteEntity entity : entities) {
            routes.add(toDefinition(entity));
        }
        return routes;
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
