package com.example.project.listener;


/**
 * 用户事件监听器 异步事件处理
 *监听用户创建事件并异步执行非核心逻辑，减少主链路耗时
 */

import com.example.project.event.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserEventListener {

    /**
     * 异步处理用户创建 模拟发送欢迎邮件
     */
    @Async
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("[异步] 用户注册成功，发送欢迎通知 : userId={}, username={}", event.getUserId(), event.getUsername());
        //模拟邮件发送耗时

        try{
            Thread.sleep(100);
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[异步] 欢迎邮件已发送至用户：{}", event.getUsername());
    }
    @Async
    @EventListener
    public void handleUserCreated_InitDefaults(UserCreatedEvent event) {
        log.info("[异步] 初始化用户默认数据 : userId = {}", event.getUserId());
        log.info("[异步] 用户{}默认数据初始化完成", event.getUserId());
    }
}