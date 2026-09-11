# MCP 助手流式输出技术方案

## 目标

MCP 助手流式输出用于把一次自动化指令拆成可观察的阶段：

1. 先确认登录态和会话。
2. 保存用户输入，避免刷新后丢失指令。
3. 实时展示工具执行状态。
4. 工具执行完成后，流式展示模型整理后的回答。
5. 最终消息保存成功后才发送完成事件。

设计重点不是模拟打字效果，而是让用户在业务执行、模型生成、保存历史这几个阶段都能看到真实进度。

## 前端实现

入口文件：

- `frontend/src/api/mcp-chat-api.ts`
- `frontend/src/api/axios-client.ts`

前端通过 Axios 的 fetch adapter 发起流式请求：

```ts
apiClient.post<ReadableStream<Uint8Array>>('/api/v1/mcp/chat/stream', {
  conversationId,
  message,
  channel: window.__TAURI_INTERNALS__ ? 'DESKTOP' : 'WEB',
}, {
  responseType: 'stream',
  headers: { Accept: 'text/event-stream' },
  timeout: 0,
  signal,
})
```

关键行为：

- 请求方法是 `POST`，响应是 `text/event-stream`。
- 请求开始前复用统一 Axios 鉴权逻辑。
- access token 临近过期时会主动 refresh。
- 建立流之前如果遇到 401，会被动 refresh 并重试一次。
- 一旦开始读取 SSE，不再自动重放业务 `POST`，避免重复创建客户、重复跑 Agent 或重复审批。
- 前端逐帧解析 `event:` 和 `data:`，收到 `delta` 后追加文本。
- 只有收到 `done` 才认为本次请求完整成功。
- 如果连接关闭但没有 `done`，提示用户核对会话和业务记录后再决定是否重试。

## 后端入口

入口文件：

- `backend/src/main/java/com/yourcompany/salesagent/assistant/api/AssistantChatController.java`
- `backend/src/main/java/com/yourcompany/salesagent/assistant/application/AssistantStreamTransport.java`
- `backend/src/main/java/com/yourcompany/salesagent/assistant/application/AssistantChatService.java`

