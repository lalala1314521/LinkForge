package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.dto.request.CouponCreateRequest;
import com.example.project.dto.response.CouponResponse;
import com.example.project.entity.Coupon;
import com.example.project.entity.UserCoupon;
import com.example.project.mapper.CouponMapper;
import com.example.project.mapper.UserCouponMapper;
import com.example.project.service.impl.CouponServiceImpl;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 优惠券服务测试（批次 1）
 * <p>
 * 覆盖：领券（正常/重复限1张/领完/过期/不存在）、冻结（正常/非本人/不满足门槛/已被占用）、
 * 使用与解冻（正常/幂等跳过/已使用不可解冻）、建券、我的券列表。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("优惠券服务测试")
class CouponServiceTest {

    @Mock CouponMapper couponMapper;
    @Mock UserCouponMapper userCouponMapper;

    @InjectMocks CouponServiceImpl couponService;

    private Coupon buildCoupon(Long id, BigDecimal discount, BigDecimal minAmount, int totalCount, String status, LocalDateTime expireAt) {
        Coupon c = new Coupon();
        c.setId(id);
        c.setName("新人券");
        c.setDiscount(discount);
        c.setMinAmount(minAmount);
        c.setTotalCount(totalCount);
        c.setUsedCount(0);
        c.setStatus(status);
        c.setExpireAt(expireAt);
        return c;
    }

    private Coupon activeCoupon(Long id) {
        return buildCoupon(id, new BigDecimal("10.00"), new BigDecimal("100.00"), 100, "ACTIVE",
                LocalDateTime.now().plusDays(1));
    }

    private UserCoupon buildUserCoupon(Long id, Long userId, Long couponId, String status, String orderNo) {
        UserCoupon uc = new UserCoupon();
        uc.setId(id);
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus(status);
        uc.setOrderNo(orderNo);
        return uc;
    }

    // ---- claim 领券 ----

