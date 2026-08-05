package com.example.project.mapper;

import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.entity.Order;
import com.example.project.enums.OrderStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订单MyBatis Mapper接口
 */
@Mapper
public interface OrderMapper {

    @Insert("""
            INSERT INTO orders (order_no, user_id, total_amount, coupon_id, coupon_discount, final_amount, status, remark, created_at, updated_at)
            VALUES (#{orderNo}, #{userId}, #{totalAmount}, #{couponId}, #{couponDiscount}, #{finalAmount}, #{status}, #{remark}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Order order);

    @Select("SELECT * FROM orders WHERE id=#{id}")
    Order selectById(Long id);

    @Select("SELECT * FROM orders WHERE order_no = #{orderNo} LIMIT 1")
    Order findByOrderNo(String orderNo);

    /**
     * 动态分页查询XML中定义复杂SQL
     */
    List<Order> selectByCondition(@Param("req") OrderQueryRequest req,
                                  @Param("offset") int offset,
                                  @Param("size") int size);
    long countByCondition(@Param("req") OrderQueryRequest req);

    /**
     * 游标分页查询
     */
    List<Order> selectByCursor(@Param("lastId") Long lastId,
                               @Param("userId") Long userId,
                               @Param("size") int size);
    @Update("UPDATE orders SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    void updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);

    /**
     * 条件更新：仅当 status='PENDING' 时更新为目标状态（超时关单/并发防重复处理用，影响行数 0 = 已被处理）
     */
    @Update("UPDATE orders SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND status='PENDING'")
    int updateStatusIfPending(@Param("id") Long id, @Param("status") OrderStatus status);

    /**
     * 发货：仅当 status='PAID' 时更新为 SHIPPED（管理员发货，影响行数 0 = 非待发货状态）
     */
    @Update("UPDATE orders SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND status='PAID'")
    int updateStatusIfPaid(@Param("id") Long id, @Param("status") OrderStatus status);

    /**
     * 确认收货：仅当 status='SHIPPED' 且属于该用户时更新为 COMPLETED（归属校验）
     */
    @Update("UPDATE orders SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND status='SHIPPED' AND user_id=#{userId}")
    int updateStatusIfShipped(@Param("id") Long id, @Param("userId") Long userId, @Param("status") OrderStatus status);

    /**
     * 扫描超时未支付订单（status=PENDING 且创建时间早于阈值），供超时关单任务使用
     */
    @Select("SELECT * FROM orders WHERE status='PENDING' AND created_at < #{timeoutBefore} ORDER BY created_at ASC")
    List<Order> selectTimeoutPending(@Param("timeoutBefore") java.time.LocalDateTime timeoutBefore);
}