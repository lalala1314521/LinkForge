# Snowflake ID 生成器完整实现

> 三个文件合并为一个 Markdown 文档，供学习参考使用。
> 实际使用时请将代码复制到对应的 `.java` 文件中，包名需调整为项目实际包名。

---

## 文件清单

| 文件 | 包路径 | 职责 |
|---|---|---|
| `SnowflakeIdGenerator.java` | `com.example.project.util` | 核心ID生成，含Javadoc、时钟回拨策略、Metrics |
| `WorkerIdAllocator.java` | `com.example.project.util` | 基于Redis的workerId自动分配，心跳续约 |
| `SnowflakeAutoConfiguration.java` | `com.example.project.config` | Spring Boot自动配置，支持手动/Redis两种模式 |

---

## 一、SnowflakeIdGenerator.java

```java
package com.example.project.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法分布式唯一ID生成器（Snowflake ID Generator）
 *
 * <h2>ID结构（63位，最高位固定为0保证正数）</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  0  │        41位时间戳         │ 5位数据中心 │ 5位工作节点 │ 12位序列号 │
 * └──────────────────────────────────────────────────────────────────┘
 *   1位       41位                     5位           5位          12位
 * 符号位  毫秒级时间戳(相对纪元)    datacenterId   workerId      序列号
 * </pre>
 *
 * <h2>容量说明</h2>
 * <ul>
 *   <li>时间戳：可用约 69 年（2^41 ms ÷ 365d ÷ 24h ÷ 3600s ÷ 1000ms ≈ 69.7年）</li>
 *   <li>数据中心：最多 32 个（2^5）</li>
 *   <li>工作节点：每个数据中心最多 32 个（2^5），全局共 1024 个节点</li>
 *   <li>序列号：每毫秒每节点最多 4096 个ID（2^12）</li>
 *   <li>理论吞吐：单节点 4,096,000 ID/s</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * {@link #nextId()} 方法使用 {@code synchronized} 保证单节点线程安全。
 * 分布式场景下通过 {@code workerId + datacenterId} 的唯一性保证全局唯一。
 *
 * <h2>时钟回拨处理策略</h2>
 * <ol>
 *   <li>回拨量 ≤ {@value #MAX_BACKWARD_MS} ms：自旋等待时钟追上，适合短暂 NTP 调整</li>
 *   <li>回拨量 &gt; {@value #MAX_BACKWARD_MS} ms：直接抛出异常，防止ID重复</li>
 * </ol>
 *
 * <h2>Metrics 指标（需注入 MeterRegistry）</h2>
 * <ul>
 *   <li>{@code snowflake.id.generated} - ID生成总次数（Counter）</li>
 *   <li>{@code snowflake.clock.backward} - 时钟回拨发生次数（Counter）</li>
 *   <li>{@code snowflake.sequence.overflow} - 序列号溢出（同毫秒超4096次）的次数（Counter）</li>
 *   <li>{@code snowflake.id.generation.time} - ID生成耗时分布（Timer）</li>
 * </ul>
 *
 * @author YourName
 * @version 2.0
 * @see WorkerIdAllocator
 * @see SnowflakeAutoConfiguration
 */
public class SnowflakeIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    // ===================== 位域常量 =====================

    /**
     * 自定义纪元（2023-11-14 00:00:00 UTC）。
     * <p>使用自定义纪元而非 Unix 纪元（1970-01-01），可将41位时间戳的可用年限
     * 从"1970年起69年"推迟为"2023年起69年"，即可用至约 2093 年。</p>
     */
    private static final long EPOCH = 1_700_000_000_000L;

    /** 工作节点ID位数（5位，支持0~31共32个节点） */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心ID位数（5位，支持0~31共32个数据中心） */
    private static final long DATACENTER_BITS = 5L;

    /** 序列号位数（12位，每毫秒最多4096个序列） */
    private static final long SEQUENCE_BITS = 12L;

    /** workerId 最大值（31） */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);      // 31

    /** datacenterId 最大值（31） */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS); // 31

    /** workerId 左移位数（12） */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** datacenterId 左移位数（17） */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳左移位数（22） */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_BITS;

    /** 序列号掩码，用于序列号溢出归零（0xFFF = 4095） */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    // ===================== 时钟回拨容忍阈值 =====================

    /**
     * 允许自旋等待的最大时钟回拨毫秒数。
     * <p>回拨量在此范围内，通过忙等待（busy-wait）直至时钟追上；
     * 超过此值则直接抛出异常，由调用方决策（熔断/降级/告警）。</p>
     */
    private static final long MAX_BACKWARD_MS = 5L;

    // ===================== 实例状态 =====================

    /** 工作节点ID（由 WorkerIdAllocator 分配或手动指定） */
    private final long workerId;

    /** 数据中心ID */
    private final long datacenterId;

    /** 毫秒内自增序列号 */
    private long sequence = 0L;

    /** 上次生成ID的时间戳（毫秒） */
    private long lastStamp = -1L;

    // ===================== Metrics =====================

    /** ID生成总计数器 */
    private final Counter generatedCounter;

    /** 时钟回拨事件计数器 */
    private final Counter clockBackwardCounter;

    /** 序列号溢出（同毫秒超过4096次，需等待下一毫秒）计数器 */
    private final Counter sequenceOverflowCounter;

    /** ID生成耗时分布计时器 */
    private final Timer generationTimer;

    /** 时钟回拨累计次数，用于日志与监控告警 */
    private final AtomicLong backwardCount = new AtomicLong(0);

    // ===================== 构造方法 =====================

    /**
     * 默认构造（无 Metrics，适合单元测试）。
     * <p>workerId=1，datacenterId=1，Metrics 使用空实现（Noop）。</p>
     */
    public SnowflakeIdGenerator() {
        this(1L, 1L, null);
    }

    /**
     * 指定节点ID构造（无 Metrics）。
     *
     * @param workerId     工作节点ID，范围 [0, 31]
     * @param datacenterId 数据中心ID，范围 [0, 31]
     * @throws IllegalArgumentException 如果ID超出合法范围
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this(workerId, datacenterId, null);
    }

    /**
     * 完整构造（生产推荐）。
     *
     * @param workerId     工作节点ID，范围 [0, 31]
     * @param datacenterId 数据中心ID，范围 [0, 31]
     * @param registry     Micrometer MeterRegistry，传 {@code null} 则禁用 Metrics
     * @throws IllegalArgumentException 如果ID超出合法范围
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId, MeterRegistry registry) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("workerId 超出范围，合法值为 [0, %d]，实际为 %d", MAX_WORKER_ID, workerId));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenterId 超出范围，合法值为 [0, %d]，实际为 %d", MAX_DATACENTER_ID, datacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;

        // 初始化 Metrics（registry 为 null 时使用 Noop，避免 NPE）
        if (registry != null) {
            this.generatedCounter = Counter.builder("snowflake.id.generated")
                    .description("雪花ID生成总次数")
                    .tag("workerId", String.valueOf(workerId))
                    .tag("datacenterId", String.valueOf(datacenterId))
                    .register(registry);
            this.clockBackwardCounter = Counter.builder("snowflake.clock.backward")
                    .description("时钟回拨发生次数")
                    .tag("workerId", String.valueOf(workerId))
                    .register(registry);
            this.sequenceOverflowCounter = Counter.builder("snowflake.sequence.overflow")
                    .description("序列号溢出（同毫秒超4096次）次数")
                    .tag("workerId", String.valueOf(workerId))
                    .register(registry);
            this.generationTimer = Timer.builder("snowflake.id.generation.time")
                    .description("ID生成耗时分布")
                    .tag("workerId", String.valueOf(workerId))
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);
        } else {
            // Noop 实现，不记录任何指标（测试/轻量部署场景）
            this.generatedCounter = null;
            this.clockBackwardCounter = null;
            this.sequenceOverflowCounter = null;
            this.generationTimer = null;
        }

        log.info("[Snowflake] 初始化完成，workerId={}, datacenterId={}, epoch={}, metricsEnabled={}",
                workerId, datacenterId, EPOCH, registry != null);
    }

    // ===================== 核心方法 =====================

    /**
     * 生成下一个全局唯一ID（线程安全）。
     *
     * <p>生成逻辑：</p>
     * <ol>
     *   <li>获取当前时间戳，防御负值（时钟异常）</li>
     *   <li>检测时钟回拨，按阈值决策自旋等待或抛异常</li>
     *   <li>同一毫秒内递增序列号；溢出时等待下一毫秒</li>
     *   <li>拼接各位段，返回64位正整数ID</li>
     * </ol>
     *
     * @return 全局唯一的 63 位正整数ID
     * @throws ClockBackwardException 时钟回拨量超过 {@value #MAX_BACKWARD_MS} ms 时抛出
     */
    public synchronized long nextId() {
        // 若注入了 Timer，用 Timer.record 包裹整个生成过程
        if (generationTimer != null) {
            return generationTimer.record(this::doNextId);
        }
        return doNextId();
    }

    /**
     * 实际ID生成逻辑（由 {@link #nextId()} 内部调用）。
     *
     * @return 生成的唯一ID
     * @throws ClockBackwardException 时钟回拨超阈值
     */
    private long doNextId() {
        long timestamp = currentTimeMillis();

        // ---------- 时钟回拨处理 ----------
        if (timestamp < lastStamp) {
            long backward = lastStamp - timestamp;
            recordClockBackward(backward);

            if (backward <= MAX_BACKWARD_MS) {
                // 小幅回拨：自旋等待，直到时钟追上 lastStamp
                log.warn("[Snowflake] 检测到时钟回拨 {}ms（≤{}ms），自旋等待中...", backward, MAX_BACKWARD_MS);
                while ((timestamp = currentTimeMillis()) < lastStamp) {
                    // busy-wait，预期等待时间极短（微秒级）
                }
            } else {
                // 大幅回拨：直接抛出，由调用方降级处理
                throw new ClockBackwardException(
                        String.format("[Snowflake] 时钟回拨 %dms 超过阈值 %dms，ID生成中断！" +
                                "请检查 NTP 同步或联系运维。workerId=%d, datacenterId=%d",
                                backward, MAX_BACKWARD_MS, workerId, datacenterId));
            }
        }

        // ---------- 序列号处理 ----------
        if (timestamp == lastStamp) {
            // 同一毫秒内，序列号自增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列号溢出（本毫秒已用完4096个），等待下一毫秒
                recordSequenceOverflow();
                timestamp = waitNextMillis(lastStamp);
            }
        } else {
            // 新的毫秒，序列号归零
            sequence = 0L;
        }

        lastStamp = timestamp;

        // ---------- 拼装ID ----------
        long id = ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;

        recordGenerated();
        return id;
    }

    // ===================== 辅助方法 =====================

    /**
     * 获取当前时间戳（毫秒），并做负值防御。
     *
     * <p>理论上 {@link System#currentTimeMillis()} 不会返回负值，
     * 但在极端虚拟化或时钟篡改场景下可能发生。此处做一层防御性校验。</p>
     *
     * @return 当前毫秒时间戳（保证 &gt; 0）
     * @throws IllegalStateException 如果系统时钟返回了不合理的负值
     */
    private long currentTimeMillis() {
        long ts = System.currentTimeMillis();
        if (ts <= 0) {
            // 极罕见：虚拟机时钟异常或时间戳溢出
            throw new IllegalStateException(
                    "[Snowflake] System.currentTimeMillis() 返回了非正值：" + ts +
                    "，请检查系统时钟配置。");
        }
        return ts;
    }

    /**
     * 自旋等待直到下一毫秒（序列号溢出时调用）。
     *
     * @param lastTimestamp 上次使用的时间戳
     * @return 严格大于 lastTimestamp 的新时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp;
        do {
            timestamp = currentTimeMillis();
        } while (timestamp <= lastTimestamp);
        return timestamp;
    }

    /**
     * 记录ID生成计数（安全调用，registry 为 null 时跳过）。
     */
    private void recordGenerated() {
        if (generatedCounter != null) {
            generatedCounter.increment();
        }
    }

    /**
     * 记录时钟回拨事件。
     *
     * @param backwardMs 回拨毫秒数
     */
    private void recordClockBackward(long backwardMs) {
        long total = backwardCount.incrementAndGet();
        log.warn("[Snowflake] 时钟回拨事件，本次回拨={}ms，累计回拨次数={}", backwardMs, total);
        if (clockBackwardCounter != null) {
            clockBackwardCounter.increment();
        }
    }

    /**
     * 记录序列号溢出事件。
     */
    private void recordSequenceOverflow() {
        log.debug("[Snowflake] 序列号溢出，等待下一毫秒。workerId={}, datacenterId={}", workerId, datacenterId);
        if (sequenceOverflowCounter != null) {
            sequenceOverflowCounter.increment();
        }
    }

    // ===================== 辅助工具方法 =====================

    /**
     * 解析ID中的时间戳（相对于自定义纪元的毫秒偏移量转为 UTC 毫秒时间戳）。
     *
     * <p>可用于排查问题：通过ID反推生成时间。</p>
     *
     * @param id 雪花ID
     * @return 生成该ID时的 UTC 毫秒时间戳
     */
    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT) + EPOCH;
    }

    /**
     * 解析ID中的 datacenterId。
     *
     * @param id 雪花ID
     * @return datacenterId（0~31）
     */
    public static long extractDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 解析ID中的 workerId。
     *
     * @param id 雪花ID
     * @return workerId（0~31）
     */
    public static long extractWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 解析ID中的序列号。
     *
     * @param id 雪花ID
     * @return 序列号（0~4095）
     */
    public static long extractSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    // ===================== Getter =====================

    /** @return 当前工作节点ID */
    public long getWorkerId() { return workerId; }

    /** @return 当前数据中心ID */
    public long getDatacenterId() { return datacenterId; }

    /** @return 时钟回拨累计次数（运行期统计） */
    public long getBackwardCount() { return backwardCount.get(); }

    // ===================== 内部异常类 =====================

    /**
     * 时钟回拨异常，当回拨量超过 {@value #MAX_BACKWARD_MS} ms 时抛出。
     *
     * <p>调用方应捕获此异常并执行降级策略，例如：</p>
     * <ul>
     *   <li>返回错误码，要求客户端稍后重试</li>
     *   <li>切换到备用ID生成策略（UUID 等）</li>
     *   <li>触发告警通知运维检查 NTP 同步</li>
     * </ul>
     */
    public static class ClockBackwardException extends RuntimeException {
        public ClockBackwardException(String message) {
            super(message);
        }
    }
}
```

