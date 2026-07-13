# LabMind Redisson Delay Queue Framework

`lab-mind-redisson-delay-queue-framework` 提供基于 Redisson 的延迟队列能力。

## 当前范围

当前模块只提供一套统一的延迟队列模型：

- 生产端通过 `DelayQueueContext` 发送消息
- 消费端通过实现 `ConsumerTask` 注册消费者
- topic 分片统一由 `IsolationRegionSelector` 决定

不会做以下事情：

- 不允许生产和消费各自定义不同的分片规则
- 不做重复 topic 的兼容注册
- 不提供单独的消费配置中心

## 自动装配入口

- 自动配置类：`com.labmind.redisson.delayqueue.config.DelayQueueAutoConfig`
- 配置属性类：`com.labmind.redisson.delayqueue.config.DelayQueueProperties`
- 自动装配注册：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 配置项

当前模块只使用两项配置：

- `spring.redis.redisson.delay-queue.shard-count`
- `spring.redis.redisson.delay-queue.consumer-poll-timeout`

约束：

- `shard-count` 必须大于 `0`
- `consumer-poll-timeout` 必须显式配置
- `consumer-poll-timeout` 必须大于 `0`

## 使用方式

### 生产端

```java
delayQueueContext.sendMessage("chat-topic", payload, 10L, TimeUnit.SECONDS);
```

### 消费端

```java
@Component
public class ChatConsumerTask implements ConsumerTask {

    @Override
    public String topic() {
        return "chat-topic";
    }

    @Override
    public void consume(Object content) {
    }
}
```

## 分片规则

topic 分片统一由 `IsolationRegionSelector` 处理。

规则固定为：

```text
{topic}-{index}
```

示例：

```text
chat-topic-0
chat-topic-1
chat-topic-2
```

生产端通过轮询选择分片，消费端通过 `listTopics(topic)` 监听全部分片，二者共享同一套路由逻辑。

## 执行链路

### 生产链路

1. `DelayQueueContext.sendMessage`
2. `DelayQueueProduceCombine`
3. `IsolationRegionSelector.selectTopic`
4. `DelayProduceQueue`
5. `redissonClient.getBlockingQueue`
6. `redissonClient.getDelayedQueue(blockingQueue)`
7. `offer(content, delay, timeUnit)`

### 消费链路

1. `DelayQueueInitHandler` 在单例初始化后扫描全部 `ConsumerTask`
2. 校验 topic 唯一性
3. `IsolationRegionSelector.listTopics` 生成每个 topic 的全部分片
4. 每个分片启动一个 `DelayConsumerQueue`
5. 消费线程轮询 `RBlockingQueue.poll`
6. 取到消息后调用 `ConsumerTask.consume`

## 失败路径

- topic 为空白：直接抛异常
- delay 为负数：直接抛异常
- `ConsumerTask.topic()` 重复：启动直接失败
- 消费线程被中断：停止当前消费者
- `consume` 抛异常：包装为 `IllegalStateException` 直接失败

## 验证

本模块当前覆盖了以下验证：

- 分片轮询路由
- 分片 topic 列举
- 生产端统一路由
- 延迟队列发送
- 消费端轮询消费
- 重复 topic 拒绝注册

执行命令：

```bash
/Users/admin/Documents/apache-maven-3.9.11/bin/mvn \
  -s /Users/admin/Documents/apache-maven-3.9.11/conf/settings.xml \
  -Dmaven.repo.local=/Users/admin/Documents/apache-maven-3.9.11/repo \
  -pl lab-mind-backend/frameworks/lab-mind-redisson-framework/lab-mind-redisson-delay-queue-framework \
  -am test
```

当前结果：`BUILD SUCCESS`
