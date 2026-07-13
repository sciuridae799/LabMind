# LabMind Business

`lab-mind-business` 是业务聚合模块，负责组织各个业务服务子模块。

当前规划中的业务服务运行入口下沉到 `lab-mind-business-chat`，父模块本身不再承载 Spring Boot 启动类和业务运行依赖。
