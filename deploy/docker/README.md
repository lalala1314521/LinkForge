# LinkForge Docker 部署指南

## 一、前置要求

- 已安装 **Docker Desktop**（Windows/macOS）或 Docker Engine（Linux）
- 本机 3000/8080/3306/6379/9092 端口空闲（**若你本机已跑 MySQL/Redis/Kafka，请先停掉或改 compose 端口映射**，见"常见问题"）

## 二、一键启动

在项目根目录（`reference-self`）执行：

```bash
docker compose up -d --build
```

首次启动会构建后端（Gradle 打包）与前端（Vite 构建），约 5-10 分钟；之后启动秒级。

## 三、访问

| 服务 | 地址 | 账号 |
|------|------|------|
| 前端（商城+管理后台） | http://localhost:3000 | `admin` / `Test@1234` |
| 后端 API | http://localhost:8080 | — |
| Swagger 文档 | http://localhost:8080/swagger-ui.html | — |
| MySQL | localhost:**3307**（root/123456） | — |
| Redis | localhost:**6380** | — |

> 注：MySQL/Redis 宿主端口已避开本机开发环境（3306/6379 被本地占用）；容器内部仍走 3306/6379，不影响后端连接。

## 四、做了什么（部署细节）

| 组件 | 方案 |
|------|------|
| MySQL 8 | 首次启动自动执行 `deploy/docker/init.sql`：建库建表 + 种子数据（admin/商品×3/优惠券×2/秒杀活动） |
| Redis 7 | 无密码（与本地一致） |
| Kafka 4 | bitnami 镜像 KRaft 单节点（无 Zookeeper），自动建 topic |
| 后端 | `SPRING_PROFILES_ACTIVE=prod`，连接全走容器服务名；`ddl-auto=update` 自动补新表（cart_items 等）；上传目录挂载 volume `uploads-data` |
| 前端 | Vite 构建产物由 Nginx 托管，`/api`、`/uploads` 反向代理到后端，SPA 路由回退 |

## 五、常用命令

```bash
docker compose ps                # 查看状态
docker compose logs -f backend   # 看后端日志
docker compose logs -f kafka     # 看 Kafka 日志
docker compose down              # 停止（保留数据卷）
docker compose down -v           # 停止并清空数据（重新初始化种子）
docker compose up -d --build     # 改代码后重新构建启动
```

## 六、可选配置

### 1. AI 客服接入 DeepSeek
编辑 `docker-compose.yml` 的 `backend.environment`，取消注释并填 Key：

```yaml
SUPPORT_AI_API_KEY: "sk-你的key"
```

然后 `docker compose up -d backend` 重建后端即可。

### 2. 邮件/短信通知
`application.yml` 的 `notification.email.enabled` 置 `true` 并在 `EmailNotificationProvider` 实现发送逻辑（预留接口，见代码注释）。

## 七、常见问题

- **端口冲突**：本机已跑 MySQL/Redis/Kafka → 注释掉 `docker-compose.yml` 中对应 `ports` 映射（容器间网络不走宿主端口，不影响功能）
- **首次启动后想重置数据**：`docker compose down -v && docker compose up -d --build`（重新执行 init.sql）
- **Kafka 消息延迟**：秒杀单/订单消息为异步消费，观察后端日志 `[秒杀消费]` / `[消费]` 关键字
- **登录 429 限流**：注册/登录有 IP 限流（Redis 计数），等待窗口或重启 Redis 清空
- **Windows 换行**：若 build 报 `exec format` 错误，检查 Dockerfile 换行是否为 LF（Git 检出设置）

## 八、目录结构（部署相关）

```
reference-self/
├── Dockerfile                  # 后端多阶段构建
├── docker-compose.yml          # 全栈编排（入口）
└── deploy/docker/
    ├── init.sql                # MySQL 初始化（建表+种子，admin 哈希已核验）
    └── README.md               # 本文档

referenceFrontEndUp/
├── Dockerfile                  # 前端多阶段构建（node 构建 → nginx）
└── nginx.conf                  # Nginx 静态托管 + API/上传代理
```
