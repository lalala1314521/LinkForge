package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单
 *
 * 和 orders 表分离的原因见 DDL 设计章节。
 *
 * 索引设计思考：
 * - idx_order_no (UNIQUE)：订单号全局唯一，用于查询订单状态
 * - idx_user_activity：秒杀的限购规则是"每人每个活动只能买一次"，
 *   这个联合索引支撑了幂等检查的查询：SELECT COUNT(*) FROM seckill_orders
 *   WHERE user_id = ? AND activity_id = ?
 */
@Data
@Entity
@Table(name = "seckill_orders", indexes = {
        @Index(name = "idx_order_no", columnList = "order_no", unique = true),
        @Index(name = "idx_user_activity", columnList = "user_id, activity_id"),
        @Index(name = "idx_user_status", columnList = "user_id, status")
})
public class SeckillOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}