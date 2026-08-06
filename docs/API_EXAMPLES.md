# LinkForge API 示例

> 所有示例基于**实际接口**（2026-08 版本）。统一响应结构：`{"code":200,"message":"操作成功","data":...}`；除标注外均需 `Authorization: Bearer <token>`。

## 1. 认证

### 登录（获取 Token）
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Test@1234"}'
```
```json
{ "code": 200, "data": { "userId": 1, "username": "admin", "nickname": "管理员",
  "token": "eyJhbGciOiJIUzM4NCJ9...", "expiresIn": 86400, "role": "ADMIN" } }
```

## 2. 用户

### 注册（强制 USER 角色；传 role=ADMIN 会被拒绝）
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"Passw0rd!","nickname":"新用户","phone":"13800138001","email":"new@example.com"}'
# 响应：{"code":200,"data":3}
```

### 管理员创建管理员（仅 ADMIN 可指定 role=ADMIN）
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"username":"ops","password":"Ops@12345","nickname":"运营","role":"ADMIN"}'
```

### 用户分页查询 / 更新 / 状态变更 / 删除
```bash
curl "http://localhost:8080/api/users?page=1&size=10&keyword=admin&status=ACTIVE" -H "Authorization: Bearer $TOKEN"
curl -X PUT http://localhost:8080/api/users/1 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nickname":"超级管理员","phone":"13900139000"}'
curl -X PATCH http://localhost:8080/api/users/1/status -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"DISABLED"}'
curl -X DELETE http://localhost:8080/api/users/1 -H "Authorization: Bearer $TOKEN"
```

## 3. 商品 / 文件

### 商品分页
```bash
curl "http://localhost:8080/api/products?page=1&size=10" -H "Authorization: Bearer $TOKEN"
```

### 创建商品（ADMIN）
```bash
curl -X POST http://localhost:8080/api/products -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"无线鼠标","price":199.00,"stock":100,"status":"ON_SALE","imageUrl":"/uploads/xxx.jpg"}'
```

### 上传图片（ADMIN，multipart，jpg/png/webp ≤5MB）
```bash
curl -X POST http://localhost:8080/api/files/upload -H "Authorization: Bearer $TOKEN" \
  -F "file=@local.png"
# 响应：{"code":200,"data":"/uploads/<uuid>.png"}
```

## 4. 订单

### 创建订单（多商品 + 可选优惠券，金额服务端计算）
```bash
curl -X POST http://localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":1,"quantity":2}],"couponId":1,"remark":"测试订单"}'
# 响应：{"code":200,"data":1}   # orderId
```

### 支付 / 取消 / 发货(ADMIN) / 确认收货
```bash
curl -X POST http://localhost:8080/api/orders/1/pay      -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/orders/1/cancel   -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/orders/1/ship     -H "Authorization: Bearer $TOKEN"   # 仅 ADMIN
curl -X POST http://localhost:8080/api/orders/1/confirm  -H "Authorization: Bearer $TOKEN"
```

### 订单详情 / 分页
```bash
curl http://localhost:8080/api/orders/1 -H "Authorization: Bearer $TOKEN"
curl "http://localhost:8080/api/orders?status=PAID&page=1&size=10" -H "Authorization: Bearer $TOKEN"
```

## 5. 秒杀

### 抢购（活动需 ACTIVE）
```bash
curl -X POST http://localhost:8080/api/seckill/1 -H "Authorization: Bearer $TOKEN"
# 响应：{"code":200,"data":{"orderNo":"SK...","status":"PENDING"}}
```

### 秒杀单：支付 / 取消 / 退款（归属校验，只能操作自己的单）
```bash
curl -X POST http://localhost:8080/api/seckill/orders/SK.../pay    -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/seckill/orders/SK.../cancel -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/api/seckill/orders/SK.../refund -H "Authorization: Bearer $TOKEN"
```

### 我的秒杀列表 / 订单状态 / 在售活动
```bash
curl http://localhost:8080/api/seckill/orders/mine -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/seckill/order/SK... -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/seckill/activities  -H "Authorization: Bearer $TOKEN"
```

### 管理端：创建活动 / 启动 / 下架（ADMIN）
```bash
curl -X POST http://localhost:8080/api/seckill/admin/activities -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"键盘限时秒杀","seckillPrice":9.90,"productId":2,"totalStock":50,"startTime":"2026-08-06T12:00:00","endTime":"2026-08-07T12:00:00"}'
curl -X PUT http://localhost:8080/api/seckill/admin/activities/1/status -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"targetStatus":"ACTIVE"}'
```

## 6. 购物车

```bash
curl -X POST http://localhost:8080/api/cart -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
curl http://localhost:8080/api/cart -H "Authorization: Bearer $TOKEN"
curl -X PUT "http://localhost:8080/api/cart/1?quantity=3" -H "Authorization: Bearer $TOKEN"
curl -X DELETE http://localhost:8080/api/cart/1 -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/cart/count -H "Authorization: Bearer $TOKEN"
```

## 7. 优惠券

```bash
curl http://localhost:8080/api/coupons                    -H "Authorization: Bearer $TOKEN"  # 可领列表
curl -X POST http://localhost:8080/api/coupons/1/claim    -H "Authorization: Bearer $TOKEN"  # 领取
curl "http://localhost:8080/api/coupons/mine?status=UNUSED" -H "Authorization: Bearer $TOKEN" # 我的券
```

## 8. AI 客服

```bash
curl -X POST http://localhost:8080/api/support/chat -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message":"我的订单状态"}'
# 响应：{"code":200,"data":{"reply":"您最近的订单如下：\n· 订单 360681...，金额 199.0 元，状态：PAID..."}}
```
> 真实模型（DeepSeek）需在 `application.yml` 配置 `support.ai.api-key`；未配置时返回 Mock 规则答案。

## 9. 限流与错误码

```bash
# 登录限流：5 次/分钟/IP，超限返回 429
for i in $(seq 1 7); do curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'; echo; done
```

| 错误码 | 含义 |
|--------|------|
| 401 | 未认证（Token 缺失/失效） |
| 403 | 无权限（如普通用户调 ADMIN 接口） |
| 1101 / 1102 | 订单不存在 / 订单状态不允许操作 |
| 1205 | 商品不存在或已下架 |
| 1321~1328 | 秒杀：已参与/已抢光/未开始/已结束/活动不存在/状态不合法/订单不存在/状态不允许 |
| 1301~1306 | 优惠券：不存在/已使用/已过期/不满足条件/已领取/已领完 |
| 1311~1312 | 积分：不足/发放失败 |
| 4290101 | 触发限流 |

> 完整接口定义与字段说明请以 Swagger（http://localhost:8080/swagger-ui.html）为准。
