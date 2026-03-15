package com.leoli.gateway.admin.model;

import lombok.Data;

/**
 * Route response DTO with both route name and UUID.
 */
@Data
public class RouteResponse {
    
    /**
     * Route ID (UUID - system identifier, used for deletion)
     */
    private String id;
    
    /**
     * Route name (business identifier)
     */
    private String routeName;
    
    /**
     * Target URI
     */
    private String uri;
    
    /**
     * Route order
     */
    private int order;
    
    /**
     * Route predicates
     */
    private java.util.List<RouteDefinition.PredicateDefinition> predicates;
    
    /**
     * Route filters
     */
    private java.util.List<RouteDefinition.FilterDefinition> filters;
    
    /**
     * Metadata
     */
    private java.util.Map<String, Object> metadata;
    
    /**
     * Enabled status
     */
    private Boolean enabled;
    
    /**
     * Description
     */
    private String description;
}
