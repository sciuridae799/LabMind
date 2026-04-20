# Super Agent Redisson Service Lease Framework

`super-agent-redisson-service-lease-framework` 提供基于 Lua 脚本的 Redis 租约互斥能力。

## 当前范围

当前模块只保留一个核心对象：`RedisLeaseManager`。

它只支持 3 个原子操作：

- `acquire`
- `renew`
- `release`

不会做以下事情：

- 不暴露 `RLock`
- 不复用 Redisson 现成锁语义
- 不做 ownerToken 不匹配时的兼容释放
- 不吞掉非法 key、token、ttl 输入

## 自动装配入口

- 自动配置类：`com.superagent.redisson.servicelease.config.ServiceLeaseAutoConfiguration`
- 自动装配注册：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 使用方式

```java
boolean acquired = redisLeaseManager.acquire("chat:running:42", "token-1", Duration.ofSeconds(30));
boolean renewed = redisLeaseManager.renew("chat:running:42", "token-1", Duration.ofSeconds(30));
boolean released = redisLeaseManager.release("chat:running:42", "token-1");
```

## Lua 语义

### `acquire`

只有在 key 不存在时才写入：

- `KEYS[1]` 不存在 -> `psetex`
- `KEYS[1]` 已存在 -> 返回失败

### `renew`

只有 ownerToken 匹配时才续期：

- `get(KEYS[1]) == ownerToken` -> `pexpire`
- 不匹配 -> 返回失败

### `release`

只有 ownerToken 匹配时才删除：

- `get(KEYS[1]) == ownerToken` -> `del`
- 不匹配 -> 返回失败

## 输入约束

- `leaseKey` 不能为空白
- `ownerToken` 不能为空白
- `ttl` 不能为 `null`
- `ttl` 必须大于 `0`

不满足约束时直接抛异常。

## 返回语义

- `acquire` 成功返回 `true`，失败返回 `false`
- `renew` 成功返回 `true`，失败返回 `false`
- `release` 成功返回 `true`，失败返回 `false`

如果脚本返回值不是整数，直接抛异常。

## 执行链路

1. `ServiceLeaseAutoConfiguration` 注入 `RedisLeaseManager`
2. 业务代码直接调用 `acquire/renew/release`
3. `RedisLeaseManager` 做输入校验
4. 通过 `redissonClient.getScript(StringCodec.INSTANCE).eval(...)` 执行 Lua
5. 按脚本返回值转换成布尔结果

## 验证

本模块当前覆盖了以下验证：

- `acquire/renew/release` 调用链
- 脚本返回值转换
- TTL 非法输入直接失败

执行命令：

```bash
/Users/admin/Documents/apache-maven-3.9.11/bin/mvn \
  -s /Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml \
  -Dmaven.repo.local=/Users/admin/Documents/apache-maven-3.9.11/repo \
  -pl super-agent-backend/frameworks/super-agent-redisson-framework/super-agent-redisson-service-framework/super-agent-redisson-service-lease-framework \
  -am test
```

当前结果：`BUILD SUCCESS`
