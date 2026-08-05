package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.entity.PointsLog;
import com.example.project.entity.UserPoints;
import com.example.project.mapper.PointsLogMapper;
import com.example.project.mapper.UserPointsMapper;
import com.example.project.service.impl.PointsServiceImpl;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 积分服务测试（批次 1）
 * <p>
 * 覆盖：earn 正常（流水+余额）、重复 EARN 幂等跳过、乐观锁重试、账户自动创建、数量非法跳过；
 * refund 正常、REFUND 唯一键幂等、无 EARN 记录跳过、余额不足重试后抛 POINTS_NOT_ENOUGH；余额查询。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("积分服务测试")
class PointsServiceTest {

    @Mock UserPointsMapper userPointsMapper;
    @Mock PointsLogMapper pointsLogMapper;

    @InjectMocks PointsServiceImpl pointsService;

    private UserPoints buildPoints(Long userId, int balance, int version) {
        UserPoints up = new UserPoints();
        up.setId(userId);
        up.setUserId(userId);
        up.setBalance(balance);
        up.setVersion(version);
        return up;
    }

    private PointsLog buildEarnLog(int amount) {
        PointsLog log = new PointsLog();
        log.setId(1L);
        log.setUserId(1L);
        log.setType("EARN");
        log.setAmount(amount);
        log.setReason("订单支付奖励");
        log.setOrderNo("SN1");
        return log;
    }

    // ---- earn ----

    @Test
    @DisplayName("earn - 正常：写 EARN 流水 + 乐观锁加余额")
    void earn_success() {
        given(userPointsMapper.selectByUserId(1L)).willReturn(buildPoints(1L, 0, 0));
        given(userPointsMapper.increaseBalance(1L, 100, 0)).willReturn(1);

        pointsService.earn(1L, 100, "SN1", "订单支付奖励");

        ArgumentCaptor<PointsLog> captor = ArgumentCaptor.forClass(PointsLog.class);
        verify(pointsLogMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getType()).isEqualTo("EARN");
        BDDAssertions.then(captor.getValue().getOrderNo()).isEqualTo("SN1");
        verify(userPointsMapper).increaseBalance(1L, 100, 0);
    }