---

## 二、WorkerIdAllocator.java

```java
package com.example.project.util;

import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis（Redisson）的雪花算法工作节点ID自动分配器。
 *
 * <h2>设计目标</h2>
 * 在分布式部署场景下，多个服务实例启动时需要获取唯一的 {@code workerId}（0~31）
 * 和 {@code datacenterId}（0~31）。手动配置易出错，本分配器通过 Redis 实现自动分配，
 * 保证全局唯一。
 *
 * <h2>分配策略</h2>
 * <ol>
 *   <li>Redis 中维护一个已使用 ID 集合（{@code snowflake:worker:used}）</li>
 *   <li>节点启动时，从 0 开始遍历，找到第一个未被占用的 workerId</li>
 *   <li>将 {@code "ip:port -> workerId"} 写入注册表（{@code snowflake:worker:registry}），
 *       同时写入 workerId 对应的心跳键（带 TTL）</li>
 *   <li>后台线程每隔 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒续约心跳键</li>
 *   <li>节点正常下线时，释放 workerId；异常宕机时，心跳键过期后 workerId 自动回收</li>
 * </ol>
 *
 * <h2>Redis 数据结构</h2>
 * <pre>
 * snowflake:worker:registry   Hash   { "192.168.1.10:8080" -> "3" }   节点注册表
 * snowflake:worker:heartbeat:{workerId}  String（带TTL）  心跳存活标志
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在 Spring Boot 中通常由 SnowflakeAutoConfiguration 自动完成，无需手动调用
 * WorkerIdAllocator allocator = new WorkerIdAllocator(redissonClient, "my-service", 8080);
 * long workerId = allocator.allocate();
 * }</pre>
 *
 * @author YourName
 * @version 1.0
 * @see SnowflakeIdGenerator
 * @see SnowflakeAutoConfiguration
 */
public class WorkerIdAllocator implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WorkerIdAllocator.class);

    // ===================== Redis Key 常量 =====================

    /** 节点注册表：Hash，key=实例标识，value=workerId */
    private static final String REGISTRY_KEY = "snowflake:worker:registry";

    /** 心跳键前缀：String（带TTL），key=workerId，value=实例标识 */
    private static final String HEARTBEAT_KEY_PREFIX = "snowflake:worker:heartbeat:";

    // ===================== 配置常量 =====================

    /** 心跳续约间隔（秒）*/
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;

    /** 心跳键TTL（秒），应大于心跳间隔的3倍，防止短暂网络抖动导致误判宕机 */
    private static final int HEARTBEAT_TTL_SECONDS = 45;

    /** workerId 最大值（与 SnowflakeIdGenerator 保持一致）*/
    private static final int MAX_WORKER_ID = 31;

    // ===================== 实例状态 =====================

    private final RedissonClient redisson;

    /** 实例唯一标识：ip:port（用于注册表中的 key）*/
    private final String instanceId;

    /** 已分配的 workerId（-1 表示尚未分配）*/
    private volatile long allocatedWorkerId = -1L;

    /** 心跳续约调度器 */
    private final ScheduledExecutorService heartbeatScheduler;

    // ===================== 构造方法 =====================

    /**
     * 构造分配器。
     *
     * @param redisson    Redisson 客户端（已连接 Redis）
     * @param serviceName 服务名称（用于生成实例标识，如 "user-service"）
     * @param port        服务端口（用于生成实例标识，如 8080）
     */
    public WorkerIdAllocator(RedissonClient redisson, String serviceName, int port) {
        this.redisson = redisson;
        this.instanceId = buildInstanceId(serviceName, port);
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-heartbeat");
            t.setDaemon(true); // 守护线程，不阻止JVM退出
            return t;
        });
    }

    // ===================== 核心方法 =====================

    /**
     * 分配工作节点ID（幂等：同一实例多次调用返回相同ID）。
     *
     * <p>分配流程：</p>
     * <ol>
     *   <li>检查注册表中是否已有本实例的记录（服务重启复用上次的ID）</li>
     *   <li>若无记录，遍历 0~31 找到空闲的 workerId</li>
     *   <li>注册并启动心跳续约</li>
     * </ol>
     *
     * @return 分配到的 workerId（0~31）
     * @throws IllegalStateException 所有 workerId（0~31）均已被占用时抛出
     */
    public synchronized long allocate() {
        if (allocatedWorkerId >= 0) {
            return allocatedWorkerId; // 已分配，直接返回（幂等）
        }

        RMap<String, String> registry = redisson.getMap(REGISTRY_KEY);

        // Step 1：检查本实例是否已有注册记录（服务重启场景）
        String existingId = registry.get(instanceId);
        if (existingId != null) {
            long wid = Long.parseLong(existingId);
            // 验证对应心跳键是否仍存活（若TTL已过期，说明之前是宕机，需重新分配）
            if (isHeartbeatAlive(wid)) {
                log.info("[WorkerIdAllocator] 复用上次分配的 workerId={}，instanceId={}", wid, instanceId);
                allocatedWorkerId = wid;
                startHeartbeat();
                return allocatedWorkerId;
            } else {
                // 心跳已过期，清理旧记录后重新分配
                log.warn("[WorkerIdAllocator] 发现过期注册记录，workerId={}，重新分配", wid);
                registry.remove(instanceId);
            }
        }

        // Step 2：遍历找空闲的 workerId
        for (int wid = 0; wid <= MAX_WORKER_ID; wid++) {
            // 尝试注册心跳键（用 SetNX 语义：仅当 key 不存在时才设置）
            boolean acquired = tryAcquireHeartbeat(wid, instanceId);
            if (acquired) {
                registry.put(instanceId, String.valueOf(wid));
                allocatedWorkerId = wid;
                startHeartbeat();
                log.info("[WorkerIdAllocator] 成功分配 workerId={}，instanceId={}", wid, instanceId);
                return allocatedWorkerId;
            }
        }

        // Step 3：所有ID均被占用（超过1024个节点，不应出现在正常规划中）
        throw new IllegalStateException(
                "[WorkerIdAllocator] 所有 workerId（0~" + MAX_WORKER_ID + "）均已被占用！" +
                "请检查是否有僵尸节点未释放，或重新规划节点容量。instanceId=" + instanceId);
    }

    /**
     * 释放当前实例占用的 workerId（Spring 容器关闭时自动调用）。
     * <p>实现 {@link DisposableBean}，在 {@code ApplicationContext} 关闭时触发。</p>
     */
    @Override
    public void destroy() {
        heartbeatScheduler.shutdownNow();
        if (allocatedWorkerId >= 0) {
            try {
                // 删除心跳键，让其他节点可立即复用该 workerId
                redisson.getBucket(HEARTBEAT_KEY_PREFIX + allocatedWorkerId).delete();
                redisson.<String, String>getMap(REGISTRY_KEY).remove(instanceId);
                log.info("[WorkerIdAllocator] 已释放 workerId={}，instanceId={}", allocatedWorkerId, instanceId);
            } catch (Exception e) {
                // 正常关闭时不应失败，但防御性捕获防止影响关闭流程
                log.warn("[WorkerIdAllocator] 释放 workerId 时发生异常（将依赖TTL自动回收）", e);
            }
        }
    }

    // ===================== 私有方法 =====================

    /**
     * 尝试以 SetNX 方式获取心跳键（原子操作，保证分布式互斥）。
     *
     * @param workerId   候选 workerId
     * @param instanceId 实例标识
     * @return {@code true} 表示抢占成功（此 workerId 空闲可用）
     */
    private boolean tryAcquireHeartbeat(long workerId, String instanceId) {
        String key = HEARTBEAT_KEY_PREFIX + workerId;
        // trySet：仅当 key 不存在时才设置，等同于 Redis SET NX EX
        return redisson.<String>getBucket(key)
                .trySet(instanceId, HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 检查指定 workerId 的心跳键是否存活。
     *
     * @param workerId 工作节点ID
     * @return {@code true} 表示心跳键存在（节点仍活跃）
     */
    private boolean isHeartbeatAlive(long workerId) {
        return redisson.getBucket(HEARTBEAT_KEY_PREFIX + workerId).isExists();
    }

    /**
     * 启动后台心跳续约线程。
     *
     * <p>每隔 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒刷新心跳键的 TTL，
     * 防止节点正常运行时心跳键过期被其他节点抢占。</p>
     */
    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                String key = HEARTBEAT_KEY_PREFIX + allocatedWorkerId;
                redisson.getBucket(key).expire(HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("[WorkerIdAllocator] 心跳续约成功，workerId={}", allocatedWorkerId);
            } catch (Exception e) {
                log.error("[WorkerIdAllocator] 心跳续约失败，workerId={}，请检查 Redis 连接！", allocatedWorkerId, e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 构建实例唯一标识（{@code ip:serviceName:port}）。
     *
     * @param serviceName 服务名称
     * @param port        端口
     * @return 实例标识字符串
     */
    private String buildInstanceId(String serviceName, int port) {
        String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip = "unknown";
            log.warn("[WorkerIdAllocator] 无法获取本机IP，使用 'unknown' 作为替代", e);
        }
        return ip + ":" + serviceName + ":" + port;
    }

    /** @return 已分配的 workerId，未分配时返回 -1 */
    public long getAllocatedWorkerId() {
        return allocatedWorkerId;
    }

    /** @return 当前实例标识（ip:serviceName:port）*/
    public String getInstanceId() {
        return instanceId;
    }
}
```

