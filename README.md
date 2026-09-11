# AI Sales Follow-up Agent

面向中小销售团队的 AI 跟进工作台。当前仓库包含可运行的后端、前端、PostgreSQL Compose 和数据库迁移骨架。

## 目录

```text
.
├── backend/       Spring Boot 4.1 + MyBatis-Plus + 可选 Spring AI 2.0
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

## Spring AI / 通义千问

后端已通过 Spring AI 的 OpenAI 兼容适配器接入阿里云百炼。默认关闭模型客户端，因此没有 API Key 时客户管理等功能仍可启动。PowerShell 中配置千问：

```powershell
$env:AI_CHAT_PROVIDER = "openai"
$env:QWEN_API_KEY = "sk-your-bailian-key"
$env:QWEN_MODEL = "qwen-plus"
.\mvnw.cmd spring-boot:run
```

这里的 `openai` 表示使用 OpenAI 兼容协议，不是调用 OpenAI 模型。默认百炼地址为 `https://dashscope.aliyuncs.com/compatible-mode/v1`，可通过 `QWEN_BASE_URL` 覆盖。启动后在“设置 → AI 模型”查看状态并点击“测试连接”。

客户详情的“互动记录”支持粘贴微信、WhatsApp 等聊天原文。导入后点击单条记录上的“AI 分析”，系统会生成需求、痛点、异议、风险、意向评分、分析依据和建议动作。分析结果默认处于待确认状态；只有销售点击“确认并更新客户”后，才会把意向评分和下一步动作回写客户档案。重新分析会新增版本，不覆盖历史结果。

正式开发 Agent 前，应继续通过应用控制的工作流实现审批、幂等和审计，不让模型直接控制有副作用的工具。

## 设计文档

- [完整技术设计](./docs/technical-design.md)
- [前端设计](./docs/frontend-design.md)
- [数据库迁移](./db/migration/V1__initial_schema.sql)
- [分支与发布规范](./docs/branching-strategy.md)
- [CI/CD 与服务器部署](./docs/deployment.md)
