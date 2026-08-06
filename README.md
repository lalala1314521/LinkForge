# LinkForge 后端

> 一个基于 **Spring Boot 4.0.5 + Java 21** 的电商后端参考项目，覆盖用户、商品、订单、秒杀、购物车、优惠券、积分、AI 客服、文件上传等完整业务域，整合 **Kafka 可靠消息最终一致性**、**Redis Lua 原子秒杀**、**Redisson 分布式锁**、**Spring Security + JWT**、**消费幂等**等企业级核心机制。

## 功能总览

| 模块 | 能力 | 亮点机制 |
|------|------|----------|
| 用户/认证 | 注册、登录、JWT、角色（USER/ADMIN） | 注册强制 USER（防提权红线：仅 ADMIN 可建 ADMIN）；BCrypt 加密；24h Token |
| 商品 | CRUD、图片上传、上下架、分页 | 本地 `uploads/` 存储 + 静态映射；类型白名单（jpg/png/webp ≤5MB） |
| 订单 | 下单（多商品+优惠券）、支付、取消、**发货/确认收货** | Redisson 防重复下单；服务端计价（不信任前端）；条件更新防并发 |
| 可靠消息 | 支付/取消事件 → Kafka 异步处理 | **本地消息表 PENDING→SENT + 定时补偿 + 消费幂等（唯一键）**，最终一致 |
| 秒杀 | 活动管理、抢购、**支付/取消/退款**、超时关单 | **Redis Lua 原子扣减+限购+布隆过滤**；Kafka 削峰建单；DB 条件扣减双保险 |
| 购物车 | 加购/改量/删除/列表/角标 | 落库（cart_items，UNIQUE(user,product)），结算复用下单 |
| 优惠券 | 领取、我的券、下单抵扣 | 领券限一人一张；FROZEN→USED 状态机 |
| 积分 | 支付发放、取消回退、余额查询 | 积分流水唯一键幂等（uk_order_type） |
| AI 客服 | 对话（订单/积分/发货查询） | **ChatProvider 抽象**：DeepSeek（OpenAI 兼容）+ RAG（实时注入用户订单上下文）；Mock 兜底 |
| 通知 | 支付/发货/取消通知 | **Provider 抽象**：Email/Sms 占位 + 配置开关，接运营商零改动 |
| 安全 | JWT 鉴权、URL 权限、IP 限流 | 登录 5次/分、注册 10次/时、全局 100QPS（Redis 计数） |
| 运维 | Swagger 文档、TraceId 链路、慢 SQL 监控 | `/swagger-ui.html`；`[traceId][uid]` 日志；>500ms WARN |

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 4.0.5 |
| 运行时 | Java | 21 |
| 数据访问 | MyBatis + Spring Data JPA | 4.0.0 / 4.0.x |
| 数据库 | MySQL | 8.0+ |
| 缓存/锁 | Spring Data Redis + Redisson | 4.3.0 |
| 安全 | Spring Security + jjwt | 6.x / 0.12.5 |
| 消息队列 | Apache Kafka（可靠消息+幂等消费） | 4.1.2 |
| 秒杀 | Redis Lua + BloomFilter | - |
| JSON | Jackson 3（tools.jackson） | - |
| API 文档 | SpringDoc OpenAPI | 2.8.6 |
| 构建 | Gradle | 9.4.1 |
| 部署 | Docker Compose（MySQL/Redis/Kafka/后端/前端） | - |

## 核心机制

### 1. 订单可靠消息（本地消息表 + 最终一致）
```
创建/支付订单 ──► DB 事务（订单状态 + 本地消息表 PENDING 同事务）
                    │
                    ▼
             消息表定时补偿扫描 ──► 发送 Kafka ──► 标记 SENT
                    │                              │
             失败重试（幂等）                    消费者处理（库存/积分/券/通知）
```
- 生产者：消息与业务**同事务**落库，定时任务补偿未发送消息（不存在"业务成功消息丢失"）
- 消费者：`order_process_record` 唯一键 + 条件更新幂等，重复消息直接跳过
- 消费端：库存回退、积分发放（唯一键幂等）、优惠券 FROZEN→USED、通知

### 2. 秒杀链路（Lua 原子 + Kafka 削峰）
```
抢购请求 ──► 时间窗校验 ──► 布隆过滤(防刷) ──► Lua 原子（库存预扣 + 用户去重 SADD）
              │
              ▼
       雪花单号 + Kafka 直发（热路径不碰 DB）
              │
              ▼
      消费者落库 PENDING ──► 支付(发积分) / 取消(回库存+释放名额) / 退款 / 超时关单
```
- Redis 库存与 DB `available_stock` 双保险（条件更新防超卖）
- 取消/退款：回加 Redis 库存 + DB 条件回加 + SREM 释放限购名额 + 积分回退

