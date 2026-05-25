# Pingo

本地生活 **探店社交平台** 后端服务：用户、商户、笔记、关注、优惠券与秒杀下单等业务，配套 Redis 缓存与分布式能力。

## 技术栈

| 类别 | 选用 |
|------|------|
| 框架 | Spring Boot 2.7、MyBatis-Plus、Spring Cloud Alibaba |
| 数据 | Microsoft SQL Server |
| 缓存 / 中间件 | Redis（String、Hash、Geo、Stream）、Redisson |
| 流量治理 | Sentinel（秒杀预热限流、店铺查询 RT 熔断降级） |
| 工具 | Hutool、Lombok |

## 功能

- **会话与登录**：基于 Redis 的登录态与 Token 刷新拦截（`RefreshTokenInterceptor` / `LoginInterceptor`）。
- **商户与 GEO**：店铺信息缓存策略；Redis Geo 支持地理相关查询（见 `ShopServiceImpl`）。
- **秒杀下单**：Lua 扣库存 + Redis Stream 异步落库 + Redisson 防并发；**Sentinel** 对秒杀入口 **预热限流**（`SentinelRuleConfiguration` / `VoucherOrderServiceImpl`）。
- **幂等下单**：`POST /voucher-order/idempotent-token` 签发短时 Token，秒杀请求头携带 `X-Idempotency-Token`，Redis 原子校验并删除（`IdempotentTokenService`）；Stream 消费者侧按订单 **id 查重** 防止消息重试重复入库。
- **店铺查询**：`ShopServiceImpl` 上配置 Sentinel **限流 + RT 熔断** 与友好降级文案。

## 本地运行

1. **环境**：JDK 21、Maven 3.6+、SQL Server、Redis（默认配置见 `application.yaml`）。
2. **数据库**：当前配置为 **SQL Server**（见 `application.yaml`）。`src/main/resources/db/vdp.sql` 为表结构与示例数据的参考脚本；若脚本方言与所用数据库不一致，请按目标库迁移后再导入。
3. **配置**：默认连接本地 SQL Server 与 Redis；生产或演示环境建议通过环境变量覆盖，勿提交真实口令：

   ```text
   SPRING_DATASOURCE_URL
   SPRING_DATASOURCE_USERNAME
   SPRING_DATASOURCE_PASSWORD
   SPRING_REDIS_HOST
   SPRING_REDIS_PORT
   SPRING_REDIS_PASSWORD
   SERVER_PORT
   SENTINEL_DASHBOARD   # 可选，Sentinel 控制台地址，默认 127.0.0.1:8080
   ```

4. **可选**：下载 [Sentinel Dashboard](https://github.com/alibaba/Sentinel/releases)，应用侧默认暴露端口 `8719` 供控制台拉取指标；规则也可在代码 `SentinelRuleConfiguration` 中维护。

5. **启动**：

   ```bash
   mvn spring-boot:run
   ```

   主类：`com.vdp.vdpApplication`，默认端口 `8081`。

## 许可证
