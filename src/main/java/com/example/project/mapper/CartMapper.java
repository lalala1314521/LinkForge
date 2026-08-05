package com.example.project.mapper;

import com.example.project.dto.response.CartItemResponse;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 购物车 Mapper
 * <p>
 * 列表查询 JOIN products 组装商品信息（名称/图片/单价/小计）。
 */
@Mapper
public interface CartMapper {

    /** 加购：不存在则插入，存在则数量累加（ON DUPLICATE KEY UPDATE） */
    @Insert("""
            INSERT INTO cart_items (user_id, product_id, quantity, created_at, updated_at)
            VALUES (#{userId}, #{productId}, #{quantity}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity}, updated_at = NOW()
            """)
    int upsert(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 购物车列表（JOIN 商品信息），按加入时间倒序 */
    @Select("""
            SELECT c.id AS id, c.product_id AS productId, p.name AS productName,
                   p.image_url AS imageUrl, p.price AS price, c.quantity AS quantity,
                   p.price * c.quantity AS subtotal
            FROM cart_items c
            JOIN products p ON p.id = c.product_id
            WHERE c.user_id = #{userId}
            ORDER BY c.id DESC
            """)
    List<CartItemResponse> selectByUserId(Long userId);

    /** 修改数量（归属校验：user_id 必须匹配） */
    @Update("UPDATE cart_items SET quantity = #{quantity}, updated_at = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int updateQuantity(@Param("id") Long id, @Param("userId") Long userId, @Param("quantity") Integer quantity);

    /** 删除条目（归属校验） */
    @Delete("DELETE FROM cart_items WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /** 结算后清空购物车 */
    @Delete("DELETE FROM cart_items WHERE user_id = #{userId}")
    int clearByUserId(Long userId);

    /** 购物车商品总数（导航角标） */
    @Select("SELECT IFNULL(SUM(quantity), 0) FROM cart_items WHERE user_id = #{userId}")
    int countByUserId(Long userId);
}
