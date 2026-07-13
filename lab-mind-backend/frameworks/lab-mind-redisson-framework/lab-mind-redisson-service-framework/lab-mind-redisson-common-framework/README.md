# LabMind Redisson Common Framework

`lab-mind-redisson-common-framework` 是 Redisson 公共装配模块，职责只有一层：基于 Spring Boot 自动配置统一创建 `RedissonClient` 和公共基础 Bean，给上层 `lock`、`lease`、`repeat-execute-limit`、`delay-queue` 复用。

## 当前范围

当前版本只支持 Redisson 单机模式，对应 `Config.useSingleServer()`。

不会做以下事情：

- 不支持哨兵
- 不支持集群
- 不支持主从
- 不支持默认值兜底
- 不支持错误配置兼容

## 自动装配入口

- 自动配置类：`com.labmind.redisson.common.config.RedissonCommonAutoConfiguration`
- 配置属性类：`com.labmind.redisson.common.config.RedissonBaseProperties`
- 自动装配注册：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 配置来源

配置统一收口到两部分：

- Spring Boot Redis 标准配置：`spring.data.redis.*`
- Redisson 扩展配置：`spring.redis.redisson.*`

当前实际使用到的配置项如下：

### `spring.data.redis.*`

- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.data.redis.database`
- `spring.data.redis.username`
- `spring.data.redis.password`
- `spring.data.redis.client-name`
- `spring.data.redis.connect-timeout`
- `spring.data.redis.timeout`

### `spring.redis.redisson.*`

- `spring.redis.redisson.protocol`
- `spring.redis.redisson.threads`
- `spring.redis.redisson.netty-threads`
- `spring.redis.redisson.local-lock-cache-ttl`

## 约束

这层装配不允许用默认值掩盖问题，启动时直接校验输入。

- `spring.data.redis.host` 必须显式配置
- `spring.data.redis.port` 必须显式配置
- `spring.data.redis.port` 必须是合法正整数
- `spring.redis.redisson.protocol` 只能是 `redis` 或 `rediss`
- `spring.redis.redisson.local-lock-cache-ttl` 必须大于 `0`
- `spring.redis.redisson.threads` 如果配置，必须大于 `0`
- `spring.redis.redisson.netty-threads` 如果配置，必须大于 `0`
- `spring.data.redis.database` 必须大于等于 `0`
- `spring.data.redis.connect-timeout` 如果配置，必须大于 `0`
- `spring.data.redis.timeout` 如果配置，必须大于 `0`

任何一项不满足，应用启动直接失败。

## 装配结果

当前模块输出 4 个基础 Bean：

- `RedissonClient`
- `RedissonDataHandle`
- `LocalLockCache`
- `LockInfoHandleFactory`

### `RedissonClient`

`RedissonClient` 由 `RedissonCommonAutoConfiguration` 创建，地址拼装规则固定为：

```text
{protocol}://{host}:{port}
```

示例：

```text
redis://127.0.0.1:6379
rediss://redis.example.com:6380
```

### `RedissonDataHandle`

`RedissonDataHandle` 是最薄的一层 Redis 数据访问封装，当前只提供：

- `get`
- `get(key, Class<T>)`
- `set`
- `set(key, value, ttl)`
- `exists`
- `delete`

约束也保持直接失败：

- `key` 不能为空白
- `value` 不能为 `null`
- `ttl` 必须大于 `0`
- 读取类型不匹配直接抛错

### `LocalLockCache`

`LocalLockCache` 是 JVM 本地锁缓存，内部维护 `lockName -> ReentrantLock` 的映射。

行为规则只有两条：

- 访问锁时更新最后使用时间
- 锁未持有、无等待线程且超过 TTL 时淘汰

它只负责本地锁对象缓存，不承担分布式互斥语义。

### `LockInfoHandleFactory`

`LockInfoHandleFactory` 负责统一锁名拼接，规则固定为冒号分隔：

```text
namespace:key1:key2:key3
```

约束：

- `namespace` 不能为空白
- 任意 `key` 不能为 `null`
- 任意 `key` 不能为空白

## 示例配置

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      connect-timeout: 2s
      timeout: 4s
  redis:
    redisson:
      protocol: redis
      threads: 8
      netty-threads: 4
      local-lock-cache-ttl: 30s
```

## 链路说明

输入链路：

- Spring Boot 绑定 `spring.data.redis.*`
- Spring Boot 绑定 `spring.redis.redisson.*`

处理链路：

1. `RedissonCommonAutoConfiguration` 校验配置
2. 构造单机 `Config`
3. 创建 `RedissonClient`
4. 基于 `RedissonClient` 输出 `RedissonDataHandle`
5. 基于 `local-lock-cache-ttl` 输出 `LocalLockCache`
6. 输出 `LockInfoHandleFactory`

输出链路：

- 上层模块直接注入 `RedissonClient`
- 或注入 `RedissonDataHandle`
- 或注入 `LocalLockCache`
- 或注入 `LockInfoHandleFactory`

## 验证

本模块当前验证覆盖了以下链路：

- 输入校验：缺失 host、缺失 port、端口非法、协议非法、TTL 非法
- 处理校验：single-server `Config` 构建、线程数、超时、认证信息、数据库编号
- 输出校验：4 个基础 Bean 可创建
- 注册校验：`AutoConfiguration.imports` 生效
- 边界校验：本地锁缓存淘汰规则、锁名拼接规则

执行命令：

```bash
/Users/admin/Documents/apache-maven-3.9.11/bin/mvn \
  -s /Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml \
  -Dmaven.repo.local=/Users/admin/Documents/apache-maven-3.9.11/repo \
  -pl lab-mind-backend/frameworks/lab-mind-redisson-framework/lab-mind-redisson-service-framework/lab-mind-redisson-common-framework \
  -am test
```

当前结果：`BUILD SUCCESS`
