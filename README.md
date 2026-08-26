# AI Sales Follow-up Agent

面向中小销售团队的 AI 跟进工作台。当前仓库包含可运行的后端、前端、PostgreSQL Compose 和数据库迁移骨架。

## 目录

```text
.
├── backend/       Spring Boot 4.1 + 可选 Spring AI 2.0
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

## 验证

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
pnpm lint
pnpm test
pnpm build
```

## Spring AI

默认构建不加载模型供应商，因此没有 API Key 也可以启动。需要 OpenAI 模型时使用 Maven Profile：

```powershell
$env:OPENAI_API_KEY = "your-key"
.\mvnw.cmd -Pai-openai spring-boot:run
```

正式开发 Agent 前，应继续通过应用控制的工作流实现审批、幂等和审计，不让模型直接控制有副作用的工具。

## 设计文档

- [完整技术设计](./docs/technical-design.md)
- [前端设计](./docs/frontend-design.md)
- [数据库迁移](./db/migration/V1__initial_schema.sql)
- [分支与发布规范](./docs/branching-strategy.md)
- [CI/CD 与服务器部署](./docs/deployment.md)
