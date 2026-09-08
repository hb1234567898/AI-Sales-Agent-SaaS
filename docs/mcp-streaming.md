# MCP 助手流式输出

## 当前行为

浏览器和 Tauri 桌面端共用 `POST /api/v1/mcp/chat/stream`，使用 SSE 响应。
客户端仍通过 Axios 的 fetch adapter 发送 JWT，支持连接建立前的 401 刷新。
旧 `POST /api/v1/mcp/chat` JSON 接口保留给旧客户端。

一次指令依次执行：保存用户消息 → 推送真实工具状态 → 执行业务 → 推送业务结果和执行摘要 → 千问增量生成回答 → 保存最终消息 → 推送 done。
模型使用已保存的 AI 模型配置，通过 Spring AI `stream().content()` 产生文本增量；前端收到即追加，未设置模拟打字定时器。

这里的“执行过程”来自实际业务步骤，不是模型内部推理原文。
当前客户分析工具仍需等结构化分析完成才能生成审批数据；等待时显示真实的 RUNNING 状态及心跳。
之后会额外调用一次模型，将已核实的业务结果整理为面向用户的流式回答，因此会增加一次模型调用的耗时与用量。
未配置模型时，业务工具仍能使用，直接展示原始结果，不模拟模型流式生成。
工具选择仍沿用现有指令路由，本次没有改成自主工具规划。

## 事件约定

| 事件 | 内容与用途 |
| --- | --- |
| meta | 已创建或验证的 conversationId |
| progress | 当前真实阶段说明 |
| tool | 工具名、RUNNING/SUCCEEDED/FAILED、结果摘要 |
| result | 已核实的业务结果 |
| summary | 执行过程摘要 |
| delta | 模型文本增量 text |
| ping | 每 15 秒维持连接，不显示为业务进度 |
| error | 业务/生成/保存中断的安全提示 |
| done | 数据库提交后的最终消息与会话 ID |

正常结束必须收到 done，不能仅凭 HTTP 200 或连接关闭判定成功。
模型中断会保留已收到的回答、附上已核实业务结果，消息 data.streamStatus 标记 INTERRUPTED；错误消息不会包含模型密钥或原始供应商异常。
若数据库保存失败，流发送 error 并结束，不发送 done。

## 事务与断线

用户消息和最终消息分别在短事务中保存；模型整理回答期间不持有会话写入事务。
工具继续使用各自业务事务。模型回答失败不回滚已成功的工具操作，也不再次执行工具。
客户端断线或离开页面仅中断显示连接，后端已开始的操作继续完成并尝试保存历史；恢复后应查看会话、客户和审批记录再决定是否重试。
客户端不会因断流自动重放 POST，避免重复创建客户或重复审批。
服务端最多同时处理 8 个流式请求；新请求超限时返回 503。连接上限为 10 分钟，模型整理回答最多 2 分钟且连续 45 秒没有增量会降级。

## 宝塔 / Nginx

仓库 `deploy/server/nginx.conf` 已加入 `/api/v1/mcp/chat/stream` 的专用 location。
使用宝塔自定义站点配置时，需要把该 location 同步到实际 HTTPS server 配置，再执行 `nginx -t` 并重载。
关键配置为：

```nginx
proxy_buffering off;
proxy_cache off;
gzip off;
proxy_set_header Accept-Encoding "";
proxy_read_timeout 660s;
```

后端也返回 `X-Accel-Buffering: no`。如果 CDN 或其他代理仍缓冲响应，需要在该链路单独关闭此接口的缓存/缓冲。
前后端需一起部署；已安装的桌面端需包含新前端资源的更新包才能使用新接口。

## 验证

1. 登录后在设置保存可用模型配置，进入 MCP 助手，发送“查看待审批”。
2. 网络面板应显示 POST stream，响应 Content-Type 为 text/event-stream。
3. 请求仍在进行时，应已看到工具状态和首段模型文本；完成后发送按钮恢复。
4. 刷新并打开原会话，最终回答、执行过程、工具轨迹应与完成时一致。
5. 断网测试：已收到内容保留、显示中断提示；恢复后不会自动重新执行原业务请求。

自动化验证包括上游本地 SSE 服务在发送尾段前等待客户端收到首段，确保模型适配器未缓冲完整回答；另覆盖中文拆字节、CRLF 拆包、缺少 done、保存失败和工具不重复执行。

参考：[Spring AI 流式 ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)、[Spring MVC 异步响应](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)。
