package com.leoli.gateway.admin.controller;

import com.leoli.gateway.admin.model.AuditLogEntity;
import com.leoli.gateway.admin.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Audit log controller for querying configuration change history.
 *
 * @author leoli
 */
@Slf4j
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Get all audit logs with optional filters.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Use JPA repository methods for filtering and pagination
            List<AuditLogEntity> logs;
            
            if (targetType != null && !targetType.isEmpty() && targetId != null && !targetId.isEmpty()) {
                // Filter by both targetType and targetId
                logs = auditLogRepository.findByTargetTypeAndTargetId(targetType, targetId);
            } else {
                // Get recent logs (no filter or single filter)
                logs = auditLogRepository.findTop10ByOrderByCreatedAtDesc();
            }
            
            // Apply additional filters in memory (simplified approach)
            final String finalTargetType = targetType;
            final String finalTargetId = targetId;
            final String finalOperationType = operationType;
            final LocalDateTime finalStartTime = startTime;
            final LocalDateTime finalEndTime = endTime;
            
            logs = logs.stream()
                .filter(log -> finalTargetType == null || finalTargetType.isEmpty() || log.getTargetType().equals(finalTargetType))
                .filter(log -> finalTargetId == null || finalTargetId.isEmpty() || log.getTargetId().equals(finalTargetId))
                .filter(log -> finalOperationType == null || finalOperationType.isEmpty() || log.getOperationType().equals(finalOperationType))
                .filter(log -> finalStartTime == null || log.getCreatedAt().isAfter(finalStartTime))
                .filter(log -> finalEndTime == null || log.getCreatedAt().isBefore(finalEndTime))
                .collect(Collectors.toList());
            
            // Apply pagination
            int total = logs.size();
            int offset = (page - 1) * size;
            int endIdx = Math.min(offset + size, total);
            if (offset < total) {
                logs = logs.subList(offset, endIdx);
            } else {
                logs = List.of();
            }
            
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", Map.of(
                "logs", logs,
                "total", total,
                "page", page,
                "size", size
            ));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception ex) {
            log.error("Failed to query audit logs", ex);
            result.put("code", 500);
            result.put("message", "Failed to query audit logs: " + ex.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Get audit log by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAuditLogById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            AuditLogEntity log = auditLogRepository.findById(id)
                .orElse(null);
            
            if (log != null) {
                result.put("code", 200);
                result.put("message", "success");
                result.put("data", log);
                return ResponseEntity.ok(result);
            } else {
                result.put("code", 404);
                result.put("message", "Audit log not found: " + id);
                return ResponseEntity.status(404).body(result);
            }
        } catch (Exception ex) {
            log.error("Failed to get audit log", ex);
            result.put("code", 500);
            result.put("message", "Failed to get audit log: " + ex.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
