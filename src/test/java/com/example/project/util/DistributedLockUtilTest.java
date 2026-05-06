package com.example.project.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * DistributedLockUtil 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("分布式锁工具单元测试")
class DistributedLockUtilTest {

    @Mock RedissonClient redissonClient;
    @Mock RLock rLock;

    @InjectMocks DistributedLockUtil lockUtil;

    private static final String LOCK_KEY = "order:create:1";

    @BeforeEach
    void setUp() {
        given(redissonClient.getLock(LOCK_KEY)).willReturn(rLock);
    }

    @Test
    @DisplayName("tryLock 成功 - 返回 true")
    void tryLock_success() throws InterruptedException {
        given(rLock.tryLock(0L, 5L, TimeUnit.SECONDS)).willReturn(true);

        boolean result = lockUtil.tryLock(LOCK_KEY, 0, 5, TimeUnit.SECONDS);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("tryLock 失败（超时）- 返回 false")
    void tryLock_timeout_returnsFalse() throws InterruptedException {
        given(rLock.tryLock(0L, 5L, TimeUnit.SECONDS)).willReturn(false);

        boolean result = lockUtil.tryLock(LOCK_KEY, 0, 5, TimeUnit.SECONDS);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("unlock - 持有者释放锁成功")
    void unlock_heldByCurrentThread() {
        given(rLock.isLocked()).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        lockUtil.unlock(LOCK_KEY);

        then(rLock).should().unlock();
    }

    @Test
    @DisplayName("unlock - 非持有者不释放他人的锁")
    void unlock_notHeldByCurrentThread_noUnlock() {
        given(rLock.isLocked()).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(false);

        lockUtil.unlock(LOCK_KEY);

        then(rLock).should(org.mockito.Mockito.never()).unlock();
    }
}
