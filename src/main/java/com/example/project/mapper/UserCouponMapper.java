package com.example.project.mapper;

import com.example.project.entity.UserCoupon;
import org.apache.ibatis.annotations.*;

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
            SELECT * FROM user_coupons WHERE user_id = #{userid} AND coupon_id = #{couponId} AND status = 'UNUSED'
            LIMIT 1
            """)
    UserCoupon findUnused(@Param("userId") Long userId,
                          @Param("couponId") Long couponId);

    /**
     * 冻结优惠劵
     */
    @Update("""
            UPDATE user_coupons SET status = 'FROZEN', frozen_at = NOW()
            WHERE id = #{id} AND status = 'UNUSED'
            """)
    int freezeCoupon(Long id);

    /**
     * 解冻优惠劵
     */
    @Update("""
            UPDATE user_coupons SET status = 'UNUSED', frozen_at = NULL
            WHERE id = #{id} AND status = 'FROZEN'
            """)
    int unfreezeCoupon(Long id);

    /**
     * 使用优惠劵
     */
    @Update("""
            UPDATE user_coupons SET status = 'USED', used_at = NOW(), order_no = #{orderNo}
            WHERE id = #{id} AND status = 'FROZEN'
            """)
    int useCoupon(@Param("id") Long id,
                  @Param("orderNo") String orderNo);
}