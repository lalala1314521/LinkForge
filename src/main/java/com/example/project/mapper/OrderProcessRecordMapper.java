package com.example.project.mapper;

import com.example.project.entity.OrderProcessRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单事件处理记录 Mapper（消费幂等）
 */
@Mapper
public interface OrderProcessRecordMapper {

    /**
     * 插入处理记录；重复（order_no + event_type）会抛 DuplicateKeyException
     */
    @Insert("""
            INSERT INTO order_process_record (order_no, event_type, status, created_at)
            VALUES (#{orderNo}, #{eventType}, #{status}, NOW())
            """)
    void insert(OrderProcessRecord record);

    /**
     * 按订单号 + 事件类型查询处理记录（对账/审计用）
     */
    @Select("""
            SELECT * FROM order_process_record
            WHERE order_no = #{orderNo} AND event_type = #{eventType} LIMIT 1
            """)
    OrderProcessRecord selectByOrderNoAndType(@Param("orderNo") String orderNo,
                                              @Param("eventType") String eventType);
}
