-- MCPort 本地开发库初始化
-- 仅负责建库与字符集；表结构在第 1 周以本目录下后续脚本追加（01-schema.sql 建表，02-data.sql 合成数据）

CREATE DATABASE IF NOT EXISTS `mcport_db`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
