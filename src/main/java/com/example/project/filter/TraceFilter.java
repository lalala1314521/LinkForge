package com.example.project.filter;

/**TraceId 过滤器全链路日志追踪
 * 每个清求生成唯一TraceId 注入 MDC 和响应头， 清求结束时清除
 * 如果上游传入 X-Trace-Id ，响应先透传 ，微服务场景的链路续接
 */

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(1)
public class TraceFilter implements Filter {

    private static final String TRACE_ID_KEY   = "traceId";
    private static final String  TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest serverRequest, ServletResponse serverResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest   request  = (HttpServletRequest) serverRequest;
        HttpServletResponse  response = (HttpServletResponse) serverResponse;

        //优先从上游请求头透传 traceId
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if(traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            //将traceId 写入响应头， 便于客户端排查问题
            response.setHeader(TRACE_ID_HEADER, traceId);
            chain.doFilter(serverRequest,serverResponse);
        }finally {
            //清求结束，清除MDC防止线程复用导致的数据污染
            MDC.remove(TRACE_ID_KEY);
            MDC.remove("userId");
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}