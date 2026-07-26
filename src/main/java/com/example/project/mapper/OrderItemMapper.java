package com.example.project.mapper;


import com.example.project.entity.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("""
            INSERT INTO order_items (order_id, product_id, quantity, price, created_at)
            VALUES (#{orderId}, #{productId}, #{quantity}, #{price}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OrderItem orderItem);

    @Select("SELECT * FORM order_item WHERE order_id = #{orderId}")
    List<OrderItem> selectByOrderId(Long orderId);
}