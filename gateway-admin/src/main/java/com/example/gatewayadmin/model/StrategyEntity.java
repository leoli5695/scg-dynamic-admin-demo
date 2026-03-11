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
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "strategy_type", nullable = false, length = 100)
    private String strategyType;
    
    @Column(name = "route_id", length = 255)
    private String routeId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String config;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean enabled;
    
    @Column
    private Integer priority;
    
    @Column(length = 500)
    private String description;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
