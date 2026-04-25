package com.example.project.util;

import org.springframework.stereotype.Component;

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

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastStamp = -1L;

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

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if(timestamp < lastStamp){
            throw new RuntimeException("时钟回拨，ID生成暂停" + (lastStamp - timestamp) + "ms");
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

}