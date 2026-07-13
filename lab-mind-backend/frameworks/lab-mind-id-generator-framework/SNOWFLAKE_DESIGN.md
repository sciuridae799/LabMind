# 项目自研 Snowflake 方案

本文档描述项目自研的 Snowflake 发号链路。

## 1. 目标

这条链路只做一件事：应用启动时从 Redis 原子分配一组 `(workId, dataCenterId)`，进程启动完成后，运行期只用这组机器位在本地生成标准 Snowflake ID。

必须满足以下约束：

1. Redis 只参与启动时机器位分配，不参与每次发号。
2. `workId` 和 `dataCenterId` 都使用 5 bit，取值范围固定为 `0~31`。
3. 分配失败、越界、脚本返回异常、时钟回拨都必须显式失败，不能吞错、不能给默认值、不能降级。
4. 同一实例进程内只允许使用一组确定的机器位，不能运行中动态切换。
5. 全链路只保留一条正确实现，不保留旧错误行为兼容。

## 2. 入口与整体链路

入口类固定为 `IdGeneratorAutoConfig`，完整链路如下：

1. Spring 启动装配 `IdGeneratorAutoConfig`
2. `IdGeneratorAutoConfig` 创建 `WorkAndDataCenterIdHandler`
3. `WorkAndDataCenterIdHandler` 加载 `workAndDataCenterId.lua`
4. `WorkAndDataCenterIdHandler` 调用 `StringRedisTemplate.execute(...)`
5. Lua 脚本在 Redis 中原子分配 `(workId, dataCenterId)`
6. 脚本返回 JSON
7. Java 将 JSON 反序列化为 `WorkDataCenterId`
8. `IdGeneratorAutoConfig` 使用 `WorkDataCenterId` 构造 `SnowflakeIdGenerator`
9. 业务代码通过 `SnowflakeIdGenerator.nextId()` 获取 ID

核心原则只有两个：

1. 机器位分配和 Snowflake 计算必须解耦。
2. 根因逻辑放在分配器和生成器内部，不在外围包补丁分支。

## 3. 建议文件落点

结合当前模块目录，建议直接按下面的最小文件集合落地：

1. `src/main/java/com/labmind/idgenerator/config/IdGeneratorAutoConfig.java`
2. `src/main/java/com/labmind/idgenerator/toolkit/WorkAndDataCenterIdHandler.java`
3. `src/main/java/com/labmind/idgenerator/toolkit/WorkDataCenterId.java`
4. `src/main/java/com/labmind/idgenerator/toolkit/SnowflakeIdGenerator.java`
5. `src/main/resources/lua/workAndDataCenterId.lua`

不额外拆服务层、仓储层、策略层；当前问题的本质就是启动分配机器位和本地生成 Snowflake，不需要多余抽象。

## 4. 组件职责

### 4.1 `IdGeneratorAutoConfig`

职责只允许是 Spring Bean 装配，不承载发号算法，不承载 Redis 细节。

建议暴露的 Bean：

1. `WorkAndDataCenterIdHandler`
2. `WorkDataCenterId`
3. `SnowflakeIdGenerator`

装配顺序必须固定：

1. 先创建 `WorkAndDataCenterIdHandler`
2. 再调用它拿到 `WorkDataCenterId`
3. 最后构造 `SnowflakeIdGenerator`

如果 `WorkDataCenterId` 获取失败，应用启动必须直接失败。

### 4.2 `WorkAndDataCenterIdHandler`

这是机器位分配的根因位置，必须把 Redis + Lua 的完整逻辑收敛在这里。

它的职责只有三件事：

1. 加载 `workAndDataCenterId.lua`
2. 调用 `StringRedisTemplate.execute(...)`
3. 把返回值转换成 `WorkDataCenterId`

这里必须完成以下校验：

1. Lua 脚本成功加载
2. Redis 执行结果不为空
3. 返回内容是合法 JSON
4. JSON 中同时包含 `workId`、`dataCenterId`
5. 两个值都在 `0~31`