Controller 暴露：

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<SseEmitter> stream(Authentication authentication, @Valid @RequestBody AssistantChatRequest request)
```

响应头：

```text
Content-Type: text/event-stream
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no
```

Transport 当前策略：

- 收到请求后先创建 `SseEmitter` 并返回，让浏览器拿到 `200 text/event-stream`。
- 后台线程再执行会话初始化和业务逻辑。
- 最多同时处理 8 个流式请求。
- 每 15 秒发送一次 `ping` 心跳。
- 流连接最长 10 分钟。
- 初始化或保存失败时，通过 SSE `error` 事件返回可见提示，并在后端日志记录真实异常。

这样做的原因是：如果在返回 `SseEmitter` 之前就执行数据库写入，一旦 MyBatis、SQL 或约束异常，浏览器只能看到空的 HTTP 500，拿不到业务错误。现在前端至少能看到可读错误，后端也有完整堆栈。

## 事件协议

| 事件 | 用途 |
| --- | --- |
| `meta` | 返回 `conversationId`，说明会话已创建或已确认 |
| `progress` | 展示当前真实阶段 |
| `tool` | 展示工具名、状态和摘要 |
| `result` | 返回已核实的结构化业务结果 |
| `summary` | 返回执行过程摘要 |
| `delta` | 返回模型文本增量 |
| `ping` | 心跳事件，前端不展示 |
| `error` | 返回可展示错误，不代表 HTTP 层失败 |
| `done` | 最终消息已保存，整次请求完成 |

正常完成必须满足两个条件：

- HTTP 响应是 `200 text/event-stream`。
- SSE 流内收到了 `done`。

只看到 HTTP 200 不等于业务成功，因为错误也可能通过 SSE `error` 返回。

## 后端执行流程

一次流式请求的后端流程：

1. `AssistantChatController.stream` 校验 JWT 后调用 transport。
2. `AssistantStreamTransport.open` 返回 `SseEmitter`。
3. 后台线程调用 `AssistantChatService.beginStream`。
4. `beginStream` 创建或确认会话，并保存用户消息。
5. 发送 `meta`。
6. `streamChat` 路由用户指令。
7. 业务工具执行时发送 `tool` 事件。
8. 工具执行完成后发送 `result` 和 `summary`。
9. 读取 AI 模型配置。
10. 调用 `QwenModelClient.streamAssistantReply(...)`。
11. 模型增量通过 `delta` 事件发送。
12. 最终助手消息落库。
13. 发送 `done`。

如果模型失败：

- 不回滚已经成功的业务工具。
- 返回工具结果作为兜底内容。
- `data.streamStatus` 标记为 `INTERRUPTED`。
- 不暴露模型供应商原始异常或密钥信息。

MCP 助手触发客户跟进 Agent 时，会写入标准 `agent_run` 记录，`trigger_type` 为 `MCP_ASSISTANT`。Agent 运行页会显示为“由 MCP 助手触发”，运行范围和输入快照中也会保留触发来源。

如果最终保存失败：

- 不发送 `done`。
- 发送 `error`。
- 提醒用户刷新会话并核对业务记录，避免重复提交。

## 鉴权方案

前端每个需要登录的请求都会带：

```text
Authorization: Bearer <accessToken>
X-Sales-Agent-Access-Token: <accessToken>
```

`X-Sales-Agent-Access-Token` 是代理兼容兜底头，避免某些 Nginx 或平台配置吞掉 `Authorization`。

后端 `BearerTokenAuthenticationFilter` 会：

- 优先读取 `Authorization`。
- 其次读取 `X-Sales-Agent-Access-Token`。
- 验证 JWT。
- 将 `AuthPrincipal` 放入 Spring Security Context。
- 写入诊断属性，401 时转成响应头。

401 诊断响应头：

| 响应头 | 含义 |
| --- | --- |
| `X-Sales-Agent-Auth-Token: missing` | 后端没有收到 token |
| `X-Sales-Agent-Auth-Token: invalid` | 后端收到 token，但验签或过期校验失败 |
| `X-Sales-Agent-Auth-Token: accepted` | token 已通过验证 |
| `X-Sales-Agent-Auth-Authorization: true` | 后端收到了 `Authorization` |
| `X-Sales-Agent-Auth-Fallback: true` | 后端收到了兜底 token 头 |

Spring Security 额外放行：

```java
dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
```

原因是 SSE 会触发 Servlet 容器内部 `ASYNC` / `ERROR` 派发。初始请求已经校验 JWT，内部派发不应该被二次鉴权误打成 401。

## Nginx 要求

宝塔或 Nginx 站点配置必须对流式接口关闭缓冲：

```nginx
location ^~ /api/v1/mcp/chat/stream {
    proxy_pass http://127.0.0.1:8080;

    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    gzip off;
    proxy_set_header Accept-Encoding "";
    proxy_read_timeout 660s;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header X-Sales-Agent-Access-Token $http_x_sales_agent_access_token;
}
```

普通 `/api/` 反代也必须透传认证头：

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header X-Sales-Agent-Access-Token $http_x_sales_agent_access_token;
```

修改后执行：

```bash
nginx -t
systemctl reload nginx
```

## 已修复的问题

### 1. 登录后流式接口 401

现象：

- 浏览器带了 `Authorization`。
- 后端仍返回 401。

关键修复：

- JWT filter 放到 `AnonymousAuthenticationFilter` 之前。
- 当前认证为 anonymous 时允许 JWT 覆盖。
- 增加 401 诊断响应头。
- 前端增加请求前主动 refresh 和 401 后被动 refresh。

判断方式：

- `missing`：查 Nginx header 透传。
- `invalid`：查 JWT 密钥、token 过期、服务器时间。
- `accepted` 但仍 401：查 Spring Security 授权规则或内部 dispatcher。

### 2. Token 时间看起来差 8 小时

JWT 和接口返回的时间使用 UTC：

```text
2026-09-09T00:47:04Z
```

`Z` 表示 UTC。换算成北京时间是：

```text
2026-09-09 08:47:04
```

所以这不是时间错误，也不是 UTC+8 导致 token 过期。

### 3. `accepted` 后仍被打成 401

现象：

```text
X-Sales-Agent-Auth-Token: accepted
X-Sales-Agent-Auth-Authorization: true
```

但接口仍返回 401。

原因：

- 初始请求已认证成功。
- SSE 后续 Servlet 内部 `ERROR` 派发再次进入 Security。
- 内部派发没有登录上下文，被误判为未认证。

修复：

```java
dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
```

### 4. 建流前异常导致裸 500

现象：

- 浏览器 Network 只看到 `500 Internal Server Error`。
- 响应体为空。
- 前端无法知道真实错误。

原因：

- 旧实现先执行 `beginStream`，再返回 `SseEmitter`。
- 如果会话初始化或数据库查询失败，HTTP 响应还不是 SSE，只能返回 500。

