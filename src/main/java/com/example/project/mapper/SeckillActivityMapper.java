package com.example.project.mapper;

import com.example.project.entity.SeckillActivity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 秒杀活动 Mapper
 * <p>
 * 修复：selectActive 缺右引号（'ACTIVE → 'ACTIVE'）；insert 补 seckill_price 列（D2）；
 * 新增 updateStatus / selectByStatus / selectPage / countPage。
 */
@Mapper
public interface SeckillActivityMapper {

    @Insert("""
            INSERT INTO seckill_activities (name, seckill_price, product_id, total_stock,
                 available_stock, start_time, end_time, status, created_at, updated_at)
            VALUES (#{name}, #{seckillPrice}, #{productId}, #{totalStock}, #{availableStock},
                 #{startTime}, #{endTime}, #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SeckillActivity activity);

    @Select("SELECT * FROM seckill_activities WHERE id = #{id}")
    SeckillActivity selectById(Long id);

    /** 在售活动列表（状态=ACTIVE） */
    @Select("SELECT * FROM seckill_activities WHERE status = 'ACTIVE' ORDER BY start_time DESC")
    List<SeckillActivity> selectActive();

    /** 按状态查询（对账/管理用） */
    @Select("SELECT * FROM seckill_activities WHERE status = #{status} ORDER BY start_time DESC")
    List<SeckillActivity> selectByStatus(@Param("status") String status);

    /** 更新活动状态（CREATED/ACTIVE/ENDED） */
    @Update("UPDATE seckill_activities SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 管理端分页查询（按状态过滤），动态 SQL */
    @Select("""
            <script>
            SELECT * FROM seckill_activities
            <where>
                <if test='status != null and status != ""'> AND status = #{status} </if>
            </where>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SeckillActivity> selectPage(@Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(1) FROM seckill_activities
            <where>
                <if test='status != null and status != ""'> AND status = #{status} </if>
            </where>
            </script>
            """)
    long countPage(@Param("status") String status);

    /**
     * DB层扣减可用库存（条件更新，防 DB 超卖兜底）
     */
    @Update("""
            UPDATE seckill_activities
            SET available_stock = available_stock - 1, updated_at = NOW()
            WHERE id = #{activityId} AND available_stock > 0
            """)
    int decrementAvailableStock(Long activityId);

}
