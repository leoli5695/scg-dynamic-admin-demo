package com.leoli.gateway.admin.alert;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 钉钉告警通知器
 */
@Component
@Slf4j
public class DingTalkAlertNotifier implements AlertNotifier {
    
    @Value("${gateway.health.alert.dingtalk.enabled:false}")
    private boolean enabled;
    
    @Value("${gateway.health.alert.dingtalk.webhook:}")
    private String webhookUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public void sendAlert(String title, String content, AlertLevel level) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.debug("DingTalk alert disabled or webhook not configured");
            return;
        }
        
        try {
            // 构建钉钉消息
            DingTalkMessage message = new DingTalkMessage();
            message.setMsgtype("markdown");
            message.setMarkdown(new Markdown(title, buildMarkdownContent(title, content, level)));
            
            // 发送 HTTP 请求
            restTemplate.postForEntity(webhookUrl, message, String.class);
            
            log.info("Sent DingTalk alert: {}", title);
        } catch (Exception e) {
            log.error("Failed to send DingTalk alert", e);
        }
    }
    
    private String buildMarkdownContent(String title, String content, AlertLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("#### ").append(title).append("\n\n");
        sb.append("> ").append(content).append("\n\n");
        sb.append("**告警级别**: ").append(level.getDescription()).append("\n");
        sb.append("**时间**: ").append(new java.util.Date()).append("\n");
        return sb.toString();
    }
    
    @Override
    public boolean isSupported() {
        return enabled && webhookUrl != null && !webhookUrl.isEmpty();
    }
    
    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DingTalkMessage {
        private String msgtype;
        private Markdown markdown;
    }
    
    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Markdown {
        private String title;
        private String text;
    }
}
