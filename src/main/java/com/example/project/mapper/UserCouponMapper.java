package com.example.project.mapper;

import com.example.project.entity.UserCoupon;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户优惠券（实例）Mapper
 * <p>
 * 状态机不变式：所有状态迁移都是条件更新（WHERE status=旧态），天然防"一券多用"。
 * UNUSED →(freezeCoupon)→ FROZEN →(useCoupon)→ USED；FROZEN →(unfreezeCoupon)→ UNUSED
 */
@Mapper
public interface UserCouponMapper {

    @Insert("""
            INSERT INTO user_coupons (user_id, coupon_id, status, created_at)
            VALUES (#{userId}, #{couponId}, 'UNUSED', NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(UserCoupon userCoupon);

    @Select("SELECT * FROM user_coupons WHERE id = #{id}")
    UserCoupon selectById(Long id);

    /**
     * 查找用户未使用的某张优惠劵
     */
    @Select("""
            SELECT * FROM user_coupons WHERE user_id = #{userId} AND coupon_id = #{couponId} AND status = 'UNUSED'
            LIMIT 1
            """)
    UserCoupon findUnused(@Param("userId") Long userId,
                          @Param("couponId") Long couponId);

    /**
     * 统计用户已领取某模板优惠券的数量（Q2：每用户每模板限领 1 张）
     */
    @Select("SELECT COUNT(1) FROM user_coupons WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int countByUserAndCoupon(@Param("userId") Long userId,
                             @Param("couponId") Long couponId);

    /**
     * 查询用户全部优惠券（领取时间倒序），"我的优惠券"列表用
     */
    @Select("SELECT * FROM user_coupons WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<UserCoupon> selectByUserId(@Param("userId") Long userId);

    /**
     * 冻结优惠劵：UNUSED → FROZEN，并记录占用订单号（order_no 用于取消链路定位解冻目标）
     */
    @Update("""
            UPDATE user_coupons SET status = 'FROZEN', frozen_at = NOW(), order_no = #{orderNo}
            WHERE id = #{id} AND status = 'UNUSED'
            """)
    int freezeCoupon(@Param("id") Long id,
                     @Param("orderNo") String orderNo);

    /**
     * 解冻优惠劵：FROZEN → UNUSED，同时清空占用订单号
     */
    @Update("""
            UPDATE user_coupons SET status = 'UNUSED', frozen_at = NULL, order_no = NULL
            WHERE id = #{id} AND status = 'FROZEN'
            """)
    int unfreezeCoupon(Long id);

    /**
     * 使用优惠劵：FROZEN → USED，记录使用时间与订单号
     */
    @Update("""
            UPDATE user_coupons SET status = 'USED', used_at = NOW(), order_no = #{orderNo}
            WHERE id = #{id} AND status = 'FROZEN'
            """)
    int useCoupon(@Param("id") Long id,
                  @Param("orderNo") String orderNo);
}
