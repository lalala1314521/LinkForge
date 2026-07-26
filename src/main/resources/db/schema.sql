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
    KEY idx_created_at (created_at)   
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
    KEY idx_user_status (user_id, status)  -- 覆盖高频 user_id + status 组合查询的联合索引
    ALTER TABLE orders ADD COLUMN coupon_id BIGINT DEFAULT NULL COMMENT '使用的用户优惠券ID' AFTER total_amount;
    ALTER TABLE orders ADD COLUMN coupon_discount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣金额' AFTER coupon_id;
    ALTER TABLE orders ADD COLUMN final_amount DECIMAL(10,2) NOT NULL COMMENT '实付金额' AFTER coupon_discount;
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
-- ---- 商品表 ----
CREATE TABLE IF NOT EXISTS products (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    price        DECIMAL(10,2) NOT NULL,
    stock        INT NOT NULL DEFAULT 0,
    status       VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ---- 订单商品关联表 ----
CREATE TABLE IF NOT EXISTS order_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT NOT NULL,
    product_id    BIGINT NOT NULL,
    quantity      INT NOT NULL,
    price         DECIMAL(10,2) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX  idx_order_id(order_id)
);
CREATE TABLE IF NOT EXISTS user_points (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL                COMMENT '用户ID',
    balance      INT          NOT NULL DEFAULT 0      COMMENT '当前积分余额',
    version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分账户表';

-- ---- 积分流水表 ----
CREATE TABLE IF NOT EXISTS points_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL                COMMENT '用户ID',
    type         VARCHAR(20)  NOT NULL                COMMENT '类型: EARN/SPEND/REFUND',
    amount       INT          NOT NULL                COMMENT '积分变动量（正数）',
    reason       VARCHAR(200) DEFAULT NULL            COMMENT '变动原因',
    order_no     VARCHAR(32)  DEFAULT NULL            COMMENT '关联订单号',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- ---- 优惠券模板表 ----
CREATE TABLE IF NOT EXISTS coupons (
    id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(100)   NOT NULL                COMMENT '优惠券名称',
    discount     DECIMAL(10,2)  NOT NULL                COMMENT '优惠金额',
    min_amount   DECIMAL(10,2)  NOT NULL DEFAULT 0.00   COMMENT '最低消费金额',
    total_count  INT            NOT NULL                COMMENT '发放总量',
    used_count   INT            NOT NULL DEFAULT 0      COMMENT '已使用数量',
    status       VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    expire_at    DATETIME       NOT NULL                COMMENT '过期时间',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板表';

-- ---- 用户优惠券关联表 ----
CREATE TABLE IF NOT EXISTS user_coupons (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL                COMMENT '用户ID',
    coupon_id    BIGINT       NOT NULL                COMMENT '优惠券ID',
    order_no     VARCHAR(32)  DEFAULT NULL            COMMENT '使用时关联订单号',
    status       VARCHAR(20)  NOT NULL DEFAULT 'UNUSED' COMMENT '状态: UNUSED/FROZEN/USED/EXPIRED',
    frozen_at    DATETIME     DEFAULT NULL            COMMENT '冻结时间',
    used_at      DATETIME     DEFAULT NULL            COMMENT '使用时间',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券关联表';

-- ---- 本地消息表 ----
CREATE TABLE IF NOT EXISTS message_table (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    topic         VARCHAR(100) NOT NULL                COMMENT 'Kafka Topic',
    message_key   VARCHAR(100) DEFAULT NULL            COMMENT '消息Key（分区键）',
    payload       TEXT         NOT NULL                COMMENT '消息体JSON',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/FAILED',
    retry_count   INT          NOT NULL DEFAULT 0      COMMENT '已重试次数',
    max_retries   INT          NOT NULL DEFAULT 3      COMMENT '最大重试次数',
    next_retry_at DATETIME     DEFAULT NULL            COMMENT '下次重试时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    sent_at       DATETIME     DEFAULT NULL            COMMENT '发送成功时间',
    PRIMARY KEY (id),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';

-- ---- 秒杀活动表 ----
CREATE TABLE IF NOT EXISTS seckill_activities (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(100) NOT NULL                COMMENT '活动名称',
    product_id      BIGINT       NOT NULL                COMMENT '关联商品ID',
    total_stock     INT          NOT NULL                COMMENT '总库存',
    available_stock INT          NOT NULL                COMMENT '剩余可用库存',
    start_time      DATETIME     NOT NULL                COMMENT '开始时间',
    end_time        DATETIME     NOT NULL                COMMENT '结束时间',
    status          VARCHAR(20)  NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/ACTIVE/ENDED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_time (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

-- ---- 秒杀订单表 ----
CREATE TABLE IF NOT EXISTS seckill_orders (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32)    NOT NULL                COMMENT '秒杀订单号',
    user_id         BIGINT         NOT NULL                COMMENT '用户ID',
    activity_id     BIGINT         NOT NULL                COMMENT '活动ID',
    product_id      BIGINT         NOT NULL                COMMENT '商品ID',
    price           DECIMAL(10,2)  NOT NULL                COMMENT '秒杀价格',
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/FAILED',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY idx_order_no (order_no),
    KEY idx_user_activity (user_id, activity_id),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- ---- 测试数据----
-- 密码均为 Test@1234（BCrypt 加密）
INSERT INTO users (username, password, nickname, phone, email) VALUES
('admin',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', '13800138000', 'admin@example.com'),
('testuser','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '测试用户', '13900139000', 'test@example.com')
ON DUPLICATE KEY UPDATE updated_at = updated_at;
