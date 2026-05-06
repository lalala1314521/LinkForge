package com.example.project.util;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;


/**
 * 雪花ID生成器（63位分布式唯一ID）
 * 1位符号位+41时间戳+10位机器ID+12位序列号
 */
@Component
public class SnowflakeIdGenerator {

    /**自定义纪元（2023-11-14 00：00：00 UTC) */
    private static final long EPOCH = 1700000000000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long MAX_BACKWARD_MS = 5L;

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastStamp = -1L;

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);
    private final AtomicLong backwardCount = new AtomicLong();

    public SnowflakeIdGenerator() {
        this(1L, 1L);
    }
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if(workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 超出范围");
        }
        if(datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 超出范围");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }
    /** @return 时钟回拨次数累计 */
    public long getBackwardCount() {
        return backwardCount.get();
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if(timestamp < lastStamp){
            long backward = lastStamp - timestamp;
            backwardCount.incrementAndGet();

            if(backward <= MAX_BACKWARD_MS) {
                log.info("[Snowflake]时钟回拨 {}ms(<={}ms), 自旋等待中...", backward, MAX_BACKWARD_MS);
                while ((timestamp = System.currentTimeMillis()) < lastStamp) {
                    //busy-wait
                }
            }else {
                throw new ClockBackwardException(
                        String.format("[Snowflake] 时钟回拨 %dms 超过阈值 %dms, ID生成中断！" +
                                "请检查NTP同步。 workerId=%d, datacenterId=%d",
                                backward, MAX_BACKWARD_MS, workerId, datacenterId));
            }
        }
        if(timestamp == lastStamp){
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if(sequence == 0){
                while((timestamp = System.currentTimeMillis()) <= lastStamp){
                    //busy-wait
                }
            }
        }else{
            sequence = 0L;
        }
        lastStamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    public static class ClockBackwardException extends RuntimeException {
        public ClockBackwardException(String message) {
            super(message);
        }
    }

}