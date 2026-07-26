package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表
 *
 * 这是分布式系统中"可靠消息"模式的实现载体。
 * 详见 Part 2 的设计章节。
 *
 * 状态流转：PENDING → SENT / FAILED
 * PENDING：事务内插入，等待发送
 * SENT：Kafka 发送成功后更新
 * FAILED：超过最大重试次数，需要人工介入
 */
@Data
@Entity
@Table(name = "message_table", indexes = {
        @Index(name = "idx_status_retry", columnList = "status, next_retry_at")
})
public class MessageTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", length = 100)
    private String messageKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}