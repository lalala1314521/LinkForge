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
            INSERT INTO orders (order_no, user_id, total_amount, status, remark, created_at, updated_at)
            VALUES (#{orderNo}, #{userId}, #{totalAmount}, #{status}, #{remark}, NOW(), NOW())
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
}