package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单事件处理记录表（消费端幂等主键）
 * <p>
 * 消费端处理订单事件前先 INSERT 占位（order_no + event_type 唯一键），
 * 重复投递 → DuplicateKeyException → 幂等跳过，保证同一条消息只生效一次。
 * 与业务操作（扣库存/发积分/用券/回退）同库同事务，失败整体回滚，
 * 重投递后完整重跑，杜绝"扣一半重投再扣一次"。
 */
@Data
@Entity
@Table(name = "order_process_record", uniqueConstraints =
        @UniqueConstraint(name = "uk_order_event", columnNames = {"order_no", "event_type"}))
public class OrderProcessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PROCESSED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderProcessRecord() {
    }

    public OrderProcessRecord(String orderNo, String eventType) {
        this.orderNo = orderNo;
        this.eventType = eventType;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
