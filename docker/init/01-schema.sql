-- MCPort 本地开发库：建库 + 建表
-- 仅在 MySQL 数据卷首次初始化时执行；改表需 down -v 重建，或手工执行本脚本

CREATE DATABASE IF NOT EXISTS `mcport_db`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `mcport_db`;

-- 订单主表
CREATE TABLE IF NOT EXISTS `t_order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id` VARCHAR(64) NOT NULL COMMENT '订单号',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户 ID',
    `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/PAID/SHIPPED/COMPLETED',
    `total_amount` DECIMAL(10, 2) NOT NULL COMMENT '订单总金额',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_t_order_order_id` (`order_id`),
    KEY `idx_t_order_user_id` (`user_id`),
    KEY `idx_t_order_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS `t_order_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id` VARCHAR(64) NOT NULL COMMENT '订单号',
    `sku_id` VARCHAR(64) NOT NULL COMMENT 'SKU',
    `quantity` INT NOT NULL COMMENT '数量',
    `price` DECIMAL(10, 2) NOT NULL COMMENT '单价',
    KEY `idx_t_order_item_order_id` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细表';

-- 库存表
CREATE TABLE IF NOT EXISTS `t_inventory` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `sku_id` VARCHAR(64) NOT NULL COMMENT 'SKU',
    `warehouse_id` VARCHAR(32) DEFAULT NULL COMMENT '仓库 ID',
    `available_qty` INT NOT NULL COMMENT '可用库存',
    `reserved_qty` INT DEFAULT 0 COMMENT '预留库存',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_t_inventory_sku_id` (`sku_id`),
    KEY `idx_t_inventory_warehouse_id` (`warehouse_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存表';

-- API Key 管理表
CREATE TABLE IF NOT EXISTS `t_api_key` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `api_key` VARCHAR(64) NOT NULL COMMENT 'API Key',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户 ID',
    `permissions` JSON NOT NULL COMMENT '权限列表，例如 ["READ","WRITE"]',
    `rate_limit_per_minute` INT DEFAULT 60 COMMENT '每分钟调用次数限制',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_t_api_key_api_key` (`api_key`),
    KEY `idx_t_api_key_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'API Key 管理表';

-- 工具调用审计日志
CREATE TABLE IF NOT EXISTS `t_audit_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `request_id` VARCHAR(36) NOT NULL COMMENT '请求 ID，UUID',
    `api_key` VARCHAR(64) NOT NULL COMMENT 'API Key',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户 ID',
    `tool_name` VARCHAR(64) NOT NULL COMMENT '调用的工具名',
    `input_params` JSON DEFAULT NULL COMMENT '输入参数（脱敏后）',
    `output_summary` VARCHAR(512) DEFAULT NULL COMMENT '输出摘要',
    `status` VARCHAR(16) NOT NULL COMMENT 'SUCCESS/BLOCKED/ERROR',
    `block_reason` VARCHAR(128) DEFAULT NULL COMMENT 'RATE_LIMIT/FORBIDDEN/INVALID_PARAM/UNAUTHORIZED',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    `latency_ms` INT NOT NULL COMMENT '执行耗时（毫秒）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_t_audit_log_user_time` (`user_id`, `created_at`),
    KEY `idx_t_audit_log_tool` (`tool_name`),
    KEY `idx_t_audit_log_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工具调用审计日志';
