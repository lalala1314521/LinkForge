package com.example.project.mapper;

import com.example.project.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Insert("""
            INSERT INTO products (name, price, stock, status, image_url, created_at, updated_at) 
            VALUES (#{name}, #{price}, #{stock}, #{status}, #{imageUrl}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Product product);

    @Select("SELECT * FROM products WHERE id = #{id}")
    Product selectById(Long id);

    /**
     * 更新商品（名称/价格/库存/状态/图片），逻辑下架 delete 也复用本方法（status=OFF_SALE）
     */
    @Update("""
            UPDATE products SET name = #{name}, price = #{price}, stock = #{stock},
                   status = #{status}, image_url = #{imageUrl}, updated_at = NOW()
            WHERE id = #{id}
            """)
    int update(Product product);

    /**
     * 分页查询（keyword 模糊匹配名称 + status 精确过滤），动态 SQL
     */
    @Select("""
            <script>
            SELECT * FROM products
            <where>
                <if test='keyword != null and keyword != ""'> AND name LIKE CONCAT('%', #{keyword}, '%') </if>
                <if test='status != null and status != ""'> AND status = #{status} </if>
            </where>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Product> selectPage(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("size") int size);

    /**
     * 分页总数
     */
    @Select("""
            <script>
            SELECT COUNT(1) FROM products
            <where>
                <if test='keyword != null and keyword != ""'> AND name LIKE CONCAT('%', #{keyword}, '%') </if>
                <if test='status != null and status != ""'> AND status = #{status} </if>
            </where>
            </script>
            """)
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status);

    /**
     * 扣减库存
     * @param product
     * @param quantity
     * @return
     */
    @Update("""
            UPDATE products SET stock = stock - #{quantity}, updated_at = NOW()
            WHERE id = #{productId} AND stock >= #{quantity}
            """)
    int decrementStock(@Param("productId") Long product,
                       @Param("quantity") Integer quantity);

    /**
     *回退库存
     * @param product
     * @param quantity
     * @return
     */
    @Update("""
            UPDATE products SET stock = stock + #{quantity}, updated_at = NOW()
            WHERE id = #{productId}
            """)
    int incrementStock(@Param("productId") Long product,
                       @Param("quantity") Integer quantity);
}