package com.example.project.entity;

import com.example.project.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/*
 *用户实体
 */
@Data
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_username", columnList = "username", unique = true),
        @Index(name = "idx_phone", columnList = "phone"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(lenght = 50)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate(){
        createdAt = LocalDateTime.now();
        updateAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate(){updatedAt = LocalDateTime.now();}

}
