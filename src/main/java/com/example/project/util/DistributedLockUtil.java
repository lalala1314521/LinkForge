package com.example.project.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工具类
 * 封装tryLock / ubLock 操作，确保finally中只有持有者才能释放锁
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockUtil {

    private final RedissonClient redissonClient;

    /**
     * 尝试加锁
     *
     * @param lockKey 锁的Key
     * @param waitTime 最多等待获取锁的时间
     * @param leaseTime 持有锁的最长时间（超时自动释放）
     * @param unit 时间单位
     * @return true = 加锁成功， false = 获取锁失败
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            if(acquired) {
                log.debug("获取分布式锁成功： key={}", lockKey);
            }else{
                log.debug("获取分布式锁失败（超时或繁忙）： key={}", lockKey);
            }
            return acquired;
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("获取分布式锁时被中断： key={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放锁（只有持有者才能释放，防止误删除他人的锁）
     *
     * @param lockKey 锁的Key
     */
    public void unlock(String lockKey) {
        try{
            RLock lock = redissonClient.getLock(lockKey);
            //isHeldByCurrentThread()保证只有持有当前线程持有锁才释放
            if(lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁成功： key={}", lockKey);
            }
        }catch (Exception e) {
            log.debug("释放分布式锁异常：key={}", lockKey, e);
        }
    }
}