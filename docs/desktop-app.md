# 桌面端应用设计与启动说明

当前桌面端采用 Tauri 方案，复用 `frontend` 里的 React 页面。桌面端只是客户端外壳，客户、审批、Agent、MCP 聊天历史仍然统一保存在后端和 PostgreSQL 中，避免 Web 端和桌面端各存一套数据。

## 为什么这样设计

- 前端页面复用，减少维护两套 UI 的成本。
- Token、MCP 会话、聊天记录都继续走后端接口，刷新或换设备后历史不丢。
- 桌面端后续可以逐步增加托盘、系统通知、拖拽导入聊天文件、剪贴板导入等能力。

## 本地开发

首次运行前需要安装：

- Node.js 与 pnpm
- Rust 工具链
- Windows WebView2 Runtime

进入前端目录：

```bash
cd frontend
pnpm install
pnpm desktop:dev
```

如果桌面端要直接连线上服务器，可以在启动前配置 API 地址：

```bash
$env:VITE_API_BASE_URL="https://ai.likeasuka.icu"
pnpm desktop:dev
```

如果不配置 `VITE_API_BASE_URL`，开发模式下会使用 Vite 代理，把 `/api` 转发到本机 `http://localhost:8080`。

也可以不设置环境变量，直接在登录页的“服务地址”中填写：

```text
https://ai.likeasuka.icu
```

登录页会把这个地址保存到当前设备，下次启动桌面端会继续使用。

## 打包 Windows 安装包

```bash
cd frontend
$env:VITE_API_BASE_URL="https://ai.likeasuka.icu"
pnpm desktop:build
```

打包产物一般会出现在：

```text
frontend/src-tauri/target/release/bundle/
```

## 当前已支持

- 复用 Web 登录和 JWT 双 Token。
- 复用客户管理、互动记录、Agent、审批中心、日志管理等页面。
- MCP 自动化助手聊天历史保存到后端数据库。
- 桌面端可以通过 `VITE_API_BASE_URL` 或登录页“服务地址”指向生产服务器。
- 后端默认允许 Tauri 桌面端、本地 Vite 开发和线上域名的 CORS 请求。

## 后端 CORS 配置

桌面端请求线上后端时，浏览器内核会带上桌面端自己的 Origin。当前后端默认允许：

```text
http://localhost:*
http://127.0.0.1:*
http://tauri.localhost
tauri://localhost
https://ai.likeasuka.icu
```

如果以后换域名，可以在服务器环境变量中覆盖：

```bash
APP_CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*,http://tauri.localhost,tauri://localhost,https://你的域名
```

## 后续建议

1. 增加桌面端 API 地址配置页，让用户不用设置环境变量。
2. 增加系统托盘和审批提醒。
3. 增加本地文件/剪贴板导入聊天记录。
4. 增加真正的 Agent 运行事件流，例如识别意图、调用工具、等待审批、执行结果。
5. 如果未来需要离线能力，再考虑本地加密缓存，不建议初版就上本地数据库。
