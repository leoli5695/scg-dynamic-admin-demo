package com.leoli.gateway.admin.service;

import com.leoli.gateway.admin.alert.AlertLevel;
import com.leoli.gateway.admin.alert.AlertNotifier;
import com.leoli.gateway.admin.dto.InstanceHealthDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警服务
 */
@Service
@Slf4j
public class AlertService {
    
    @Autowired
    private List<AlertNotifier> notifiers;
    
    /**
     * 发送告警（所有启用的通知器）
     */
    public void sendAlert(String title, String content, AlertLevel level) {
        for (AlertNotifier notifier : notifiers) {
            if (notifier.isSupported()) {
                try {
                    notifier.sendAlert(title, content, level);
                } catch (Exception e) {
                    log.error("Notifier {} failed to send alert", 
                             notifier.getClass().getSimpleName(), e);
                }
            }
        }
    }
    
    /**
     * 发送实例不健康告警
     */
    public void sendInstanceUnhealthyAlert(InstanceHealthDTO health) {
        String title = String.format("实例不健康：%s:%d", health.getIp(), health.getPort());
        String content = buildUnhealthyContent(health);
        
        sendAlert(title, content, AlertLevel.ERROR);
    }
    
    /**
     * 构建不健康告警内容
     */
    private String buildUnhealthyContent(InstanceHealthDTO health) {
        StringBuilder sb = new StringBuilder();
        sb.append("服务 ID: ").append(health.getServiceId()).append("\n");
        sb.append("IP 地址：").append(health.getIp()).append("\n");
        sb.append("端  口：").append(health.getPort()).append("\n");
        
        if (health.getUnhealthyReason() != null) {
            sb.append("原  因：").append(health.getUnhealthyReason()).append("\n");
        }
        
        sb.append("失败次数：").append(health.getConsecutiveFailures()).append("\n");
        sb.append("检查类型：").append(health.getCheckType()).append("\n");
        
        if (health.getLastRequestTime() != null) {
            sb.append("最后请求：").append(new java.util.Date(health.getLastRequestTime())).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 发送严重告警（如多个实例同时不健康）
     */
    public void sendCriticalAlert(String serviceName, int unhealthyCount) {
        String title = String.format("【严重】服务 %s 多个实例不健康", serviceName);
        String content = String.format(
            "服务 %s 有 %d 个实例被标记为不健康，请立即检查！\n\n" +
            "可能原因：\n" +
            "1. 服务整体故障\n" +
            "2. 网络问题\n" +
            "3. 网关配置错误",
            serviceName, unhealthyCount
        );
        
        sendAlert(title, content, AlertLevel.CRITICAL);
    }
}
