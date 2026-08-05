package com.example.project.aspect;

import com.example.project.annotation.AuditLog;
import com.example.project.common.Result;
import com.example.project.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * 操作审计日志切面（T2）
 * <p>
 * @Around 拦截带 @AuditLog 的方法：方法执行成功后记录（失败不记录——简化方案，报告说明可扩展失败态）。
 * - userId 取 SecurityContext.details（Long）；username 取 auth principal；
 * - IP 取 RequestContextHolder（无请求上下文时为 null，如单元测试直调）；
 * - targetId 优先取第一个 Long 类型参数（@PathVariable id），创建类方法取返回值 Result&lt;Long&gt; 的 data；
 * - detail 序列化方法入参（管理端标注接口入参无密码等敏感字段；生产如需要可加脱敏）。
 * 审计日志写入失败不影响主流程（catch 后仅告警）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object result = joinPoint.proceed();   // 方法成功才记录
        try {
            record(joinPoint, auditLog, result);
        } catch (Exception e) {
            log.error("[审计] 日志写入失败：action={}", auditLog.action(), e);
        }
        return result;
    }

    private void record(ProceedingJoinPoint joinPoint, AuditLog auditLog, Object result) {
        // 实体与注解同名，实体用全限定名（com.example.project.entity.AuditLog）
        com.example.project.entity.AuditLog record = new com.example.project.entity.AuditLog();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getDetails() instanceof Long userId) {
                record.setUserId(userId);
            }
            record.setUsername(auth.getName());
        } else {
            record.setUserId(0L);      // 理论不会发生（管理端均需认证），兜底
            record.setUsername("unknown");
        }
        record.setAction(auditLog.action());
        record.setTargetType(auditLog.targetType());
        record.setTargetId(resolveTargetId(joinPoint.getArgs(), result));
        record.setDetail(buildDetail(joinPoint.getArgs()));
        record.setIp(resolveIp());
        auditLogMapper.insert(record);
    }

    /**
     * 目标ID解析：优先第一个 Long 类型参数（@PathVariable id）；创建类方法取返回值 Result&lt;Long&gt; 的 data
     */
    private String resolveTargetId(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return String.valueOf(id);
            }
        }
        if (result instanceof Result<?> r && r.getData() instanceof Long id) {
            return String.valueOf(id);
        }
        return null;
    }

    private String buildDetail(Object[] args) {
        try {
            return jsonMapper.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("[审计] 入参序列化失败：{}", e.getMessage());
            return null;
        }
    }

    private String resolveIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();
        }
        return null;
    }
}
