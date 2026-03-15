package com.leoli.gateway.admin.service;

import com.leoli.gateway.admin.model.AuditLogEntity;
import com.leoli.gateway.admin.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Audit log service for recording configuration changes.
 *
 * @author leoli
 */
@Slf4j
@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Record an audit log asynchronously.
     */
    @Async
    public void recordAuditLog(String operator, String operationType, String targetType, 
                               String targetId, String oldValue, String newValue, String ipAddress) {
        try {
            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setOperator(operator);
            auditLog.setOperationType(operationType);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setOldValue(oldValue);
            auditLog.setNewValue(newValue);
            auditLog.setIpAddress(ipAddress);
            
            auditLogRepository.save(auditLog);
            log.debug("Audit log recorded: {} {} {} by {}", operationType, targetType, targetId, operator);
        } catch (Exception ex) {
            log.error("Failed to record audit log", ex);
        }
    }

    /**
     * Record audit log with minimal parameters.
     */
    @Async
    public void recordAuditLog(String operator, String operationType, String targetType, 
                               String targetId, String ipAddress) {
        recordAuditLog(operator, operationType, targetType, targetId, null, null, ipAddress);
    }
}
