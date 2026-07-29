# LabMind 论文知识图谱服务

这是与原文档问答链路隔离的 Python 模块。Java 只负责登录、工作组权限和 HTTP 转发；本服务负责论文保存、Kafka 构图任务、固定 Schema 抽取、证据校验、PostgreSQL 存储和图谱查询。

## 目录

```text
app/
├── api/             # 供 Java 调用的 FastAPI 接口
├── domain/          # 7 类实体、6 类关系及严格校验规则
├── infrastructure/  # PostgreSQL、MinIO、Kafka、LLM、PDF 解析
├── prompts/         # 计算机论文图谱提示词
├── services/        # 上传、版本、构图和查询流程
├── main.py          # HTTP 进程
└── worker.py        # paper.graph.build 消费进程
```

PDF 解析只使用 PyMuPDF；没有 Tika/MinerU/pypdf 级联。模型每个 Chunk 调用一次，任何非法类型、错误关系方向、缺失证据或非原文 quote 都会使该文档明确进入 `FAILED`。

单个 PDF 最大 10 MB。前端、Java multipart 入口和 Python 内部接口使用同一限制；超过限制时接口返回 413，不创建文档或构图任务。

## 初始化

```bash
cd /Users/admin/Documents/web-management/labmind/paper-graph-service
python3 -m venv .venv
.venv/bin/python -m pip install -e '.[test]'
cp .env.example .env
```

先在专用 PostgreSQL 数据库执行：

```bash
psql "$LAB_MIND_PAPER_GRAPH_POSTGRES_DSN" \
  -f /Users/admin/Documents/web-management/labmind/sql/lab-mind-paper-graph/postgresql/init/001_create_paper_graph_tables.sql
```

`.env` 中的内部令牌必须与 Java 使用的 `LAB_MIND_PAPER_GRAPH_INTERNAL_API_TOKEN` 完全一致。Java 还需要在根目录 `.env` 配置 `LAB_MIND_PAPER_GRAPH_SERVICE_BASE_URL`。

## 启动

在两个终端分别运行：

```bash
/Users/admin/Documents/web-management/labmind/scripts/run-paper-graph-local.sh api
/Users/admin/Documents/web-management/labmind/scripts/run-paper-graph-local.sh worker
```

## 测试

```bash
cd /Users/admin/Documents/web-management/labmind/paper-graph-service
.venv/bin/python -m pytest
```

## 生产部署

生产环境保持 API 和 Worker 两个独立 systemd 服务，单元文件位于 `deploy/`。服务先读取 Java 后端的 `/opt/labmind/.env`，再读取 `/opt/labmind-paper-graph/.env`；后者只负责把现有共享变量映射为 Python 模块的显式配置：

```bash
LAB_MIND_PAPER_GRAPH_POSTGRES_DSN="${LAB_MIND_PGVECTOR_URL#jdbc:}"
PGUSER="${LAB_MIND_PGVECTOR_USERNAME}"
PGPASSWORD="${LAB_MIND_PGVECTOR_PASSWORD}"
LAB_MIND_PAPER_GRAPH_LLM_CHAT_COMPLETIONS_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
LAB_MIND_PAPER_GRAPH_LLM_API_KEY="${ALI_BAI_LIAN_API_KEY}"
LAB_MIND_PAPER_GRAPH_LLM_MODEL=qwen-plus-latest
LAB_MIND_PAPER_GRAPH_INTERNAL_API_TOKEN=<与 Java 完全一致的内部令牌>
LAB_MIND_KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT
LAB_MIND_KAFKA_SASL_MECHANISM=PLAIN
```

API 只监听 `127.0.0.1:18090`，Java 使用 `LAB_MIND_PAPER_GRAPH_SERVICE_BASE_URL=http://127.0.0.1:18090` 转发请求；Worker 只消费 `paper.graph.build`。