这些校验必须在这里闭环，不能把异常吞掉后让外层继续启动。

建议方法：

```java
public WorkDataCenterId allocate();
```

如果希望拆内部私有方法，可以保留：

```java
private DefaultRedisScript<String> loadScript();
private WorkDataCenterId parse(String result);
```

### 4.3 `WorkDataCenterId`

它是纯值对象，只表达一组机器位：

1. `long workId`
2. `long dataCenterId`

要求：

1. 不可变
2. 创建时立即校验
3. 越界直接报错

建议结构：

```java
public record WorkDataCenterId(long workId, long dataCenterId) {
}
```

不要在这个对象里塞 Redis、脚本、环境变量等无关逻辑。

### 4.4 `SnowflakeIdGenerator`

它只负责标准 Snowflake 算法，不再依赖 Redis。

建议内部状态：

1. `workerId`
2. `dataCenterId`
3. `sequence`
4. `lastTimestamp`
5. `epoch`

当前源码实现里，`epoch` 固定为 `2025-01-01 00:00:00 UTC`。

建议公开方法：

```java
public synchronized long nextId();
public String getOrderNumber(long userId);
public long parseIdTimestamp(long id);
```

方法约束：

1. `nextId()` 负责生成基础 ID
2. 同毫秒内 `sequence` 自增
3. `sequence` 达到上限后，自旋到下一毫秒
4. 时钟回拨直接抛异常
5. `parseIdTimestamp(id)` 只负责解析时间戳，不做业务补偿
6. `getOrderNumber(userId)` 如果存在，只能是基于 `nextId()` 的业务包装，不能形成第二套发号逻辑

## 5. Redis 与 Lua 设计

### 5.1 Redis Key

Redis 中只维护两个 key：

1. `snowflake_work_id`
2. `snowflake_data_center_id`

语义分别表示“当前最近一次分配后的 workerId”和“当前最近一次分配后的 dataCenterId”。

### 5.2 Lua 脚本目标

脚本的目标是原子分配一组 `(workId, dataCenterId)`，不能把两个值拆成两次普通 Redis 写入，否则并发启动多个实例时会出现重复分配或交叉覆盖。

### 5.3 Lua 分配规则

分配规则固定如下：

1. 优先递增 `workId`
2. 当 `workId < 31` 时，`workId = workId + 1`
3. 当 `workId == 31` 时，`workId = 0`，同时递增 `dataCenterId`
4. 当 `dataCenterId < 31` 时，`dataCenterId = dataCenterId + 1`
5. 当 `dataCenterId == 31` 且 `workId == 31` 时，两个值同时回到 `0`
6. 返回本次分配给当前实例的最终结果 JSON

这里必须把返回语义定义死：

脚本返回值就是“本次调用者拿到的机器位”，不是“旧值”，不是“下一个值”，不允许 Java 侧再做二次推导。

### 5.4 Lua 伪代码

```lua
local workKey = KEYS[1]
local dataCenterKey = KEYS[2]
local maxValue = tonumber(ARGV[1])

local workId = tonumber(redis.call('get', workKey))
local dataCenterId = tonumber(redis.call('get', dataCenterKey))

if workId == nil then
    workId = -1
end

if dataCenterId == nil then
    dataCenterId = 0
end

if workId < maxValue then
    workId = workId + 1
else
    workId = 0
    if dataCenterId < maxValue then
        dataCenterId = dataCenterId + 1
    else
        dataCenterId = 0
    end
end

redis.call('set', workKey, workId)
redis.call('set', dataCenterKey, dataCenterId)

return cjson.encode({
    workId = workId,
    dataCenterId = dataCenterId
})
```

说明：

1. `workId` 初始使用 `-1` 是为了让第一次分配拿到 `0`
2. `dataCenterId` 初始使用 `0`，与第一次分配配合后得到 `(0, 0)`

## 6. `SnowflakeIdGenerator` 位布局

位布局保持标准 64 bit Snowflake 结构：

