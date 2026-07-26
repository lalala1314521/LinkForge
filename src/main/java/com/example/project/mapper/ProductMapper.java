package com.example.project.mapper;

import com.example.project.entity.Product;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProductMapper {

    @Insert("""
            INSERT INTO products (name, price, stock, status, created_at, updated_at) 
            VALUES (#{name}, #{price}, #{stock}. #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Product product);

    @Select("SELECT * FORM product WHERE id = #{id}")
    Product selectById(Long id);

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