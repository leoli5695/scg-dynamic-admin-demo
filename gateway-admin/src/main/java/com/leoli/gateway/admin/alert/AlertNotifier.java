package com.leoli.gateway.admin.alert;

/**
 * 告警通知器接口
 */
public interface AlertNotifier {
    
    /**
     * 发送告警
     * @param title 标题
     * @param content 内容
     * @param level 告警级别
     */
    void sendAlert(String title, String content, AlertLevel level);
    
    /**
     * 是否支持该通知方式
     */
    boolean isSupported();
}
