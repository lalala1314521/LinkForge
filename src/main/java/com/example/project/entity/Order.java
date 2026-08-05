package com.example.project.entity;

import com.example.project.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@Entity
@Table(name = "orders", indexes ={
        @Index(name = "idx_user_id",       columnList = "user_id"),
        @Index(name = "idx_order_no",      columnList = "order_no", unique = true),
        @Index(name = "idx_order_status",  columnList = "status"),
        @Index(name = "idx_user_status",   columnList = "user_id, status") //覆盖高频组合查询的联合索引
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_discount", precision = 10, scale = 2)
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected  void onCreate(){
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected  void onUpdate(){
        updatedAt = LocalDateTime.now();
    }
}
