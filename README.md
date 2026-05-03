# LinkForge

> 一个基于 **Spring Boot 4.0.5 + Java 21** 的全栈后端参考项目，涵盖用户管理、订单管理两大核心业务域，整合了 MyBatis、Redis 缓存、Spring Security + JWT 认证、Kafka 消息队列、Redisson 分布式锁、Guava 限流等企业级技术栈。

## 技术栈总览

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 基础框架 | Spring Boot | 4.0.5 | 应用核心框架 |
| 运行时 | Java (JDK) | 21 | 语言与平台 |
| ORM / 数据访问 | **MyBatis** | 4.0.0 | 主力数据访问层（注解 + XML） |
| ORM 辅助 | Spring Data JPA | 4.0.x | Entity 实体映射（非 CRUD） |
| 数据库 | MySQL | 8.0+ | 关系型数据存储 |
| 缓存 | Spring Data Redis + Lettuce | - | 多级缓存、分布式会话 |
| 分布式锁 | Redisson | 4.3.0 | 基于 Redis 的分布式锁 |
| 安全认证 | Spring Security + jjwt | Security 6.x / jjwt 0.12.5 | 无状态 JWT 认证 |
| 接口限流 | Google Guava | 33.4.0-jre | 多维度接口限流 |
| 消息队列 | Apache Kafka | 4.1.2 | 订单事件异步通知 |
| 接口文档 | SpringDoc OpenAPI (Swagger UI) | 2.8.6 | REST API 在线文档 |
| 参数校验 | Jakarta Validation | 3.x | 请求参数校验 |
| 构建工具 | Gradle | 9.4.1 | 项目构建与依赖管理 |
| 工具库 | Lombok | - | 减少样板代码 |

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client (前端 / 第三方)                     │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP/HTTPS (REST API)
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Controller 层 (REST)                        │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │AuthController│  │UserController│  │    OrderController     │   │
│  │ /api/auth/*  │  │ /api/users/* │  │    /api/orders/*       │   │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬────────────┘   │
│         │                 │                      │                │
│  ┌──────▼─────────────────▼──────────────────────▼────────────┐   │
│  │         统一拦截器链                                        │   │
│  │  TraceFilter → RateLimitInterceptor → JwtAuthenticationFilter│   │
│  └────────────────────────┬───────────────────────────────────┘   │
└───────────────────────────┼───────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│                       Service 层 (业务逻辑)                       │
│  ┌──────────────────────┐  ┌────────────────────────────────┐   │
│  │    UserServiceImpl    │  │       OrderServiceImpl          │   │
│  │ · 登录认证 & JWT签发  │  │ · 创建订单(分布式锁+雪花ID)      │   │
│  │ · 用户CRUD            │  │ · 支付/取消(发送Kafka消息)      │   │
│  │ · 发布UserCreatedEvent│  │                                │   │
│  │ · @Cacheable/@CacheEvict│                               │   │
│  └──────────┬───────────┘  └───────────┬────────────────────┘   │
│             │                          │                         │
│             │           ┌──────────────▼──────────────────┐     │
│             │           │    Event / MQ 异步通知层        │     │
│             │           │  ┌────────────┐  ┌───────────┐  │     │
│             │           │  │KafkaProducer│  │SpringEvent│  │     │
│             │           │  │(OrderPaid/ │  │(UserCreated│  │     │
│             │           │  │Cancelled)  │  │Event)      │  │     │
│             │           │  └─────┬──────┘  └─────┬─────┘  │     │
│             │           │        ▼               ▼        │     │
│             │           │  ┌──────────┐  ┌────────────┐  │     │
│             │           │  │KafkaConsumer│ │EventListener│  │     │
│             │           │  │(TODO:下游集成)│ │(模拟邮件等) │  │     │
│             │           │  └──────────┘  └────────────┘  │     │
│             │           └────────────────────────────────┘     │
│             └──────────────┬───────────────────────────────────┘
│                            │
│  ┌─────────────────────────▼─────────────────────────────────┐  │
│  │                    数据访问层 (Mapper)                       │  │
│  │  ┌──────────────┐  ┌───────────────────────────────────┐   │  │
│  │  │  UserMapper  │  │          OrderMapper              │   │  │
│  │  │ 注解: CRUD   │  │  注解: CRUD                       │   │  │
│  │  │ XML: 条件查询 │  │  XML: 条件查询                    │   │  │
│  │  │     游标分页  │  │     游标分页                      │   │  │
│  │  └──────┬───────┘  └──────────────┬────────────────────┘   │  │
│  └─────────┼─────────────────────────┼────────────────────────┘  │
│            │                         │                           │
│  ┌─────────▼─────────────────────────▼─────────────────────────┐ │
│  │                    基础设施层                                 │ │
│  │  ┌──────────┐ ┌──────┐ ┌────────┐ ┌───────┐ ┌───────────┐  │ │
│  │  │  MySQL   │ │Redis │ │Redisson│ │ Kafka │ │  SlowSql  │  │ │
│  │  │ (HikariCP)│ │Cache│ │Lock   │ │MQ     │ │Interceptor│  │ │
│  │  └──────────┘ └──────┘ └────────┘ └───────┘ └───────────┘  │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## 核心模块说明

### 1. 用户模块 (User Module)

负责用户的全生命周期管理：注册、登录、信息维护、状态变更、逻辑删除。

| 功能 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 用户注册 | `POST /api/users` | `createUser()` | 校验重复用户名 → BCrypt 加密密码 → 插入 DB → 发布 UserCreatedEvent |
| 用户登录 | `POST /api/auth/login` | `login()` | 校验用户名密码 → 验证状态 → 生成 JWT Token |
| 查询用户 | `GET /api/users/{id}` | `getUserById()` | 带 `@Cacheable("user")` 一级缓存（TTL=1h） |
| 分页查询 | `GET /api/users` | `queryUsers()` | 支持关键词搜索 + 状态过滤 + offset 分页 |
| 更新信息 | `PUT /api/users/{id}` | `updateUserInfo()` | 更新昵称/手机/邮箱 + 清除缓存 |
| 变更状态 | `PATCH /api/users/{id}/status` | `updateUserStatus()` | ACTIVE ↔ DISABLED + 清除缓存 |
| 删除用户 | `DELETE /api/users/{id}` | `deleteUser()` | 逻辑删除（status→DELETED）+ 清除缓存 |

### 2. 订单模块 (Order Module)

订单的完整生命周期：创建 → 待支付 → 已支付 → 已发货 → 已完成 / 已取消。

| 功能 | 接口 | 方法 | 核心机制 |
|------|------|------|----------|
| 创建订单 | `POST /api/orders` | `createOrder()` | **Redisson 分布式锁防并发** + **雪花 ID 生成订单号** |
| 查询订单 | `GET /api/orders/{id}` | `getOrderById()` | 单条查询 |
| 分页查询 | `GET /api/orders` | `queryOrders()` | 按 userId + status 过滤 |
| 支付订单 | `POST /api/orders/{id}/pay` | `payOrder()` | PENDING → PAID + **发送 Kafka OrderPaidMessage** |
| 取消订单 | `POST /api/orders/{id}/cancel` | `cancelOrder()` | 非 COMPLETED/SHIPPED 可取消 + **发送 Kafka OrderCancelledMessage** |

### 3. 安全认证模块 (Security)

采用无状态的 JWT Bearer Token 认证方案：

- **登录流程**：用户提交凭据 → Service 校验 → BCrypt 验证密码 → JwtUtil 签发 Token（有效期 24h）→ 返回给客户端
- **请求鉴权**：`JwtAuthenticationFilter` 从 Header 提取 `Authorization: Bearer <token>` → 解析验证 → 设置 SecurityContext
- **URL 权限规则**：

| 路径 | 权限 |
|------|------|
| `/api/auth/**`, `/api/users`(POST), `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**` | 允许匿名访问 |
| 其他所有 `/api/**` 路径 | 需要 JWT 认证 |

### 4. 缓存策略模块 (Caching)

基于 Spring Cache 抽象的多级 TTL 缓存配置：

| 缓存名称 | TTL | 用途 |
|----------|-----|------|
| `user` | 1 小时 | 用户详情缓存 |
| `order` | 15 分钟 | 订单详情缓存 |
| 默认 | 30 分钟 | 其他缓存 |

使用 Jackson 3 进行 JSON 序列化/反序列化存储到 Redis。

### 5. 接口限流模块 (Rate Limiting)

基于 Guava 的多维度限流拦截器（`RateLimitInterceptor`），对 `/api/**` 生效：

| 维度 | 限制 | 策略 |
|------|------|------|
| 登录接口 | 5 次/分钟/IP | `RateLimiter` |
| 注册接口 | 10 次/小时/IP | `LoadingCache` 计数器 |
| 全局接口 | 100 QPS/IP | `RateLimiter` |

超限时返回 HTTP 429 + 标准错误 JSON。

### 6. 消息队列模块 (Kafka)

用于订单事件的异步解耦通知：

```
OrderServiceImpl (生产者)
       │
       ├── payOrder()  ──► Kafka Topic: order-paid     ──► OrderKafkaConsumer.handleOrderPaid()
       │                                                                  ├─ [TODO] 调用仓库服务备货
       │                                                                  ├─ [TODO] 发送支付成功短信
       │                                                                  └─ [TODO] 更新会员积分
       │
       └── cancelOrder() ──► Kafka Topic: order-cancelled ──► OrderKafkaConsumer.handleOrderCancelled()
                                                                          ├─ [TODO] 回退库存
                                                                          └─ [TODO] 解冻优惠券
```

**Kafka 配置要点**：
- Producer：`acks=all`（最高可靠性）、幂等性开启、3 次重试
- Consumer：手动提交 offset（`ack-mode: manual_immediate`）、3 个并发消费者线程
- 错误处理：FixedBackOff（1s × 3 次）+ DeadLetter Recoverer

### 7. 链路追踪模块 (Tracing)

`TraceFilter` 实现全链路 TraceId 透传：
- 请求进入时：从 `X-Trace-Id` Header 读取或自动生成 UUID → 写入 SLF4J MDC → 写入响应 Header
- 请求结束：finally 中清理 MDC
- 日志输出格式包含 `[traceId] [uid:userId]` 便于排查

### 8. SQL 监控模块

`SlowSqlInterceptor`（MyBatis 插件）：拦截 Executor 的 query/update 操作，执行时间 > 500ms 时输出 WARN 日志。

## 数据流图

### 用户注册流程

```
Client                    UserController           UserServiceImpl          UserMapper         Redis         Spring EventBus
  │                            │                        │                      │               │                │
  │ POST /api/users            │                        │                      │               │                │
  │ {username,password,...}    │                        │                      │               │                │
  │───────────────────────────▶│                        │                      │               │                │
  │                            │ createUser(req)         │                      │               │                │
  │                            │───────────────────────▶│                      │               │                │
  │                            │                        │ existsByUsername()   │               │                │
  │                            │                        │─────────────────────▶│               │                │
  │                            │                        │◀─────────────────────│               │                │
  │                            │                        │ encodePassword()      │               │                │
  │                            │                        │ insert(user)         │               │                │
  │                            │                        │─────────────────────▶│               │                │
  │                            │                        │◀─────────────────────│               │                │
  │                            │                        │ publish(event)        │               │                │
  │                            │                        │───────────────────────────────────────────────────────▶│
  │                            │                        │                      │               │                │ async
  │                            │                        │                      │               │         UserEventListener
  │                            │                        │                      │               │         (发送欢迎邮件/初始化默认值)
  │◀───────────────────────────│◀───────────────────────│                      │               │                │
  │ {code:200, data: userId}   │                        │                      │               │                │
```

### 订单支付流程

```
Client                   OrderController          OrderServiceImpl         DistributedLockUtil    OrderMapper    KafkaProducer    Kafka Broker
  │                             │                        │                       │                  │                │               │
  │ POST /api/orders/{id}/pay   │                        │                       │                  │                │               │
  │─────────────────────────────▶│                        │                       │                  │                │               │
  │                             │ payOrder(id)            │                       │                  │                │               │
  │                             │───────────────────────▶│                       │                  │                │               │
  │                             │                        │ tryLock("order:{id}") │                  │                │               │
  │                             │                        │──────────────────────▶│                  │                │               │
  │                             │                        │◀──────────────────────│                  │                │               │
  │                             │                        │ updateStatus(PAID)     │                  │                │               │
  │                             │                        │──────────────────────────────────────────▶│               │
  │                             │                        │◀──────────────────────────────────────────│               │
  │                             │                        │ sendOrderPaid(msg)     │                  │                │               │
  │                             │                        │──────────────────────────────────────────────────────────────▶│
  │                             │                        │                       │                  │                │  topic:order-paid
  │                             │                        │ unlock()               │                  │                │               │
  │                             │                        │──────────────────────▶│                  │                │               │
  │◀────────────────────────────│◀───────────────────────│                       │                  │                │               │
  │ {code:200}                  │                        │                       │                  │                │               │
```

## 项目结构

```
src/main/java/com/example/project/
├── ProjectApplication.java          # 启动类 (@EnableCaching + @EnableAsync)
│
├── common/                          # 公共类
│   ├── Result.java                  # 统一响应包装器
│   ├── PageResult.java              # 分页响应包装器
│   ├── BusinessException.java       # 业务异常
│   ├── ErrorCode.java               # 错误码枚举 (~20个)
│   └── GlobalExceptionHandler.java  # 全局异常处理器
│
├── config/                          # 配置类
│   ├── SecurityConfig.java          # Spring Security 安全配置
│   ├── RedisConfig.java             # Redis 缓存管理器 + Template 配置
│   ├── MyBatisConfig.java           # MyBatis Mapper 扫描
│   ├── WebMvcConfig.java            # MVC 配置（注册限流拦截器）
│   ├── OpenApiConfig.java           # Swagger/OpenAPI 文档配置
│   ├── Jackson3JsonRedisSerializer.java  # Redis JSON 序列化器
│   └── SlowSqlInterceptor.java      # MyBatis 慢SQL拦截器 (>500ms告警)
│
├── controller/                      # 控制器层
│   ├── AuthController.java          # 认证接口 (/api/auth)
│   ├── UserController.java          # 用户接口 (/api/users)
│   └── OrderController.java         # 订单接口 (/api/orders)
│
├── service/                         # 服务层
│   ├── UserService.java             # 用户服务接口
│   ├── OrderService.java            # 订单服务接口
│   └── impl/
│       ├── UserServiceImpl.java     # 用户服务实现 (含缓存+事务+事件发布)
│       └── OrderServiceImpl.java    # 订单服务实现 (含分布式锁+Kafka消息)
│
├── mapper/                          # MyBatis Mapper 接口
│   ├── UserMapper.java              # 用户 Mapper (注解 + XML 混合)
│   └── OrderMapper.java             # 订单 Mapper (注解 + XML 混合)
│
├── entity/                          # JPA 实体类
│   ├── User.java                    # 用户实体 (@Entity)
│   └── Order.java                   # 订单实体 (@Entity)
│
├── dto/                             # 数据传输对象
│   ├── request/                     # 请求 DTO（含 Validation 注解）
│   │   ├── LoginRequest.java
│   │   ├── UserCreateRequest.java / UserUpdateRequest.java / UserQueryRequest.java
│   │   ├── CursorPageRequest.java
│   │   └── OrderCreateRequest.java / OrderQueryRequest.java
│   └── response/                    # 响应 DTO
│       ├── LoginResponse.java
│       ├── UserResponse.java
│       └── OrderResponse.java
│
├── mq/                              # Kafka 消息队列模块
│   ├── MqConstants.java             # Topic / Group 常量
│   ├── config/
│   │   └── KafkaConsumerConfig.java # Consumer 工厂配置 (错误处理+手动ACK)
│   ├── producer/
│   │   └── OrderKafkaProducer.java  # 订单事件消息生产者
│   ├── consumer/
│   │   └── OrderKafkaConsumer.java  # 订单事件消息消费者 (TODO: 下游集成)
│   └── dto/
│       ├── OrderPaidMessage.java
│       └── OrderCancelledMessage.java
│
├── security/                        # 安全模块
│   └── JwtAuthenticationFilter.java # JWT 认证过滤器
│
├── event/                           # Spring 事件
│   ├── UserCreatedEvent.java
│   ├── OrderPaidEvent.java           # ⚠️ 已被 Kafka 替代
│   └── OrderCancelledEvent.java      # ⚠️ 已被 Kafka 替代
│
├── listener/                        # Spring 事件监听器
│   ├── UserEventListener.java       # ✅ 仍活跃 (异步处理用户创建事件)
│   └── OrderEventListener.java      # ⚠️ 死代码 (事件已不再发布)
│
├── filter/                          # Servlet 过滤器
│   └── TraceFilter.java             # 链路追踪 Filter
│
├── interceptor/                     # MVC 拦截器
│   └── RateLimitInterceptor.java    # Guava 多维度限流
│
├── enums/                           # 枚举
│   ├── UserStatus.java              # ACTIVE / DISABLED / DELETED
│   └── OrderStatus.java             # PENDING / PAID / SHIPPED / COMPLETED / CANCELLED
│
└── util/                            # 工具类
    ├── JwtUtil.java                 # JWT 工具 (生成/解析/校验)
    ├── SnowflakeIdGenerator.java    # 雪花 ID 生成器
    └── DistributedLockUtil.java     # Redisson 分布式锁封装

src/main/resources/
├── application.yml                  # 主配置文件
├── application-dev.yml              # 开发环境 (DDL auto-update, DEBUG日志)
├── application-prod.yml             # 生产环境 (外部化敏感配置)
├── db/schema.sql                    # 数据库建表脚本 + 种子数据
├── logback-spring.xml               # 日志配置 (控制台+滚动文件, 30天保留)
└── mapper/
    ├── UserMapper.xml               # MyBatis XML 映射 (条件查询/游标分页)
    └── OrderMapper.xml              # MyBatis XML 映射 (条件查询/游标分页)
```

## 安装步骤

### 前置要求

| 环境 | 要求 |
|------|------|
| JDK | 21 或更高版本 |
| Gradle | 8.x+（或使用项目内置 Gradle Wrapper） |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Apache Kafka | 4.x（如需订单支付/取消功能） |

### 1. 克隆项目

```bash
git clone <repository-url>
cd LinkForge
```

### 2. 初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 执行初始化脚本（自动建库、建表、插入测试数据）
source src/main/resources/db/schema.sql
```

或直接在 MySQL 客户端中执行 `src/main/resources/db/schema.sql` 文件内容。

**默认测试账号**：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `Test@1234` | 管理员 |
| `testuser` | `Test@1234` | 测试用户 |

> ⚠️ 密码为 BCrypt 加密后的哈希值对应的原文 `Test@1234`，实际部署请修改默认密码。

### 3. 启动 Redis

```bash
# Windows (默认端口 6379)
redis-server

# 如需设置密码，在 redis.conf 中设置:
# requirepass 123456
```

本项目 Redis 默认配置为 `localhost:6379`，密码 `123456`。

### 4. 启动 Kafka（可选）

如果需要订单支付/取消的 Kafka 异步通知功能：

```bash
# Windows
# ① 进入脚本目录
cd F:\kafka\kafka_2.13-4.1.2\bin\windows

# ② 生成集群 ID（复制输出的 UUID）
.\kafka-storage.bat random-uuid

# ③ 格式化存储（替换为上一步的 UUID）
.\kafka-storage.bat format -t <UUID> -c ..\..\config\server.properties --standalone

# ④ 启动 Kafka
.\kafka-server-start.bat ..\..\config\server.properties

# 创建所需 Topic (可选，Kafka 可自动创建)
.\bin\windows\kafka-topics.bat --create --topic order-paid --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
.\bin\windows\kafka-topics.bat --create --topic order-cancelled --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

### 5. 修改配置（按需）

编辑 `src/main/resources/application.yml`，确认以下连接信息与你本地环境一致：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo_db?...
    username: root
    password: 你的MySQL密码        # ← 修改此处
  data:
    redis:
      password: 你的Redis密码       # ← 修改此处
  kafka:
    bootstrap-servers: localhost:9092  # ← 确认 Kafka 地址
jwt:
  secret: "你的JWT密钥(至少32字符)"    # ← 生产环境必须更换
```

**生产环境部署**：切换 profile 为 `prod`，通过环境变量注入敏感配置：

```bash
export DB_URL=jdbc:mysql://prod-host:3306/demo_db
export DB_USERNAME=app_user
export DB_PASSWORD=***
export REDIS_HOST=redis-cluster
export REDIS_PORT=6379
export REDIS_PASSWORD=***
export JWT_SECRET=your-production-secret-key-at-least-32-chars
java -jar LinkForge.jar --spring.profiles.active=prod
```

### 6. 构建并启动

```bash
# 使用 Gradle Wrapper 构建（推荐，无需单独安装 Gradle）
./gradlew build -x test

# 启动应用
./gradlew bootRun

# 或者打包后运行 JAR
./gradlew build
java -jar build/libs/LinkForge-0.0.1-SNAPSHOT.jar
```

启动成功后，控制台应看到类似以下输出：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.0.5)

... Started ProjectApplication in x.xxx seconds
```

### 7. 验证服务

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 访问 Swagger UI 接口文档
# 浏览器打开: http://localhost:8080/swagger-ui.html
```

## 使用示例

### 示例 1：用户注册

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "Passw0rd!",
    "nickname": "新用户",
    "phone": "13800138001",
    "email": "newuser@example.com"
  }'
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": 3
}
```

### 示例 2：用户登录（获取 JWT Token）

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Test@1234"
  }'
```

**响应**（后续所有需认证接口都需要携带返回的 token）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "管理员",
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 86400000
  }
}
```

### 示例 3：携带 Token 访问受保护接口

```bash
# 设置 Token 变量（替换为上一步获取的实际 token）
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

# 查询当前用户信息
curl http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $TOKEN"
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "phone": "13800138000",
    "email": "admin@example.com",
    "status": "ACTIVE",
    "createdAt": "2026-05-02T12:00:00",
    "updatedAt": "2026-05-02T12:00:00"
  }
}
```

### 示例 4：分页查询用户列表

```bash
curl "http://localhost:8080/api/users?page=1&size=10&keyword=admin&status=ACTIVE" \
  -H "Authorization: Bearer $TOKEN"
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [...],
    "total": 1,
    "page": 1,
    "size": 10,
    "totalPages": 1
  }
}
```

### 示例 5：创建订单

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "userId": 1,
    "totalAmount": 99.99,
    "remark": "测试订单"
  }'
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "orderNo": "20260502000100001",   # 雪花算法生成的订单号
    "userId": 1,
    "totalAmount": 99.99,
    "status": "PENDING",
    "remark": "测试订单"
  }
}
```

### 示例 6：支付订单（触发 Kafka 消息）

```bash
curl -X POST http://localhost:8080/api/orders/1/pay \
  -H "Authorization: Bearer $TOKEN"
```

此操作会将订单状态从 `PENDING` 更新为 `PAID`，并向 Kafka `order-paid` topic 发送一条消息供消费者处理。

### 示例 7：更新用户信息

```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nickname": "超级管理员",
    "phone": "13900139000",
    "email": "superadmin@example.com"
  }'
```

### 示例 8：限流触发演示

短时间内频繁调用登录接口（超过 5 次/分钟）将触发限流：

```bash
for i in $(seq 1 7); do
  curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong"}'
done
```

第 6 次及之后将收到：
```json
{
  "code": 4290101,
  "message": "操作过于频繁，请稍后再试",
  "data": null
}
```

## Swagger 接口文档

启动项目后访问 **http://localhost:8080/swagger-ui.html** 即可查看完整的交互式 API 文档，支持在线调试每个接口。

## 测试

```bash
# 运行全部测试
./gradlew test

# 仅运行指定测试类
./gradlew test --tests "com.example.project.service.UserServiceTest"

# 查看测试报告
# 浏览器打开: build/reports/tests/test/index.html
```

**现有测试覆盖**：

| 测试类 | 覆盖范围 | 用例数 |
|--------|----------|--------|
| `UserServiceTest` | 用户服务的单元测试 | 6 |
| `UserControllerTest` | 用户控制器 MockMvc 测试 | 3 |
| `DistributedLockUtilTest` | 分布式锁工具测试 | 4 |

## License

MIT
