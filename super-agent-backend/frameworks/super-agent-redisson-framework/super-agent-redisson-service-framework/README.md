# Super Agent Redisson Service Framework

`super-agent-redisson-service-framework` 是服务类 Redisson 能力聚合模块，负责组织公共能力、服务锁、服务租约和重复执行限制能力。

## 模块组成

- `super-agent-redisson-common-framework`
- `super-agent-redisson-service-lock-framework`
- `super-agent-redisson-service-lease-framework`
- `super-agent-redisson-repeat-execute-limit-framework`

## 当前职责

这层只负责组合服务类 Redisson 基础设施，不承载延迟队列。

能力分工如下：

- `common`：统一创建 `RedissonClient`、`RedissonDataHandle`、`LocalLockCache`、`LockInfoHandleFactory`
- `service-lock`：提供注解式分布式锁
- `service-lease`：提供 Lua 版租约互斥
- `repeat-execute-limit`：提供防重复执行能力

## 对外使用方式

- 直接注入 `RedissonClient`
- 直接注入 `RedissonDataHandle`
- 直接注入 `RedisLeaseManager`
- 注解式使用 `@ServiceLock`
- 注解式使用 `@RepeatExecuteLimit`

## 自动装配

各模块都通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动装配。

当前自动装配类：

- `com.superagent.redisson.common.config.RedissonCommonAutoConfiguration`
- `com.superagent.redisson.servicelock.config.ServiceLockAutoConfiguration`
- `com.superagent.redisson.servicelease.config.ServiceLeaseAutoConfiguration`
- `com.superagent.redisson.repeatexecutelimit.config.RepeatExecuteLimitAutoConfiguration`

## 验证

服务类 Redisson 模块当前验证命令：

```bash
/Users/admin/Documents/apache-maven-3.9.11/bin/mvn \
  -s /Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml \
  -Dmaven.repo.local=/Users/admin/Documents/apache-maven-3.9.11/repo \
  -pl super-agent-backend/frameworks/super-agent-redisson-framework/super-agent-redisson-service-framework/super-agent-redisson-common-framework,super-agent-backend/frameworks/super-agent-redisson-framework/super-agent-redisson-service-framework/super-agent-redisson-service-lock-framework,super-agent-backend/frameworks/super-agent-redisson-framework/super-agent-redisson-service-framework/super-agent-redisson-service-lease-framework,super-agent-backend/frameworks/super-agent-redisson-framework/super-agent-redisson-service-framework/super-agent-redisson-repeat-execute-limit-framework \
  -am test
```

当前结果：`BUILD SUCCESS`
