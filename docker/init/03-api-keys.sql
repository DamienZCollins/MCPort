-- 本地联调用 API Key（阶段 3 治理层使用）
USE `mcport_db`;

INSERT INTO `t_api_key` (`api_key`, `user_id`, `permissions`, `rate_limit_per_minute`, `enabled`) VALUES
('test-key-read-only', 'user0', '["READ"]', 60, 1),
('test-key-admin', 'admin', '["READ", "WRITE"]', 100, 1),
('test-key-disabled', 'user1', '["READ"]', 60, 0)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;
