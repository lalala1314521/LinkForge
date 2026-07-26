package com.example.project.mapper;

import com.example.project.entity.SeckillActivity;
import org.apache.ibatis.annotations.*;

import java.util.List;


@Mapper
public interface SeckillActivityMapper {

    @Insert("""
            INSERT INTO seckill_activities (name, product_id, total_stock,
            available_stock,
                 start_time, end_time, status, created_at, updated_at)
            VALUES (#{name}, #{productId}, #{totalStock}, #{availableStock},
                 #{startTime}, #{endTime}, #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SeckillActivity activity);

    @Select("SELECT * FROM seckill_activities WHERE id = #{id}")
    SeckillActivity selectById(long id);

    @Select("SELECT * FROM seckill_activities WHERE status = 'ACTIVE")
    List<SeckillActivity> selectActive();


    /**
     * DB层扣减可用库存
     */
    @Update("""
            UPDATE seckill_activities
            SET available_stock = available_stock - 1, updated_at = NOW()
            WHERE id = #{activityId} AND available_stock > 0
            """)
    int decrementAvailableStock(Long activityId);
    
}