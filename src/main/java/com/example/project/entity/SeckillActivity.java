package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动
 *
 * 设计要点：available_stock 是可售卖库存，和 Redis 缓存中的库存保持最终一致。
 * 为什么要分 total_stock 和 available_stock？
 * 答：total_stock 是活动创建时设定的总库存量（不变量），
 *     available_stock 是剩余可售卖量（会变化）。
 *     如果活动要重置，用 total_stock 恢复 available_stock 即可。
 */
@Data
@Entity
@Table(name = "seckill_activities", indexes = {
        @Index(name = "idx_status_time", columnList = "status, start_time, end_time")
})
public class SeckillActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "total_stock", nullable = false)
    private Integer totalStock;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "CREATED";

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