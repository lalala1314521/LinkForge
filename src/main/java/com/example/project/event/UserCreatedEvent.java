package com.example.project.event;

/**
 * 用户创建成功事件 异步时间处理
 */

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserCreatedEvent extends ApplicationEvent {

    private final Long userId;
    private final String username;
    private final String nickname;

    public UserCreatedEvent(Object source, Long userId, String username, String nickname) {
        super(source);
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
    }
}