---

## 三、SnowflakeAutoConfiguration.java

```java
package com.example.project.config;

import com.example.project.util.SnowflakeIdGenerator;
import com.example.project.util.WorkerIdAllocator;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 雪花ID生成器自动配置类。
 *
 * <h2>配置模式选择</h2>
 * 通过 {@code snowflake.mode} 属性切换两种配置模式：
 *
 * <h3>模式一：手动配置（manual，默认）</h3>
 * 适合：单机测试、CI 环境、无 Redis 场景。<br>
 * 配置示例：
 * <pre>{@code
 * # application.yml
 * snowflake:
 *   mode: manual
 *   worker-id: 1
 *   datacenter-id: 1
 * }</pre>
 *
 * <h3>模式二：Redis 自动分配（redis）</h3>
 * 适合：分布式部署、多实例水平扩展场景。<br>
 * 配置示例：
 * <pre>{@code
 * # application.yml
 * snowflake:
 *   mode: redis
 *   service-name: user-service      # 用于生成唯一实例标识
 *   server-port: 8080
 * }</pre>
 *
 * <h2>Metrics 说明</h2>
 * 当 Spring 容器中存在 {@link MeterRegistry}（即引入了 {@code spring-boot-starter-actuator}
 * + Micrometer 实现）时，自动注入并启用 Metrics 监控。
 * 可通过 {@code /actuator/metrics/snowflake.id.generated} 等端点查看指标。
 *
 * @author YourName
 * @version 1.0
 * @see SnowflakeIdGenerator
 * @see WorkerIdAllocator
 */
@Configuration
public class SnowflakeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeAutoConfiguration.class);

    // ===================== 模式一：手动配置 =====================

    /**
     * 手动配置模式下的 {@link SnowflakeIdGenerator} Bean。
     *
     * <p>条件：{@code snowflake.mode} 不存在（默认）或值为 {@code manual}，
     * 且容器中尚无同类 Bean。</p>
     *
     * @param workerId     手动指定的 workerId，默认 1
     * @param datacenterId 手动指定的 datacenterId，默认 1
     * @param registry     Metrics 注册表（可选，不存在时 Metrics 禁用）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(SnowflakeIdGenerator.class)
    @ConditionalOnProperty(name = "snowflake.mode", havingValue = "manual", matchIfMissing = true)
    public SnowflakeIdGenerator snowflakeIdGeneratorManual(
            @Value("${snowflake.worker-id:1}") long workerId,
            @Value("${snowflake.datacenter-id:1}") long datacenterId,
            // required=false：没有 Actuator 依赖时也能正常启动
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            MeterRegistry registry) {

        log.info("[Snowflake] 使用手动配置模式，workerId={}, datacenterId={}, metricsEnabled={}",
                workerId, datacenterId, registry != null);
        return new SnowflakeIdGenerator(workerId, datacenterId, registry);
    }

    // ===================== 模式二：Redis 自动分配 =====================

    /**
     * Redis 模式下的 {@link WorkerIdAllocator} Bean。
     *
     * <p>条件：{@code snowflake.mode=redis} 且容器中已有 {@link RedissonClient}。</p>
     *
     * @param redisson     Redisson 客户端
     * @param serviceName  服务名称，用于构建实例唯一标识
     * @param serverPort   服务端口
     */
    @Bean
    @ConditionalOnProperty(name = "snowflake.mode", havingValue = "redis")
    @ConditionalOnBean(RedissonClient.class)
    public WorkerIdAllocator workerIdAllocator(
            RedissonClient redisson,
            @Value("${snowflake.service-name:default-service}") String serviceName,
            @Value("${server.port:8080}") int serverPort) {

        log.info("[Snowflake] 初始化 WorkerIdAllocator，serviceName={}, port={}", serviceName, serverPort);
        WorkerIdAllocator allocator = new WorkerIdAllocator(redisson, serviceName, serverPort);
        // 立即分配（在 Bean 初始化阶段完成，确保后续 ID 生成器可用）
        allocator.allocate();
        return allocator;
    }

    /**
     * Redis 模式下的 {@link SnowflakeIdGenerator} Bean。
     *
     * <p>依赖 {@link WorkerIdAllocator} 提供的 workerId，datacenterId 固定为 1
     * （也可通过 {@code snowflake.datacenter-id} 配置多数据中心）。</p>
     *
     * @param allocator    已完成分配的 WorkerIdAllocator
     * @param datacenterId 数据中心ID，默认 1
     * @param registry     Metrics 注册表（可选）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(SnowflakeIdGenerator.class)
    @ConditionalOnProperty(name = "snowflake.mode", havingValue = "redis")
    @ConditionalOnBean(WorkerIdAllocator.class)
    public SnowflakeIdGenerator snowflakeIdGeneratorRedis(
            WorkerIdAllocator allocator,
            @Value("${snowflake.datacenter-id:1}") long datacenterId,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            MeterRegistry registry) {

        long workerId = allocator.getAllocatedWorkerId();
        log.info("[Snowflake] 使用 Redis 自动分配模式，workerId={}, datacenterId={}, metricsEnabled={}",
                workerId, datacenterId, registry != null);
        return new SnowflakeIdGenerator(workerId, datacenterId, registry);
    }
}
```

---

## 四、application.yml 配置示例

```yaml
# ===================== 手动模式（默认）=====================
# 适合：单机测试、CI 环境、无 Redis 场景
snowflake:
  mode: manual
  worker-id: 1
  datacenter-id: 1

# ===================== Redis 模式（生产推荐）=====================
# 适合：分布式部署、多实例水平扩展
# snowflake:
#   mode: redis
#   service-name: user-service
#   datacenter-id: 1
```

## 五、依赖引入（build.gradle）

```groovy
// Spring Boot 已自带，无需额外引入
// implementation 'org.springframework.boot:spring-boot-starter'

// 如果需要 Metrics 监控（Actuator + Micrometer）
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'  // 或 prometheus / influxdb / datadog 等

// Redis 分布式锁分配（已有 Redisson 则跳过）
// implementation 'org.redisson:redisson-spring-boot-starter:4.x.x'
```
