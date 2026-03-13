package com.example.gatewayadmin.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Strategy entity for database persistence.
 *
 * @author leoli
 */
@Data
@Entity(name = "strategies")
@Table(name = "strategies")
public class StrategyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Business strategy name (e.g., "user-route-rate-limiter"), used to identify this strategy.
     */
    @Column(name = "strategy_name", nullable = false, unique = true, length = 255)
    private String strategyName;
    
    /**
     * Strategy ID (UUID), kept for backward compatibility.
     */
    @Column(name = "strategy_id", length = 255)
    private String strategyId;
    
    /**
     * Complete configuration as JSON backup.
     * Contains: type, routeId, config, priority, etc.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
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
