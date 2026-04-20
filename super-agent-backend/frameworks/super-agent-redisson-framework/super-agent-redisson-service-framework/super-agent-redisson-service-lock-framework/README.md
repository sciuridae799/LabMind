# Super Agent Redisson Service Lock Framework

`super-agent-redisson-service-lock-framework` 提供注解式分布式锁能力，基于 `RedissonClient` 和 AOP 完成方法级互斥控制。

## 当前范围

当前模块只做一种使用方式：`@ServiceLock` 注解锁。

不会做以下事情：

- 不提供编程式锁 API
- 不提供默认降级逻辑
- 不吞掉加锁失败异常
- 不绕开统一锁名处理

## 自动装配入口

- 自动配置类：`com.superagent.redisson.servicelock.config.ServiceLockAutoConfiguration`
- 自动装配注册：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 主要组成

- 注解：`ServiceLock`
- 切面：`ServiceLockAspect`
- 锁信息处理：`ServiceLockInfoHandle`
- 锁工厂：`ServiceLockFactory`
- 锁管理器：`ManageLocker`
- 锁实现：
  - `RedissonReentrantLocker`
  - `RedissonFairLocker`
  - `RedissonReadLocker`
  - `RedissonWriteLocker`

## 支持的锁类型

`LockType` 当前支持 4 种：

- `Reentrant`
- `Fair`
- `Read`
- `Write`

底层映射关系固定：

- `Reentrant` -> `redissonClient.getLock`
- `Fair` -> `redissonClient.getFairLock`
- `Read` -> `redissonClient.getReadWriteLock(...).readLock()`
- `Write` -> `redissonClient.getReadWriteLock(...).writeLock()`

## 使用方式

```java
@ServiceLock(
    keys = {"#orderId", "submit"},
    lockType = LockType.Fair,
    waitTime = 2L,
    leaseTime = 5L,
    timeUnit = TimeUnit.SECONDS
)
public void submit(String orderId) {
}
```

## 注解约束

- `keys` 不能为空
- `keys` 中每一项不能空白
- `keys` 支持两种写法：
  - 固定字符串
  - SpEL 表达式，例如 `#p0`、`#arg0`、`#orderId`
- `waitTime` 必须大于等于 `0`
- `leaseTime` 必须大于 `0`
- `timeUnit` 不能为 `null`

不满足约束时，直接抛异常。

## 锁名规则

锁名统一走 `LockInfoHandleFactory`，命名规则固定为：

```text
{declaringClass}.{methodName}:{key1}:{key2}:...
```

示例：

```text
com.superagent.demo.OrderService.submit:42:submit
```

## 执行链路

1. `ServiceLockAspect` 拦截带 `@ServiceLock` 的方法
2. `ServiceLockInfoHandle` 解析注解和参数，生成锁名
3. `ServiceLockFactory` 根据 `LockType` 选择具体锁实现
4. 具体锁实现调用 Redisson `tryLock`
5. 获取成功后执行业务
6. `finally` 中释放当前线程持有的锁

## 失败路径

- 锁名解析失败：直接抛异常
- `tryLock` 返回 `false`：直接抛 `IllegalStateException`
- 业务执行异常：原样透出
- 解锁只在当前线程持锁时执行，不做兜底补偿

## 验证

本模块当前覆盖了以下验证：

- 锁信息解析
- 锁类型注册完整性
- 切面加锁成功和解锁
- 加锁失败直接中断

执行命令：

```bash
/Users/admin/Documents/apache-maven-3.9.11/bin/mvn \
  -s /Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml \
  -Dmaven.repo.local=/Users/admin/Documents/apache-maven-3.9.11/repo \
  -pl super-agent-backend/frameworks/super-agent-redisson-framework/super-agent-redisson-service-framework/super-agent-redisson-service-lock-framework \
  -am test
```

当前结果：`BUILD SUCCESS`
