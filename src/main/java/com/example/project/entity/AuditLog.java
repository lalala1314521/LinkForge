package com.example.project.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体（T2）
 * <p>
 * 记录管理端写操作：操作人/时间/IP/动作/目标/详情，供安全审计与回溯。
 * 表结构与 schema.sql 同步（dev 靠 ddl-auto:update 自动建表）。
 */
@Data
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_action", columnList = "action"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
