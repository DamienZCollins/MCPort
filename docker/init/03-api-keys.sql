-- 本地联调用 API Key（阶段 3 治理层使用）
USE `mcport_db`;

INSERT INTO `t_api_key` (`api_key`, `user_id`, `permissions`, `rate_limit_per_minute`, `enabled`) VALUES
('test-key-read-only', 'user0', '["READ"]', 60, 1),
-- ADMIN 为权限位，决定能否跨用户访问；不再由 user_id 字符串推断
('test-key-admin', 'admin', '["READ", "WRITE", "ADMIN"]', 100, 1),
('test-key-disabled', 'user1', '["READ"]', 60, 0),
-- 低阈值 Key：用于快速验证 429，不占用只读 Key 的 60/min 额度
('test-key-tight', 'user0', '["READ"]', 3, 1)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;
