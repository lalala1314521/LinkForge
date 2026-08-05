package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水表
 *
 * 设计要点：只做 INSERT，永远不做 UPDATE 和 DELETE。
 * 这叫追加写入（Append-Only）模式，保证流水数据的完整性和可审计性。
 * 回退操作不是修改原记录，而是新增一条 type='REFUND' 的记录。
 */
@Data
@Entity
@Table(name = "points_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_type", columnNames = {"order_no", "type"}),
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_order_no", columnList = "order_no")
        })
public class PointsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "reason", length = 200)
    private String reason;

    /**
     * 关联订单号，用于订单取消时查找对应的 EARN 记录来回退
     */
    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}