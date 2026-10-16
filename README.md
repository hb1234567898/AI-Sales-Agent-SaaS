# AI Sales Follow-up Agent

面向中小销售团队的 AI 跟进工作台。当前仓库包含可运行的后端、前端、PostgreSQL Compose 和数据库迁移骨架。

## 目录

```text
.
├── backend/       Spring Boot 4.1 + MyBatis-Plus 业务服务
├── ai-service/    FastAPI + LangChain/LangGraph AI 服务
├── frontend/      React + Vite + TypeScript 手工设计系统
├── db/migration/  Flyway PostgreSQL 迁移
├── docs/          技术与前端设计
└── compose.yaml   本地 PostgreSQL
```

## 环境要求

- Java 17+
- Node.js 20.19+ 或 22.12+
- pnpm
- Docker Compose

## 本地启动

先复制环境变量示例：

```powershell
Copy-Item .env.example .env
```

启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

启动后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

启动前端：

```powershell
cd frontend
pnpm install
pnpm dev
```

打开 `http://localhost:5173`。开发服务器会把 `/api` 和 `/actuator` 代理到 `http://localhost:8080`。

首次启动会创建演示工作区和本地账号：

```text
邮箱：chen.mo@demo.local
密码：Demo@123456
```

另外两个演示账号为 `li.xin@demo.local` 和 `wang.ning@demo.local`，初始密码相同。可通过 `DEMO_LOGIN_PASSWORD` 修改初始密码。部署到正式环境时请设置 `DEMO_SEED_ENABLED=false`，并通过正式的管理员流程创建账号。

认证采用 JWT 双令牌：Access Token 默认有效 15 分钟，请求时通过 `Authorization: Bearer` 发送；Refresh Token 随登录会话保存并在每次刷新时轮换。生产环境必须配置固定的 `AUTH_JWT_SIGNING_KEY`，可使用 `openssl rand -base64 32` 生成。更换签名密钥会使现有登录全部失效。

## 验证

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
pnpm lint
pnpm test
pnpm build
```

## FastAPI + LangChain AI 服务

Spring Boot 是业务主服务，`ai-service` 是必需的模型编排服务。前端仍只访问 Spring Boot；认证、租户、CRM、审批、工具执行及 Token 用量入库均留在 Java，Python 负责全部模型调用、LangGraph 计划、流式回答、聊天分析和 RAG。

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\pip install -e ".[test]"
$env:AI_SERVICE_TOKEN = "local-dev-token"
.\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8090
```

Spring Boot 通过 `AI_SERVICE_BASE_URL=http://127.0.0.1:8090` 和相同的 `AI_SERVICE_TOKEN` 访问 AI 服务。所有模型调用都由 FastAPI + LangChain 执行，Java 不再直连模型，也不保留 Spring AI 回退路径。

模型 API Key、模型名和 OpenAI 兼容地址由登录用户在“设置 → AI 模型”中配置，Spring Boot 解密后仅在当前内部请求中传给 Python，不写入 Python 配置或日志。

发布流水线使用 Python 3.12 构建 Linux 离线 wheelhouse，部署服务器需安装 `python3.12`；如解释器命令不同，可通过部署环境变量 `PYTHON_BIN` 指定。

客户详情的“互动记录”支持粘贴微信、WhatsApp 等聊天原文。导入后点击单条记录上的“AI 分析”，系统会生成需求、痛点、异议、风险、意向评分、分析依据和建议动作。分析结果默认处于待确认状态；只有销售点击“确认并更新客户”后，才会把意向评分和下一步动作回写客户档案。重新分析会新增版本，不覆盖历史结果。

正式开发 Agent 前，应继续通过应用控制的工作流实现审批、幂等和审计，不让模型直接控制有副作用的工具。

## 设计文档

- [完整技术设计](./docs/technical-design.md)
- [前端设计](./docs/frontend-design.md)
- [数据库迁移](./db/migration/V1__initial_schema.sql)
- [分支与发布规范](./docs/branching-strategy.md)
- [CI/CD 与服务器部署](./docs/deployment.md)
