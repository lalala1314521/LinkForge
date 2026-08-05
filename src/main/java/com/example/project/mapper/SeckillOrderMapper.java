package com.example.project.mapper;

import com.example.project.entity.SeckillOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 秒杀订单 Mapper（当前缺失，属死代码缺口，本批次补齐）
 * <p>
 * 幂等锚点：seckill_orders.idx_order_no 唯一键 —— Consumer 先 INSERT 占位，
 * 重复 order_no → DuplicateKeyException → 幂等跳过（D7）。
 */
@Mapper
public interface SeckillOrderMapper {

    @Insert("""
            INSERT INTO seckill_orders (order_no, user_id, activity_id, product_id, price, status, created_at, updated_at)
            VALUES (#{orderNo}, #{userId}, #{activityId}, #{productId}, #{price}, #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SeckillOrder order);

    @Select("SELECT * FROM seckill_orders WHERE order_no = #{orderNo} LIMIT 1")
    SeckillOrder selectByOrderNo(String orderNo);

    @Select("SELECT * FROM seckill_orders WHERE activity_id = #{activityId}")
    List<SeckillOrder> selectByActivityId(Long activityId);

    /** 我的秒杀订单：按用户查询（倒序，最新在前） */
    @Select("SELECT * FROM seckill_orders WHERE user_id = #{userId} ORDER BY id DESC")
    List<SeckillOrder> selectByUserId(Long userId);

    @Select("SELECT COUNT(1) FROM seckill_orders WHERE activity_id = #{activityId}")
    long countByActivityId(Long activityId);

    /** 预留：PENDING→PAID 支付流转（批次 4） */
    @Update("UPDATE seckill_orders SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
