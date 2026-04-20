# Super Agent Business Chat

`super-agent-business-chat` 是当前业务服务运行模块，用于承载对话与知识库相关业务代码以及 Spring Boot 启动入口。

当前骨架按真实业务域拆分为：

- `com.superagent.business.chat.manage`
- `com.superagent.business.chat.chatagent`

其中 `com.superagent.business.chat.chatagent.rag` 作为对话 Agent 主链路下的核心子域继续保留在业务模块内部。
