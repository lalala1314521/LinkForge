package com.example.project.config;


/**
 * MyBatis 慢SQL拦截器 记录超过阈值的慢查询
 * 拦截 Executor 的 query 和 update 操作 ,超时500ms打印 WARN 日志
 */

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                   args = {MyBatisConfig.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                   args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    /*慢SQL拦截阈值 超过此值打印WARN日志 */

    private static final long SLOW_SQL_THRESHOLD_MS = 500L;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = invocation.proceed();
        long elapsed = System.currentTimeMillis() - startTime;

        if(elapsed >= SLOW_SQL_THRESHOLD_MS) {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
            log.warn("[慢SQL警告] 耗时={}ms, StatementId={}, 参数={}", elapsed, ms.getId(), parameter);
        }
        return result;
    }
}