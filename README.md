# LabMind

LabMind 是面向知识库场景的 AI 对话系统，支持文档解析、知识路由、混合检索、工作区管理与调用链观测。

## 技术栈

- 前端：Vue 3、TypeScript、Vite
- 后端：Java 25、Spring Boot 3.5、Maven
- 基础设施：MySQL、Redis、Kafka、MinIO、Neo4j、PostgreSQL/pgvector、Elasticsearch

## 项目结构

```text
frontend/          前端应用
lab-mind-backend/  后端聚合工程
sql/               数据库初始化与迁移脚本
docs/              核心链路设计文档
scripts/           本地运行脚本
```

## 本地启动

准备 JDK 25、Maven 3.9.11、Node.js/npm 及上述基础设施，然后复制环境变量模板，并根据后端配置补齐连接信息与 API 密钥：

```bash
cp .env.example .env
```

启动后端：

```bash
./scripts/run-business-chat-local.sh
```

在另一个终端启动前端：

```bash
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:5173`。前端会将 `/backend` 请求代理到 `http://127.0.0.1:8080`。

环境变量定义见 [`.env.example`](.env.example)，后端完整配置见 [`application.yaml`](lab-mind-backend/services/lab-mind-business/lab-mind-business-chat/src/main/resources/application.yaml)。