    @Test
    @DisplayName("claim - 正常：领取返回 user_coupon.id")
    void claim_success() {
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));
        given(userCouponMapper.countByUserAndCoupon(1L, 10L)).willReturn(0);
        given(couponMapper.incrementClaimCount(10L)).willReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            UserCoupon uc = invocation.getArgument(0);
            uc.setId(7L);
            return null;
        }).when(userCouponMapper).insert(any(UserCoupon.class));

        Long userCouponId = couponService.claim(1L, 10L);

        BDDAssertions.then(userCouponId).isEqualTo(7L);
        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getUserId()).isEqualTo(1L);
        BDDAssertions.then(captor.getValue().getCouponId()).isEqualTo(10L);
        BDDAssertions.then(captor.getValue().getStatus()).isEqualTo("UNUSED");
    }

    @Test
    @DisplayName("claim - 重复领（每用户限1张）：抛 1305")
    void claim_duplicate() {
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));
        given(userCouponMapper.countByUserAndCoupon(1L, 10L)).willReturn(1);

        assertThatThrownBy(() -> couponService.claim(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_CLAIMED);
        then(couponMapper).should(never()).incrementClaimCount(anyLong());
    }

    @Test
    @DisplayName("claim - 已领完（used_count>=total_count）：抛 1306")
    void claim_stockRunOut() {
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));
        given(userCouponMapper.countByUserAndCoupon(1L, 10L)).willReturn(0);
        given(couponMapper.incrementClaimCount(10L)).willReturn(0);

        assertThatThrownBy(() -> couponService.claim(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_STOCK_RUN_OUT);
        then(userCouponMapper).should(never()).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("claim - 已过期：抛 1303")
    void claim_expired() {
        given(couponMapper.selectById(10L)).willReturn(
                buildCoupon(10L, new BigDecimal("10.00"), new BigDecimal("100.00"), 100, "ACTIVE",
                        LocalDateTime.now().minusDays(1)));

        assertThatThrownBy(() -> couponService.claim(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("claim - 券不存在/非ACTIVE：抛 1301")
    void claim_notFound() {
        given(couponMapper.selectById(10L)).willReturn(null);

        assertThatThrownBy(() -> couponService.claim(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
    }

    // ---- freezeForOrder 冻结 ----

    @Test
    @DisplayName("freezeForOrder - 正常：条件冻结 UNUSED→FROZEN，返回抵扣金额")
    void freezeForOrder_success() {
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "UNUSED", null));
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));
        given(userCouponMapper.freezeCoupon(5L, "SN1")).willReturn(1);

        BigDecimal discount = couponService.freezeForOrder(5L, 1L, "SN1", new BigDecimal("200.00"));

        BDDAssertions.then(discount).isEqualByComparingTo("10.00");
        then(userCouponMapper).should().freezeCoupon(5L, "SN1");
    }

    @Test
    @DisplayName("freezeForOrder - 券不属于当前用户：抛 1301")
    void freezeForOrder_notOwner() {
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 2L, 10L, "UNUSED", null));

        assertThatThrownBy(() -> couponService.freezeForOrder(5L, 1L, "SN1", new BigDecimal("200.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_FOUND);
        then(userCouponMapper).should(never()).freezeCoupon(anyLong(), anyString());
    }

    @Test
    @DisplayName("freezeForOrder - 不满足满减门槛：抛 1304")
    void freezeForOrder_notApplicable() {
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "UNUSED", null));
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));  // minAmount=100

        assertThatThrownBy(() -> couponService.freezeForOrder(5L, 1L, "SN1", new BigDecimal("50.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);
        then(userCouponMapper).should(never()).freezeCoupon(anyLong(), anyString());
    }

    @Test
    @DisplayName("freezeForOrder - 券已被其他订单冻结/使用：抛 1302")
    void freezeForOrder_alreadyUsed() {
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "UNUSED", null));
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));
        given(userCouponMapper.freezeCoupon(5L, "SN1")).willReturn(0);

        assertThatThrownBy(() -> couponService.freezeForOrder(5L, 1L, "SN1", new BigDecimal("200.00")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_USED);
    }

    // ---- markUsed 支付确认 ----

    @Test
    @DisplayName("markUsed - 正常：FROZEN→USED")
    void markUsed_success() {
        given(userCouponMapper.useCoupon(5L, "SN1")).willReturn(1);

        couponService.markUsed(5L, "SN1");

        then(userCouponMapper).should().useCoupon(5L, "SN1");
    }

    @Test
    @DisplayName("markUsed - 幂等：已 USED 同单视为成功")
    void markUsed_idempotent() {
        given(userCouponMapper.useCoupon(5L, "SN1")).willReturn(0);
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "USED", "SN1"));

        couponService.markUsed(5L, "SN1");   // 不抛异常
    }

    @Test
    @DisplayName("markUsed - 非 USED 同单：抛 1302")
    void markUsed_fail() {
        given(userCouponMapper.useCoupon(5L, "SN1")).willReturn(0);
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "UNUSED", null));

        assertThatThrownBy(() -> couponService.markUsed(5L, "SN1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_USED);
    }

    // ---- unfreeze 解冻 ----

    @Test
    @DisplayName("unfreeze - 正常：FROZEN→UNUSED")
    void unfreeze_success() {
        given(userCouponMapper.unfreezeCoupon(5L)).willReturn(1);

        couponService.unfreeze(5L);

        then(userCouponMapper).should().unfreezeCoupon(5L);
    }

    @Test
    @DisplayName("unfreeze - 幂等：已 UNUSED 视为成功")
    void unfreeze_idempotent() {
        given(userCouponMapper.unfreezeCoupon(5L)).willReturn(0);
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "UNUSED", null));

        couponService.unfreeze(5L);   // 不抛异常
    }

    @Test
    @DisplayName("unfreeze - 已 USED 不可解冻：抛 1302")
    void unfreeze_usedFail() {
        given(userCouponMapper.unfreezeCoupon(5L)).willReturn(0);
        given(userCouponMapper.selectById(5L)).willReturn(buildUserCoupon(5L, 1L, 10L, "USED", "SN1"));

        assertThatThrownBy(() -> couponService.unfreeze(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_USED);
    }

    // ---- createCoupon / listMyCoupons ----

    @Test
    @DisplayName("createCoupon - 正常：建券模板并返回 id")
    void createCoupon_success() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Coupon c = invocation.getArgument(0);
            c.setId(3L);
            return null;
        }).when(couponMapper).insert(any(Coupon.class));

        CouponCreateRequest req = new CouponCreateRequest();
        req.setName("新人券");
        req.setDiscount(new BigDecimal("10.00"));
        req.setMinAmount(new BigDecimal("100.00"));
        req.setTotalCount(100);
        req.setExpireAt(LocalDateTime.now().plusDays(30));

        Long id = couponService.createCoupon(req);

        BDDAssertions.then(id).isEqualTo(3L);
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        BDDAssertions.then(captor.getValue().getUsedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("listMyCoupons - 组装模板信息（名称/折扣/门槛/过期时间）")
    void listMyCoupons_joinTemplate() {
        given(userCouponMapper.selectByUserId(1L))
                .willReturn(List.of(buildUserCoupon(5L, 1L, 10L, "UNUSED", null)));
        given(couponMapper.selectById(10L)).willReturn(activeCoupon(10L));

        List<CouponResponse> responses = couponService.listMyCoupons(1L, null);

        BDDAssertions.then(responses).hasSize(1);
        CouponResponse resp = responses.get(0);
        BDDAssertions.then(resp.getId()).isEqualTo(5L);          // user_coupons.id（实例）
        BDDAssertions.then(resp.getCouponId()).isEqualTo(10L);   // coupons.id（模板）
        BDDAssertions.then(resp.getName()).isEqualTo("新人券");
        BDDAssertions.then(resp.getDiscount()).isEqualByComparingTo("10.00");
        BDDAssertions.then(resp.getStatus()).isEqualTo("UNUSED");
    }

    @Test
    @DisplayName("listMyCoupons - 按状态过滤")
    void listMyCoupons_filterByStatus() {
        given(userCouponMapper.selectByUserId(1L))
                .willReturn(List.of(
                        buildUserCoupon(5L, 1L, 10L, "UNUSED", null),
                        buildUserCoupon(6L, 1L, 11L, "USED", "SN1")));
        // 过滤后只组装 USED 的券（id=6），因此只查模板 11
        given(couponMapper.selectById(11L)).willReturn(activeCoupon(11L));

        List<CouponResponse> responses = couponService.listMyCoupons(1L, "USED");

        BDDAssertions.then(responses).hasSize(1);
        BDDAssertions.then(responses.get(0).getId()).isEqualTo(6L);
        org.mockito.Mockito.verify(couponMapper, never()).selectById(10L);
    }
}
