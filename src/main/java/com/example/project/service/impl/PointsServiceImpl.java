package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.entity.PointsLog;
import com.example.project.entity.UserPoints;
import com.example.project.mapper.PointsLogMapper;
import com.example.project.mapper.UserPointsMapper;
import com.example.project.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分服务实现
 * <p>
 * 幂等：先插流水（uk_order_type 唯一键占位）再动余额；重复插 → DuplicateKeyException → 视为已处理。
 * 余额：乐观锁 version + 条件更新（balance &gt;= amount），重试 MAX_RETRY 次后抛错（回滚流水占位）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final int MAX_RETRY = 3;

    private final UserPointsMapper userPointsMapper;
    private final PointsLogMapper pointsLogMapper;

    /**
     * 积分比例：默认 1 元 = 1 积分（由调用方按 finalAmount 换算后传入 amount，此处仅记录配置便于调整）
     */
    @Value("${points.earn-ratio:1}")
    private int earnRatio;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void earn(Long userId, Integer amount, String orderNo, String reason) {
        if (amount == null || amount <= 0) {
            log.warn("[积分] 发放数量非法，跳过：userId={}, amount={}", userId, amount);
            return;
        }
        // 幂等占位：先插 EARN 流水（uk_order_type 唯一键）
        PointsLog earnLog = buildLog(userId, "EARN", amount, reason, orderNo);
        try {
            pointsLogMapper.insert(earnLog);
        } catch (DuplicateKeyException e) {
            log.info("[积分幂等] 订单{} 已发积分，跳过", orderNo);
            return;
        }
        ensureAccount(userId);
        for (int i = 0; i < MAX_RETRY; i++) {
            UserPoints up = userPointsMapper.selectByUserId(userId);
            if (up == null) {
                continue; // ensureAccount 已建账户，理论不会走到这里
            }
            if (userPointsMapper.increaseBalance(userId, amount, up.getVersion()) == 1) {
                log.info("[积分] 发放成功：userId={}, amount={}, orderNo={}, ratio={}", userId, amount, orderNo, earnRatio);
                return;
            }
            log.debug("[积分] 乐观锁冲突，重试：userId={}, attempt={}", userId, i + 1);
        }
        throw new BusinessException(ErrorCode.POINTS_EARN_FAILED); // 回滚流水占位 → 重投重跑
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refund(Long userId, String orderNo) {
        PointsLog earn = pointsLogMapper.findLastEarnByOrderNo(userId, orderNo);
        if (earn == null) {
            log.warn("[积分回退] 订单{} 无 EARN 记录，跳过", orderNo);
            return 0;
        }
        // 幂等占位：先插 REFUND 流水，重复插 → 已退过
        PointsLog refundLog = buildLog(userId, "REFUND", earn.getAmount(), "订单取消退还积分", orderNo);
        try {
            pointsLogMapper.insert(refundLog);
        } catch (DuplicateKeyException e) {
            log.info("[积分幂等] 订单{} 已退积分，跳过", orderNo);
            return 0;
        }
        for (int i = 0; i < MAX_RETRY; i++) {
            UserPoints up = userPointsMapper.selectByUserId(userId);
            if (up == null) {
                return 0;
            }
            if (userPointsMapper.decreaseBalance(userId, earn.getAmount(), up.getVersion()) == 1) {
                log.info("[积分] 回退成功：userId={}, amount={}, orderNo={}", userId, earn.getAmount(), orderNo);
                return earn.getAmount();
            }
            log.debug("[积分] 回退乐观锁冲突或余额不足，重试：userId={}, attempt={}", userId, i + 1);
        }
        throw new BusinessException(ErrorCode.POINTS_NOT_ENOUGH); // 回滚 REFUND 占位
    }

    @Override
    public int getBalance(Long userId) {
        UserPoints up = userPointsMapper.selectByUserId(userId);
        return up == null ? 0 : up.getBalance();
    }

    private PointsLog buildLog(Long userId, String type, Integer amount, String reason, String orderNo) {
        PointsLog logEntry = new PointsLog();
        logEntry.setUserId(userId);
        logEntry.setType(type);
        logEntry.setAmount(amount);
        logEntry.setReason(reason);
        logEntry.setOrderNo(orderNo);
        return logEntry;
    }

    /**
     * 账户不存在自动创建；并发撞唯一键则回查（忽略 DuplicateKeyException）
     */
    private void ensureAccount(Long userId) {
        if (userPointsMapper.selectByUserId(userId) != null) {
            return;
        }
        UserPoints up = new UserPoints();
        up.setUserId(userId);
        up.setBalance(0);
        up.setVersion(0);
        try {
            userPointsMapper.insert(up);
        } catch (DuplicateKeyException e) {
            log.debug("[积分] 账户并发创建，忽略：userId={}", userId);
        }
    }
}
