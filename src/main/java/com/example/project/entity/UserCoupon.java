package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券（实例）
 *
 * 状态机转换：
 *
 *   ┌─────────┐  下单冻结   ┌─────────┐  支付确认   ┌──────┐
 *   │ UNUSED  │ ─────────→ │ FROZEN  │ ─────────→ │ USED │
 *   └─────────┘ ←───────── └─────────┘            └──────┘
 *                    订单取消
 *
 * 为什么不直接 UNUSED → USED？
 * 答：假设用户同时在两个浏览器下单，都用了同一张券。
 *     如果直接 UNUSED → USED，两个请求可能都看到 status='UNUSED'，
 *     然后都尝试更新为 USED，导致优惠券被重复使用（超卖）。
 *     引入 FROZEN 后，只有第一个请求能把 UNUSED 改成 FROZEN，
 *     第二个请求看到的是 FROZEN，无法操作，自然失败。
 */
@Data
@Entity
@Table(name = "user_coupons", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_status", columnList = "status")
})
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "UNUSED";

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}