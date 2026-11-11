# 企业知识库（RAG）

## 当前能力

企业知识库采用检索增强生成（RAG），不训练或微调模型。用户上传资料后，系统解析原文、切分片段并建立检索索引；提问时先筛选当前用户有权访问的资料，再把相关片段交给千问回答。回答展示来源、页码（PDF）、片段版本及原文。

第一版是 **PostgreSQL 全文检索 RAG**，不是向量检索。中文按相邻双字切分，英文使用词项；PostgreSQL GIN 索引召回最多 60 个片段，Python 按词项重合度排序，最多将 5 个片段交给模型。没有使用 Embedding、向量数据库或语义重排模型。同义表达、跨语言和抽象问题可能召回不足，后续可增加向量与全文混合检索。

## 职责边界

| 层 | 职责 |
| --- | --- |
| React 前端 | 资料列表、分类筛选、文件/文本提交、原文预览、资料管理、知识问答与引用回链 |
| Java / Spring Boot | 登录、租户与成员权限、原文件持久化、文档与片段存储、全文查询、状态与版本、模型配置和 Token 记账 |
| Python / FastAPI | PDF/DOCX/TXT/Markdown 解析、LangChain 文本切分、中文检索词生成、LangGraph 检索排序与基于资料生成回答 |
| PostgreSQL | 原文件字节、文档元数据、片段、GIN 全文索引、模型调用及用量记录 |
| 千问兼容接口 | 生成最终回答；不负责资料权限或数据库操作 |

浏览器只调用 Java，不直接访问 Python，也不接触模型 API Key。Java 与 Python 沿用相同的内部服务令牌和 HTTP/1.1 通信。

## 使用方式

1. 登录后进入侧边栏「企业知识库」。
2. 点击「新增资料」，选择文件，或切换到「录入文本」。填写标题、分类、团队共享/个人私有。
3. **选择文件不会上传**，只有点击「保存并入库」才上传并解析。
4. 入库成功后可查看原文片段、下载原文件、更新文件、重新解析、停用或删除。
5. 切换「知识问答」提出问题，也可从资料详情选择「基于此资料提问」。
6. MCP 助手提供 `knowledge.query` 工具，并支持 `KNOWLEDGE_QUERY` 计划意图；企业资料问题会走同一权限与检索链路。知识库回答直接展示，不再进行一次无资料的改写。

正式账号的资料库无数据时显示空状态，不自动生成企业制度、产品说明或问答。游客模式单独展示明确标识的本地演示资料和预设问答，不访问真实企业数据或调用模型。

## 文件与解析限制

- 支持 PDF、DOCX、TXT、MD、Markdown；单文件最大 10MB。
- PDF 最多 200 页；不支持加密 PDF。
- 扫描 PDF/图片未提供 OCR，无文本时提示先做 OCR，不会显示为成功入库。
- DOCX 读取正文段落和表格；限制解压后的总大小。
- 文本支持 UTF-8、GB18030；最多 50 万字符、1000 个片段。
- 每个片段约 900 字符，重叠 120 字符。PDF 保留原始页码。
- 解析同步执行；页面保存时显示处理中。失败保留文档记录与错误状态，可重新解析。异常退出留下的处理中记录可在 10 分钟后重试。

## 权限与一致性

- 所有读写必须登录；查询强制限定组织 ID。
- 团队共享资料可被同组织成员检索；个人私有资料只对上传者可见，管理员也不能读取他人的私有资料。
- 上传者以及可见资料的 OWNER/ADMIN 可管理资料，VIEWER 只能读取和提问。
- 修改、停用、删除、替换文件都携带乐观锁版本；文件重新解析递增内容版本。
- 仅 READY 且启用的当前版本参与问答，失败、停用、删除和旧版本片段不参与召回。
- 回答生成后再次核验来源权限和版本，若资料在生成中更新或失去访问权限则抑制原回答，要求重新提问。
- 删除会移除检索片段并将文档标记删除；原始文件字节暂留数据库，不提供已删除文档的下载入口。对象存储、物理清理与保留期策略尚未实现。

## 模型与用量

解析、切分和检索词生成不调用大模型。知识问答检查成员 Token 配额，无分配或额度用完会被拒绝。无匹配片段时不会调用回答模型，直接说明缺少相关资料。

用户在「设置 → AI 模型」配置千问兼容地址、模型与 API Key。Java 在当前内部请求中传递配置，不写入 Python 配置文件。问答调用以 `KNOWLEDGE_QA` 记录输入、输出和总 Token，计入现有用量统计。MCP 的计划调用另外记账。

## 部署

需要同时发布 frontend、backend 和 ai-service；不能只更新前端。

- Java 启动时 Flyway 自动执行 `V16__enterprise_knowledge.sql`。
- Python 新增 `langchain-text-splitters`、`pypdf`、`python-docx` 依赖，需重建离线 wheelhouse 或更新宝塔现有虚拟环境，**不要删除或覆盖虚拟环境**。
- 沿用 `AI_SERVICE_BASE_URL` / `AI_SERVICE_TOKEN`，无需新增 Embedding Key 或向量数据库。
- 反向代理需允许 12MB 请求体（multipart 有额外开销），并为入库/问答保留足够超时；前端这些请求超时为 180 秒，Java → Python 默认 120 秒。

服务器已有离线发布包时，在实际 ai-service 目录运行：

```bash
/www/server/pyporject_evn/ai-services/bin/python -m pip install --no-index --find-links ./wheelhouse -r runtime-requirements.txt
```

源码开发环境：

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m pip install -e ".[test]"
.\.venv\Scripts\python.exe -m pytest -q
```

## 接口

| 方法与路径（统一前缀 `/api/v1/knowledge`） | 用途 |
| --- | --- |
| GET `/documents` | 分页、标题/文件名搜索、分类筛选 |
| POST `/documents` | multipart：metadata JSON 部分 + file |
| POST `/documents/text` | JSON：metadata + content |
| GET `/documents/{id}` | 资料详情 |
| GET `/documents/{id}/chunks` | 分页原文片段 |
| GET `/documents/{id}/file` | 有权限的原文件下载 |
| PUT `/documents/{id}` | 元数据、可见范围与版本 |
| PUT `/documents/{id}/state` | 启停与版本 |
| POST `/documents/{id}/reprocess` | 重新解析与版本 |
| POST `/documents/{id}/replace` | multipart：新 file + version |
| DELETE `/documents/{id}?version=…` | 删除 |
| POST `/query` | JSON：question、可选 documentId |

Python 内部接口为 `/v1/knowledge/ingest`、`/v1/knowledge/terms`、`/v1/knowledge/query`，均要求内部服务令牌。

## 验证与边界

已补充 Python 解析与生成测试、Java 权限/用量/来源核验测试、真实 PostgreSQL 迁移与中文检索测试、前端提交和问答交互测试。真实模型效果仍需使用用户配置的千问密钥和企业资料验收；自动测试以替身模型验证生成协议，不代表千问线上效果。

当前不包含 OCR、语义向量检索、文档问答流式输出、批量入库后台队列、网页采集或知识库专属空间管理。