1. 1 bit 符号位，固定为 `0`
2. 41 bit 时间戳差值
3. 5 bit `dataCenterId`
4. 5 bit `workerId`
5. 12 bit `sequence`

由此得到以下固定约束：

1. `workerId` 最大值 `31`
2. `dataCenterId` 最大值 `31`
3. 每毫秒单实例最大序列数 `4095`

## 7. 关键方法设计

### 7.1 `nextId()`

标准流程：

1. 获取当前时间戳
2. 如果当前时间戳小于 `lastTimestamp`，直接抛异常
3. 如果当前时间戳等于 `lastTimestamp`，则 `sequence = (sequence + 1) & SEQUENCE_MASK`
4. 如果 `sequence == 0`，阻塞到下一毫秒
5. 如果当前时间戳大于 `lastTimestamp`，则 `sequence = 0`
6. 更新 `lastTimestamp`
7. 通过移位拼装最终 ID

### 7.2 `getOrderNumber(long userId)`

如果业务必须保留该方法，只能基于 `nextId()` 做包装，不能把 `userId` 混入 Snowflake 核心位运算。

### 7.3 `parseIdTimestamp(long id)`

这个方法只负责从 Snowflake ID 反解出原始时间戳。

## 8. 启动失败与运行期失败策略

### 8.1 启动期

以下情况必须直接阻断应用启动：

1. Redis 不可达
2. Lua 脚本不存在或加载失败
3. Redis 执行返回空值
4. 返回值不是合法 JSON
5. `workId` 或 `dataCenterId` 缺失
6. `workId` 或 `dataCenterId` 越界

### 8.2 运行期

以下情况必须直接抛错，不允许静默修正：

1. 时钟回拨
2. 生成器内部机器位为空或非法

唯一允许的等待行为只有一个：同毫秒 `sequence` 用尽时等待到下一毫秒。

## 9. 全链路验证

验证不能只看“能不能生成数字”，必须覆盖输入、处理、输出、边界和回归影响。

### 9.1 Lua 脚本验证

至少覆盖：

1. 首次分配，返回 `(0, 0)`
2. 连续递增，`workId` 从 `0` 到 `31`
3. `workId` 从 `31` 回到 `0` 时，`dataCenterId` 加 `1`
4. `(31, 31)` 之后回到 `(0, 0)`
5. 并发执行时无重复分配

### 9.2 `WorkAndDataCenterIdHandler` 单测

至少覆盖：

1. 正常 JSON 解析
2. 空返回值
3. 非 JSON 返回
4. 字段缺失
5. 越界值
6. Redis 执行异常

### 9.3 `SnowflakeIdGenerator` 单测

至少覆盖：

1. 连续生成 ID 唯一且总体递增
2. 同毫秒内 `sequence` 递增
3. `sequence` 用尽后进入下一毫秒
4. 时钟回拨抛异常
5. `parseIdTimestamp(id)` 解析正确

### 9.4 集成验证

至少覆盖：

1. Spring 启动时能完整装配 `WorkDataCenterId` 和 `SnowflakeIdGenerator`
2. 多实例连续启动时，拿到的 `(workId, dataCenterId)` 符合轮转预期
3. 业务代码实际调用 `nextId()` 时不再访问 Redis

## 10. 不做的事

本方案明确不做以下事情：

1. 不保留双发号器并存
2. 不在失败时回退到随机机器位
3. 不在 Redis 异常时改成本地默认值
4. 不为了“先跑起来”在外围包特判分支
5. 不额外设计当前需求之外的扩展点

## 11. 最终落地标准

当这条链路真正编码完成时，必须达到以下完成标准：

1. 启动时只分配一次 `(workId, dataCenterId)`
2. 运行期所有 ID 都由同一个 `SnowflakeIdGenerator` 生成
3. Redis/Lua 只承担机器位分配，不承担每次发号
4. 所有越界、空值、时钟异常都显式失败
5. 单测、集成验证、边界验证完整覆盖

这就是项目自研 Snowflake 的唯一正确主线。
