package com.example.gatewayadmin.repository;

import com.example.gatewayadmin.model.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AuditLog repository interface.
 *
 * @author leoli
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    
    /**
     * Find audit logs by target type and ID.
     */
    List<AuditLogEntity> findByTargetTypeAndTargetId(String targetType, String targetId);
    
    /**
     * Find recent audit logs.
     */
    List<AuditLogEntity> findTop10ByOrderByCreatedAtDesc();
}
