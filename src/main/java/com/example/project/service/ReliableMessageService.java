package com.example.project.service;

/**
 * 可靠消息服务接口（本地消息表模式）
 * <p>
 * 主流程：事务内 savePendingMessage（INSERT PENDING）→ 事务提交后 sendAfterCommit（注册 afterCommit → 发送）。
 * 补偿任务：@Scheduled 定时扫描 PENDING，加分布式锁防多实例重复扫描。
 * 状态闭环：PENDING →(发送成功)→ SENT；PENDING →(超重试)→ FAILED。
 */
public interface ReliableMessageService {

    /**
     * 事务内调用：写 PENDING 消息，返回消息 id（供 sendAfterCommit 回写状态）
     */
    Long savePendingMessage(String topic, String messageKey, Object dto);

    /**
     * 事务提交后调用：注册 afterCommit → doSend；无事务时（测试直调）直接发送
     */
    void sendAfterCommit(Long msgId, String topic, String messageKey, Object dto);

    /**
     * @Scheduled 补偿任务：分布式锁防多实例重复扫描，发送成功必须 updateToSent
     */
    void compensatePendingMessages();
}