### 3. 安全红线
- 注册**强制 USER**，仅 ADMIN 可通过管理端创建 ADMIN（Service 层单一校验点）
- 订单/购物车/秒杀单均做**归属校验**（userId 从 SecurityContext 取，不信任前端）
- 服务端计价：金额以数据库快照为准，请求只传商品+数量
- 秒杀单查询/支付/取消/退款全部校验归属，修复过越权漏洞

### 4. 状态机
- 订单：PENDING → PAID → SHIPPED → COMPLETED；PENDING → CANCELLED（超时自动）
- 秒杀单：PENDING → PAID → REFUNDED；PENDING → CANCELLED（超时关单 15min）

## 快速开始

### 方式一：本地开发（dev profile）

前置：JDK 21、MySQL 8、Redis、Kafka

```bash
# 1. 初始化数据库（建库建表+种子，admin/Test@1234）
mysql -u root -p < src/main/resources/db/schema.sql

# 2. 启动（默认 application-dev.yml，连 localhost）
./gradlew bootRun
```

### 方式二：Docker 一键部署（推荐演示）

```bash
docker compose up -d --build
# 前端 http://localhost:3000（admin/Test@1234） | 后端 http://localhost:8080
```

详见 `deploy/docker/README.md`（含端口、重置、常见问题）。

### 生产环境（prod profile）

所有敏感配置环境变量化，见 `application-prod.yml`：
`DB_URL / DB_USERNAME / DB_PASSWORD / REDIS_HOST / KAFKA_BOOTSTRAP_SERVERS / JWT_SECRET / FILE_UPLOAD_DIR / SUPPORT_AI_API_KEY`

## 主要 API

| 模块 | 接口 |
|------|------|
| 认证 | `POST /api/auth/login` |
| 用户 | `POST /api/users`（注册）、`GET/PUT/DELETE /api/users/**` |
| 商品 | `GET /api/products`、`POST/PUT/DELETE /api/products/**`（ADMIN） |
| 文件 | `POST /api/files/upload`（ADMIN） |
| 订单 | `POST /api/orders`、`POST /{id}/pay`、`/cancel`、`/ship`(ADMIN)、`/confirm` |
| 秒杀 | `POST /api/seckill/{activityId}`、`/orders/{orderNo}/pay`、`/cancel`、`/refund`、`/api/seckill/admin/**`(ADMIN) |
| 购物车 | `GET/POST /api/cart`、`PUT/DELETE /api/cart/{id}`、`GET /api/cart/count` |
| 优惠券 | `GET /api/coupons/**`、`POST /api/coupons/{id}/claim` |
| 积分 | `GET /api/points/balance` |
| 客服 | `POST /api/support/chat`（AI+RAG） |

完整接口见 Swagger：http://localhost:8080/swagger-ui.html

## 项目结构（核心）

```
src/main/java/com/example/project/
├── controller/        # REST 接口（auth/user/product/order/seckill/cart/coupon/points/file/support）
├── service/           # 业务层（含 SeckillTimeoutCancelTask 超时关单、OrderTimeoutCancelTask）
├── mapper/            # MyBatis（注解+XML；秒杀/购物车条件更新）
├── entity/            # JPA 实体（orders/seckill_orders/cart_items/...）
├── mq/                # Kafka（producer/consumer/dto，可靠消息）
├── notification/      # 通知 Provider 抽象（Email/Sms 占位）
├── support/           # AI 客服 Provider（Mock + OpenAiChatProvider(RAG)）
├── security/          # JWT 过滤器 + SecurityUtil
├── interceptor/       # 限流（RateLimitInterceptor）
├── common/            # Result/PageResult/ErrorCode/GlobalExceptionHandler
└── config/            # Security/Redis/WebMvc/MyBatis/OpenApi/BloomFilter
```

## 测试

```bash
./gradlew test          # 130+ 用例（Service/Controller/安全/消费幂等/边界）
```

覆盖：用户/订单服务、秒杀并发与限购、购物车、优惠券、积分、可靠消息消费幂等、QaEdgeCase、安全红线（越权/提权拦截）。

## 部署文件

```
Dockerfile                 # 多阶段构建（gradle:9.4.1-jdk21 → temurin:21-jre）
docker-compose.yml         # MySQL8 + Redis7 + Kafka(KRaft) + 后端 + 前端
deploy/docker/init.sql     # MySQL 初始化（建表+种子，SET NAMES utf8mb4 防乱码）
deploy/docker/README.md    # 部署指南
```

## License

MIT
