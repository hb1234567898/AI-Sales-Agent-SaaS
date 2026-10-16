# AI Service

FastAPI + LangChain 模型编排服务。Spring Boot 仍是唯一业务入口，负责认证、租户、CRM、审批、工具执行和用量入库；本服务只接收 Spring Boot 已核实的业务结果并调用模型。

当前能力：

- `/v1/chat/stream`：对 Java 已核实的业务结果生成流式说明。
- `/v1/chat/analyze`：销售聊天结构化分析。
- `/v1/agent/plan`：LangGraph 生成并校验候选工具计划，不执行工具。
- `/v1/knowledge/query`：对 Java 提供的租户隔离候选知识片段进行检索增强问答。

知识库候选片段必须由 Java 根据当前组织和用户权限筛选后传入；Python 不直接读取 CRM 数据库。

## 本地运行

```bash
python -m venv .venv
.venv/Scripts/pip install -e ".[test]"
.venv/Scripts/uvicorn app.main:app --host 127.0.0.1 --port 8090
```

配置 `AI_SERVICE_TOKEN` 后，除 `/health` 外的接口必须携带 `Authorization: Bearer <token>`。模型 API Key 由 Spring Boot 按组织配置逐次传入，不在 Python 服务中保存。

## 目录结构

```text
app/
├── main.py                    # 创建 FastAPI 应用，只挂载总路由
├── api/
│   ├── router.py              # 汇总所有 feature 路由
│   └── health.py              # 健康检查
├── core/
│   ├── config.py              # 环境变量和服务配置
│   └── security.py            # Java -> Python 内部令牌校验
├── llm/
│   ├── factory.py             # LangChain 模型实例创建
│   ├── messages.py            # 模型文本、Token 和请求元数据解析
│   └── schemas.py             # 模型配置及公共响应协议
└── features/
    ├── model/                 # 模型连接测试
    ├── chat/                  # 流式回答、聊天分析及提示词
    ├── agent/                 # LangGraph 工具计划与风险校验
    └── knowledge/             # 知识检索、排序和增强回答
```

每个 feature 自己维护 `router.py`、`schemas.py` 和 `service.py` 或 `workflow.py`。HTTP 参数只放在对应 feature 的 Schema 中；供应商适配和模型响应解析放在 `llm/`；跨业务配置和鉴权放在 `core/`。新增能力时优先新增 feature，不再把业务代码堆进 `main.py`。
