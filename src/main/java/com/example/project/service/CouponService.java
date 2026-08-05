package com.example.project.service;

import com.example.project.dto.request.CouponCreateRequest;
import com.example.project.dto.response.CouponResponse;
import com.example.project.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券服务接口
 * <p>
 * 状态机（不变式：所有迁移都是 WHERE status=旧态 条件更新）：
 * <pre>
 * UNUSED --冻结(freezeForOrder)--> FROZEN --支付确认(markUsed)--> USED
 *    ^                              |
 *    └--------- 解冻(unfreeze) ------┘
 * </pre>
 * 命名语义：freezeForOrder/markUsed/unfreeze 的入参 userCouponId 是 user_coupons.id（实例），
 * 不是 coupons 模板 id；orders.coupon_id 与消息 DTO 的 couponId 同样存的是实例 id。
 */
public interface CouponService {

    /**
     * 领取：校验券存在/ACTIVE/未过期/未领完/不重复领（Q2：每用户每模板限 1 张）
     *
     * @return user_coupon.id（实例 id）
     */
    Long claim(Long userId, Long couponId);

    /**
     * 下单冻结：校验归属/有效/门槛 → 条件冻结(UNUSED→FROZEN, 记 order_no)
     *
     * @return 抵扣金额
     */
    BigDecimal freezeForOrder(Long userCouponId, Long userId, String orderNo, BigDecimal orderTotal);

    /**
     * 解冻：FROZEN→UNUSED（幂等：已 UNUSED 视为成功；已 USED 不可解冻）
     */
    void unfreeze(Long userCouponId);

    /**
     * 支付确认：FROZEN→USED 记 order_no（幂等：已 USED 同单视为成功）
     */
    void markUsed(Long userCouponId, String orderNo);

    /**
     * 我的优惠券列表（可按状态过滤）
     */
    List<CouponResponse> listMyCoupons(Long userId, String status);

    /**
     * 管理员创建优惠券模板
     *
     * @return coupons 模板 id
     */
    Long createCoupon(CouponCreateRequest request);

    /**
     * 可领取列表（未过期/未领完/ACTIVE）
     */
    List<Coupon> listAvailable();
}