修复：

- 先返回 `SseEmitter`。
- 后台线程里执行 `beginStream`。
- 异常通过 SSE `error` 发给前端。
- 后端日志记录：

```text
MCP assistant stream failed before completion
```

### 5. FollowUp TODAY 过滤 SQL 类型错误

现象：

```text
ERROR: operator does not exist: timestamp with time zone < interval
```

问题 SQL：

```sql
f.due_at < #{now} + interval '1 day'
```

PostgreSQL 将参数推断错，变成 `timestamptz < interval`。

修复：

```sql
f.due_at < CAST(#{now} AS timestamptz) + interval '1 day'
```

`OVERDUE` 也同步改为：

```sql
f.due_at < CAST(#{now} AS timestamptz)
```

### 6. Assistant record 行映射没有 setter

现象：

```text
There is no setter for property named 'id' in AssistantConversationRow
```

原因：

- `AssistantConversationRow` 和 `AssistantMessageRow` 是 Java `record`。
- XML mapper 使用了 setter 风格 `<result property="id" ...>`。
- record 没有 setter，MyBatis 查询结果无法写入属性。

修复：

- `ConversationMap` 改为 `<constructor>` 映射。
- `MessageMap` 也改为 `<constructor>` 映射。
- 新增 mapper XML 测试，确认两个 resultMap 都使用构造器映射。

## 排障流程

### 浏览器看到 401

1. 看 Response Headers。
2. 如果 `X-Sales-Agent-Auth-Token: missing`，查 Nginx 是否透传 header。
3. 如果 `invalid`，查 JWT 密钥、旧进程、token 是否过期。
4. 如果 `accepted`，查授权规则或 Servlet dispatcher。
5. 如果没有诊断头，说明请求没有到达最新后端，或 401 不是 Spring Boot 返回。

### 浏览器看到 500

1. 看 Network 响应是否是 `text/event-stream`。
2. 如果不是 SSE，说明异常发生在建流前或请求被代理层拦截。
3. 查后端日志 `dispatcherServlet` 和 `AssistantStreamTransport`。
4. 优先看 MyBatis SQL、mapper resultMap、数据库约束异常。
5. 部署最新 transport 后，大多数建流阶段异常应转成 SSE `error`。

### 浏览器看到 200 但页面报错

1. 打开 Network 的 EventStream 面板。
2. 看是否收到 `error`。
3. 看是否缺少 `done`。
4. 如果只有 `ping` 没有业务事件，查后台线程是否阻塞。
5. 如果有 `tool/result` 但没有 `delta`，查模型配置和供应商调用。

## 验证清单

手工验证：

1. 登录后进入 MCP 助手。
2. 发送“查看待审批”。
3. Network 中 `POST /api/v1/mcp/chat/stream` 应为 `200`。
4. Response Header 应包含 `Content-Type: text/event-stream`。
5. EventStream 中应能看到 `progress`、`tool`、`result`、`delta`、`done`。
6. 刷新页面后，历史会话和最终回答仍存在。
7. 断网或刷新时，不应自动重复执行原业务指令。

自动化验证：

```bash
cd backend
./mvnw --batch-mode --no-transfer-progress clean verify
```

当前覆盖点：

- SSE 流式业务结果。
- 模型增量返回。
- 模型失败兜底。
- 保存失败不发送 `done`。
- JWT filter 解析和 anonymous 覆盖。
- Security `ASYNC` / `ERROR` dispatcher 放行。
- FollowUp mapper XML 解析。
- Assistant mapper constructor resultMap。

前端相关验证：

```bash
cd frontend
pnpm build
pnpm test -- src/api/axios-client.test.ts src/api/mcp-chat-api.test.ts
```

覆盖点：

- 请求前主动 refresh。
- 401 后被动 refresh。
- SSE 中文拆字节和 CRLF 拆包。
- 无 `done` 断流。
- 后端 `error` 事件。
- 建流后不自动重放 POST。

## 发布注意事项

- 前后端必须一起部署。
- 宝塔 Java 项目使用的 JAR 必须确认是最新 release 目录。
- 宝塔环境变量要配置在实际运行 Java 进程读取的位置，不要只配置 systemd。
- Nginx 修改后必须 `nginx -t` 并 reload。
- 桌面端 Windows 自动更新需要同步提升 Tauri 版本号，并用 `desktop-v*` tag 触发 release workflow。
- 线上调试不要公开完整 JWT、API Key、数据库密码或签名密钥。
