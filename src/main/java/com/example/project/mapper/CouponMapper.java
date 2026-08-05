package com.example.project.mapper;

import com.example.project.entity.Coupon;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 优惠券模板 Mapper
 * <p>
 * 语义澄清：coupons.used_count 在 DDL 中注释为"已使用数量"，
 * 但领取校验用 used_count &lt; total_count 当"已领取数"用（领取时自增），保持既有设计。
 */
@Mapper
public interface CouponMapper {

    @Select("SELECT * FROM coupons WHERE id = #{id}")
    Coupon selectById(Long id);

    @Insert("""
            INSERT INTO coupons (name, discount, min_amount, total_count, used_count, status, expire_at, created_at)
            VALUES (#{name}, #{discount}, #{minAmount}, #{totalCount}, #{usedCount}, #{status}, #{expireAt}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Coupon coupon);

    /**
     * 领取时原子自增已领取数：影响行数=0 → 已领完/已失效（条件更新防超发）
     */
    @Update("""
            UPDATE coupons SET used_count = used_count + 1
            WHERE id = #{id} AND status = 'ACTIVE'
              AND expire_at > NOW() AND used_count < total_count
            """)
    int incrementClaimCount(Long id);

    /**
     * 可领取列表：未过期、未领完、状态 ACTIVE
     */
    @Select("""
            SELECT * FROM coupons
            WHERE status = 'ACTIVE' AND expire_at > NOW() AND used_count < total_count
            ORDER BY created_at DESC
            """)
    List<Coupon> selectAvailable();
}
