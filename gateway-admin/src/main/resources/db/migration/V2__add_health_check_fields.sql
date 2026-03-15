-- ============================================
-- 健康检查功能数据库表结构升级
-- 执行时间：2026-03-11
-- 说明：为 service_instances 表添加健康状态相关字段
-- ============================================

-- 添加健康状态字段
ALTER TABLE service_instances 
ADD COLUMN health_status VARCHAR(20) DEFAULT 'HEALTHY' COMMENT '健康状态：HEALTHY, UNHEALTHY';

ALTER TABLE service_instances 
ADD COLUMN last_health_check_time BIGINT COMMENT '最后健康检查时间（毫秒时间戳）';

ALTER TABLE service_instances 
ADD COLUMN unhealthy_reason VARCHAR(500) COMMENT '不健康原因';

ALTER TABLE service_instances 
ADD COLUMN consecutive_failures INT DEFAULT 0 COMMENT '连续失败次数';

-- 添加索引优化查询性能
CREATE INDEX idx_health_status ON service_instances(health_status);
CREATE INDEX idx_service_id_health ON service_instances(service_id, health_status);

-- 更新现有数据（如果有）
UPDATE service_instances 
SET health_status = 'HEALTHY',
    last_health_check_time = UNIX_TIMESTAMP(NOW()) * 1000
WHERE health_status IS NULL;

-- 查看表结构
DESC service_instances;
