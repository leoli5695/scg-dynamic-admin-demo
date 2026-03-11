package com.example.gatewayadmin.model;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, length = 1024)
    private String uri;
    
    @Column(columnDefinition = "TEXT")
    private String predicates;
    
    @Column(columnDefinition = "TEXT")
    private String filters;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "order_num", columnDefinition = "INT DEFAULT 0")
    private Integer orderNum;
    
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
