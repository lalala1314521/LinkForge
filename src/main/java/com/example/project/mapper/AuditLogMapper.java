package com.example.project.mapper;

import com.example.project.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper（只追加写，不更新/删除）
 */
@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO audit_log (user_id, username, action, target_type, target_id, detail, ip, created_at)
            VALUES (#{userId}, #{username}, #{action}, #{targetType}, #{targetId}, #{detail}, #{ip}, NOW())
            """)
    void insert(AuditLog auditLog);
}
