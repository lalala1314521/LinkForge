-- ============================================================
-- 数据库初始化脚本
-- 数据库: demo_db
-- ============================================================

CREATE DATABASE IF NOT EXISTS demo_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE demo_db;

-- ---- 用户表 ----
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL                COMMENT '用户名（唯一）',
    password    VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加密密码',
    nickname    VARCHAR(50)  DEFAULT NULL            COMMENT '昵称',
    phone       VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    email       VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED/DELETED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_username (username),
    KEY idx_phone (phone),
    KEY idx_created_at (created_at)   -- 任务5: created_at 排序常用，补充索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---- 订单表 ----
CREATE TABLE IF NOT EXISTS orders (
    id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)    NOT NULL                COMMENT '订单编号（雪花ID）',
    user_id      BIGINT         NOT NULL                COMMENT '下单用户ID',
    total_amount DECIMAL(10,2)  NOT NULL                COMMENT '订单金额',
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/SHIPPED/COMPLETED/CANCELLED',
    remark       VARCHAR(500)   DEFAULT NULL            COMMENT '备注',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_order_status (status),
    KEY idx_user_status (user_id, status)  -- 任务5: 覆盖高频 user_id + status 组合查询的联合索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ---- 测试数据（可选）----
-- 密码均为 Test@1234（BCrypt 加密）
INSERT INTO users (username, password, nickname, phone, email) VALUES
('admin',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', '13800138000', 'admin@example.com'),
('testuser','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '测试用户', '13900139000', 'test@example.com')
ON DUPLICATE KEY UPDATE updated_at = updated_at;
