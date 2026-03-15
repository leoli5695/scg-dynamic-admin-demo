package com.leoli.gateway.admin.alert;

/**
 * 告警级别枚举
 */
public enum AlertLevel {
    INFO("信息"),
    WARNING("警告"),
    ERROR("错误"),
    CRITICAL("严重");
    
    private final String description;
    
    AlertLevel(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
