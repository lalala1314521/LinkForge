package com.example.project.service;

/**
 * 积分服务接口
 * <p>
 * 幂等落点：points_log.uk_order_type(order_no,type) 唯一键 ——
 * EARN/REFUND 各只能有一条；REFUND 先插流水（占位）再扣余额，重复插 → 已退过。
 * 余额变更使用乐观锁（version）+ 条件更新（balance>=amount），冲突/不足重试后抛错。
 */
public interface PointsService {

    /**
     * 发放积分：EARN 流水唯一（幂等）+ 乐观锁加余额；账户不存在自动创建
     *
     * @param userId  用户ID
     * @param amount  积分数量（正数）
     * @param orderNo 关联订单号（幂等键）
     * @param reason  变动原因
     */
    void earn(Long userId, Integer amount, String orderNo, String reason);

    /**
     * 回退积分：同 order_no 重复 REFUND 只退一次；无 EARN 记录则跳过；
     * 余额不足抛 POINTS_NOT_ENOUGH
     *
     * @return 实退积分数量（0 = 无 EARN 记录或已退过）
     */
    int refund(Long userId, String orderNo);

    /**
     * 查询余额（预留展示用）
     */
    int getBalance(Long userId);
}
