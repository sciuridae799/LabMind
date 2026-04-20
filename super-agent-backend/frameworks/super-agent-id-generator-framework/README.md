# Super Agent ID Generator Framework

`super-agent-id-generator-framework` 承载项目内的 ID 生成能力。当前模块只实现项目自研的 Snowflake 方案，不接百度 UID，也不保留双轨兼容逻辑。

## 模块职责

这个模块只解决两件事：

1. 应用启动时，从 Redis 原子分配一组 `(workId, dataCenterId)`
2. 运行期基于这组机器位，在本地生成标准 Snowflake ID

也就是说，Redis 只参与启动分配，不参与每次发号；真正地发号逻辑始终在进程内完成。

## 当前链路

模块入口是 `IdGeneratorAutoConfig`，启动链路如下：

1. 注入 `WorkAndDataCenterIdHandler`
2. 由 `WorkAndDataCenterIdHandler` 执行 `workAndDataCenterId.lua`
3. Lua 从 Redis 分配 `(workId, dataCenterId)`，并返回 JSON
4. JSON 反序列化为 `WorkDataCenterId`
5. 用这组机器位构造 `SnowflakeIdGenerator`
6. 业务代码统一通过 `SnowflakeIdGenerator` 生成 ID

## 代码落点

当前实现核心文件如下：

1. `src/main/java/com/superagent/idgenerator/config/IdGeneratorAutoConfig.java`
2. `src/main/java/com/superagent/idgenerator/toolkit/WorkAndDataCenterIdHandler.java`
3. `src/main/java/com/superagent/idgenerator/toolkit/WorkDataCenterId.java`
4. `src/main/java/com/superagent/idgenerator/toolkit/SnowflakeIdGenerator.java`
5. `src/main/resources/lua/workAndDataCenterId.lua`

## 实现约束

这条链路按下面的原则实现：

1. `workId` 和 `dataCenterId` 固定使用 5 bit，范围 `0~31`
2. Lua 返回空值、字段缺失、越界、非 JSON，都直接失败
3. 时钟回拨直接失败，不吞错、不降级
4. 同毫秒内 `sequence` 用尽后，等待下一毫秒继续生成

## 详细设计

完整设计、边界条件和验证方案见 [SNOWFLAKE_DESIGN.md](./SNOWFLAKE_DESIGN.md)。
