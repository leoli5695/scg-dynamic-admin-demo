package com.example.gatewayadmin.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Service entity for database persistence.
 *
 * @author leoli
 */
@Data
@Entity(name = "services")
@Table(name = "services")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;
    
    @Column(length = 1024)
    private String uri;
    
    @Column(length = 255)
    private String host;
    
    @Column
    private Integer port;
    
    @Column(name = "load_balancer", length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'round_robin'")
    private String loadBalancer;
    
    @Column(name = "health_check_url", length = 1024)
    private String healthCheckUrl;
    
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
