# LabMind Redisson Repeat Execute Limit Framework

`lab-mind-redisson-repeat-execute-limit-framework` 提供防重复执行能力，目标是阻断同一业务键的重复提交或重复消费。

## 当前范围

当前模块只做注解式使用：`@RepeatExecuteLimit`。

实现严格按“三层防线”执行：

1. Redis 成功标记
2. JVM 本地 `ReentrantLock`
3. Redisson 公平锁

不会做以下事情：

- 不在业务异常时写成功标记
- 不吞掉业务异常
- 不绕开统一锁名规则
- 不做额外重试和补偿

## 自动装配入口

- 自动配置类：`com.labmind.redisson.repeatexecutelimit.config.RepeatExecuteLimitAutoConfiguration`
- 自动装配注册：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 使用方式

```java
@RepeatExecuteLimit(
    keys = {"#orderId"},
    waitTime = 1L,
    lockLeaseTime = 5L,
    successLeaseTime = 10L,
    timeUnit = TimeUnit.SECONDS
)
public void submit(String orderId) {
}
```

## 注解约束

- `keys` 不能为空
- `keys` 中每一项不能空白
- `keys` 支持固定字符串和 SpEL 表达式
- `waitTime` 必须大于等于 `0`
- `lockLeaseTime` 必须大于 `0`
- `successLeaseTime` 必须大于 `0`
- `timeUnit` 不能为 `null`

## 键规则

本模块内部固定使用三类 key：

- 成功标记：`repeat-execute-limit:success:{class}.{method}:{keys...}`
- 本地锁键：`repeat-execute-limit:local-lock:{class}.{method}:{keys...}`
- 分布式锁键：`repeat-execute-limit:distributed-lock:{class}.{method}:{keys...}`

三类 key 都统一走 `LockInfoHandleFactory` 拼接。

## 执行链路

1. `RepeatExecuteLimitAspect` 拦截带 `@RepeatExecuteLimit` 的方法
2. `RepeatExecuteLimitLockInfoHandle` 解析参数，生成成功标记键、本地锁键、分布式锁键
3. 先读 Redis 成功标记
4. 命中 `SUCCESS` 时直接拒绝
5. 未命中时，先抢 JVM 本地锁
6. 本地锁成功后，再抢 Redisson 公平锁
7. 两层锁都成功后执行业务
8. 业务成功后，把 `SUCCESS` 写回 Redis，并设置成功标记 TTL
9. 在 `finally` 中释放分布式锁和本地锁

## 失败路径

- 成功标记已存在：直接抛异常
- 成功标记值不是 `SUCCESS`：直接抛异常
- 本地锁获取失败：直接抛异常
- 分布式公平锁获取失败：直接抛异常
- 业务异常：原样透出，不写成功标记

## 验证

本模块当前覆盖了以下验证：

- 键名生成
- 成功标记命中拦截
- 业务成功后写成功标记
- 业务失败不写成功标记
- 分布式公平锁释放

执行命令：

```bash
/Users/admin/Documents/apache-maven-3.9.11/bin/mvn \
  -s /Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml \
  -Dmaven.repo.local=/Users/admin/Documents/apache-maven-3.9.11/repo \
  -pl lab-mind-backend/frameworks/lab-mind-redisson-framework/lab-mind-redisson-service-framework/lab-mind-redisson-repeat-execute-limit-framework \
  -am test
```

当前结果：`BUILD SUCCESS`
