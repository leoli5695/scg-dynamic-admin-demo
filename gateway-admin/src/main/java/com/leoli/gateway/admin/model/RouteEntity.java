package com.leoli.gateway.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Route entity for database persistence.
 *
 * @author leoli
 */
@Data
@Entity(name = "routes")
@Table(name = "routes")
public class RouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Business route name (e.g., "user-route"), used in Nacos config key.
     */
    @Column(name = "route_name", nullable = false, unique = true, length = 255)
    private String routeName;
    
    /**
     * Route ID (UUID), kept for backward compatibility.
     */
    @Column(name = "route_id", length = 255)
    private String routeId;
    
    /**
     * Complete configuration as JSON backup.
     * Contains: id, uri, predicates, filters, metadata, order, etc.
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean enabled;
    
    @Column(length = 500)
    private String description;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