    @Test
    @DisplayName("earn - 重复 EARN（uk_order_type 冲突）：幂等跳过，不加余额")
    void earn_duplicate_idempotent() {
        doThrow(new DuplicateKeyException("dup points_log uk_order_type"))
                .when(pointsLogMapper).insert(any(PointsLog.class));

        pointsService.earn(1L, 100, "SN1", "订单支付奖励");

        verify(userPointsMapper, never()).increaseBalance(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("earn - 乐观锁冲突重试：第一次失败第二次成功")
    void earn_optimisticLockRetry() {
        given(userPointsMapper.selectByUserId(1L)).willReturn(
                buildPoints(1L, 0, 0),   // ensureAccount 读取（账户已存在，version=0）
                buildPoints(1L, 0, 0),   // 第一次尝试读到 version=0 → increaseBalance(...,0) 冲突
                buildPoints(1L, 100, 1)  // 重试读到 version=1（他人已改）→ increaseBalance(...,1) 成功
        );
        given(userPointsMapper.increaseBalance(1L, 100, 0)).willReturn(0);  // 冲突
        given(userPointsMapper.increaseBalance(1L, 100, 1)).willReturn(1);  // 重试成功

        pointsService.earn(1L, 100, "SN1", "订单支付奖励");

        verify(userPointsMapper).increaseBalance(1L, 100, 0);
        verify(userPointsMapper).increaseBalance(1L, 100, 1);
    }

    @Test
    @DisplayName("earn - 账户不存在自动创建")
    void earn_accountAutoCreate() {
        given(userPointsMapper.selectByUserId(1L)).willReturn(null, buildPoints(1L, 0, 0));
        given(userPointsMapper.increaseBalance(1L, 100, 0)).willReturn(1);

        pointsService.earn(1L, 100, "SN1", "订单支付奖励");

        ArgumentCaptor<UserPoints> captor = ArgumentCaptor.forClass(UserPoints.class);
        verify(userPointsMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getUserId()).isEqualTo(1L);
        BDDAssertions.then(captor.getValue().getBalance()).isEqualTo(0);
        BDDAssertions.then(captor.getValue().getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("earn - 数量非法（<=0）：跳过不写流水")
    void earn_invalidAmount_skip() {
        pointsService.earn(1L, 0, "SN1", "订单支付奖励");

        verify(pointsLogMapper, never()).insert(any(PointsLog.class));
        verify(userPointsMapper, never()).increaseBalance(any(), anyInt(), anyInt());
    }

    // ---- refund ----

    @Test
    @DisplayName("refund - 正常：写 REFUND 流水（金额取 EARN）+ 扣余额")
    void refund_success() {
        given(pointsLogMapper.findLastEarnByOrderNo(1L, "SN1")).willReturn(buildEarnLog(100));
        given(userPointsMapper.selectByUserId(1L)).willReturn(buildPoints(1L, 200, 0));
        given(userPointsMapper.decreaseBalance(1L, 100, 0)).willReturn(1);

        int refunded = pointsService.refund(1L, "SN1");

        BDDAssertions.then(refunded).isEqualTo(100);
        ArgumentCaptor<PointsLog> captor = ArgumentCaptor.forClass(PointsLog.class);
        verify(pointsLogMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getType()).isEqualTo("REFUND");
        BDDAssertions.then(captor.getValue().getAmount()).isEqualTo(100);
        BDDAssertions.then(captor.getValue().getOrderNo()).isEqualTo("SN1");
        verify(userPointsMapper).decreaseBalance(1L, 100, 0);
    }

    @Test
    @DisplayName("refund - 重复 REFUND（唯一键冲突）：已退过，不重复扣")
    void refund_idempotent() {
        given(pointsLogMapper.findLastEarnByOrderNo(1L, "SN1")).willReturn(buildEarnLog(100));
        doThrow(new DuplicateKeyException("dup points_log uk_order_type"))
                .when(pointsLogMapper).insert(any(PointsLog.class));

        int refunded = pointsService.refund(1L, "SN1");

        BDDAssertions.then(refunded).isEqualTo(0);
        verify(userPointsMapper, never()).decreaseBalance(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("refund - 无 EARN 记录：跳过返回 0")
    void refund_noEarnRecord() {
        given(pointsLogMapper.findLastEarnByOrderNo(1L, "SN1")).willReturn(null);

        int refunded = pointsService.refund(1L, "SN1");

        BDDAssertions.then(refunded).isEqualTo(0);
        verify(pointsLogMapper, never()).insert(any(PointsLog.class));
    }

    @Test
    @DisplayName("refund - 余额不足重试 3 次后抛 POINTS_NOT_ENOUGH")
    void refund_insufficientBalance() {
        given(pointsLogMapper.findLastEarnByOrderNo(1L, "SN1")).willReturn(buildEarnLog(100));
        given(userPointsMapper.selectByUserId(1L)).willReturn(buildPoints(1L, 50, 0));
        given(userPointsMapper.decreaseBalance(1L, 100, 0)).willReturn(0);   // balance 50 < 100

        assertThatThrownBy(() -> pointsService.refund(1L, "SN1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POINTS_NOT_ENOUGH);
        // 重试 3 次
        then(userPointsMapper).should(org.mockito.Mockito.times(3)).decreaseBalance(1L, 100, 0);
    }

    // ---- getBalance ----

    @Test
    @DisplayName("getBalance - 有账户返回余额")
    void getBalance_hasAccount() {
        given(userPointsMapper.selectByUserId(1L)).willReturn(buildPoints(1L, 50, 3));

        BDDAssertions.then(pointsService.getBalance(1L)).isEqualTo(50);
    }

    @Test
    @DisplayName("getBalance - 无账户返回 0")
    void getBalance_noAccount() {
        given(userPointsMapper.selectByUserId(1L)).willReturn(null);

        BDDAssertions.then(pointsService.getBalance(1L)).isEqualTo(0);
    }
}
