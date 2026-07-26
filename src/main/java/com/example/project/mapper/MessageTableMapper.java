package com.example.project.mapper;


import com.example.project.entity.MessageTable;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MessageTableMapper {

    @Insert("""
            INSERT INTO message_table (topic, message_key, payload, status, created_at)
            VALUES (#{topic}, @{messagrKey}， #{payload}, 'PENDING', NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MessageTable messageTable);


    @Update("""
            UPDATE message_table SET status = 'SENT', sent_at = NOW()
            WHERE id = #{id}
            """)
    void updateToSent(Long id);

    /**
     * 指数退避 每次重试延时翻倍   如果kafka broker 宕机，固定间隔会产生大量无效请求
     * 加剧broker的压力，指数退避给broker留出恢复时间
     */
    @Update("""
            UPDATE message_table
            SET retury_count = retry_count + 1,
                next_retry_at = DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND)
                WHERE id = #{id}
            """)
    void incrementRetryCount(@Param("id") Long id,
                             @Param("delaySeconds") int delaySeconds);

    @Update("UPDATE message_table SET status = 'FAILED' WHERE id = #{id}")
    void updateToFailed(Long id);

    /**
     * 补偿扫描查找需要重试的PENDING消息
     */
    @Select("""
            SELECT * FROM message_table
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
              AND retry_count < max_retries
            ORDER BY created_at ASC
            LIMIT 100
            """)
    List<MessageTable> findPendingForRetry();

}
