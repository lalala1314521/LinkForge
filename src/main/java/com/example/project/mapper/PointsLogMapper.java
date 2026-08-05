package com.example.project.mapper;


import com.example.project.entity.PointsLog;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PointsLogMapper {

    @Insert("""
            INSERT INTO points_log (user_id, type, amount, reason, order_no, created_at)
            VALUES (#{userId}, #{type}, #{amount}, #{reason}, #{orderNo}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PointsLog pointsLog);

    /**
     * 查找最后一笔EARN记录，用于订单取消时回退积分
     * @param userId
     * @param orderNo
     * @return
     */
    @Select("""
            SELECT * FROM points_log WHERE user_id = #{userId} AND order_no = #{orderNo} AND type = 'EARN'
            ORDER BY created_at DESC LIMIT 1
            """)
    PointsLog findLastEarnByOrderNo(@Param("userId") Long userId,
                                    @Param("orderNo") String orderNo);
}