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

## 本地打包 Windows 安装包

```bash
cd frontend
$env:VITE_API_BASE_URL="https://ai.likeasuka.icu"
pnpm desktop:build
```

打包产物一般会出现在：

```text
frontend/src-tauri/target/release/bundle/
```

## GitHub Releases 自动更新

桌面端从 `0.1.1` 开始接入 Tauri 官方 updater。默认采用静默更新：有新版本时在顶部状态区显示下载/安装进度，后台完成下载和签名校验；没有新版本时不显示任何更新入口。

```text
推送 desktop-v* tag 或手动触发 Desktop release
        ↓
GitHub Actions 在 windows-latest 打包 NSIS 安装包
        ↓
Tauri 使用私钥生成 .sig 更新签名
        ↓
tauri-action 发布 GitHub Release
        ↓
Release 自动带上 latest.json
        ↓
桌面端启动后读取 latest.json 检查新版本
        ↓
发现新版本后在后台静默下载
        ↓
校验签名、启动安装程序、重启应用
```

### 更新签名密钥

Tauri updater 要求所有更新包必须签名，不能关闭。仓库只提交公钥，私钥必须放在 GitHub Secrets。

当前仓库内置的 updater 公钥来自：

```text
.workbuddy/tauri-updater.key.pub
```

私钥保存在本地，不要提交：

```text
.workbuddy/tauri-updater.key
```

如果需要重新生成密钥，可以执行：

```bash
cd frontend
pnpm tauri signer generate --ci -w ../.workbuddy/tauri-updater.key
```

生成后，把 `.workbuddy/tauri-updater.key.pub` 的内容写入 `frontend/src-tauri/tauri.conf.json` 的 `plugins.updater.pubkey`。

注意：一旦已经有用户安装了带某个公钥的版本，后续版本必须继续用对应私钥签名。丢失私钥后，老用户无法通过自动更新升级，只能重新下载安装包。

### GitHub Secrets

进入 GitHub 仓库：

```text
Settings → Secrets and variables → Actions → New repository secret
```

新增：

```text
TAURI_SIGNING_PRIVATE_KEY
```

值填写 `.workbuddy/tauri-updater.key` 文件的完整内容。

当前密钥生成时没有设置密码，因此这个 Secret 可以不配：

```text
TAURI_SIGNING_PRIVATE_KEY_PASSWORD
```

如果以后重新生成了带密码的私钥，就把密码放到 `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`。

### 发布版本

方式一：推送 tag 自动发布。

```bash
git tag desktop-v0.1.2
git push origin desktop-v0.1.2
```

方式二：GitHub 页面手动触发。

```text
Actions → Desktop release → Run workflow
```

填写：

```text
release_tag = desktop-v0.1.2
prerelease = true / false
```

### 版本号规则

发布前需要同步修改这几个版本号：

```text
frontend/package.json
frontend/src-tauri/Cargo.toml
frontend/src-tauri/tauri.conf.json
```

例如发布 `desktop-v0.1.2` 前，上面三个地方都应改成：

```text
0.1.2
```

否则 GitHub Release 发出去了，桌面端也可能因为版本号没有变而判断“不需要更新”。

### 更新地址

桌面端内置的更新地址是：

```text
https://github.com/hb1234567898/AI-Sales-Agent-SaaS/releases/latest/download/latest.json
```

GitHub Release 中的 `latest.json` 由 `tauri-apps/tauri-action` 自动生成，不需要手写。

## 当前已支持

- 复用 Web 登录和 JWT 双 Token。
- 复用客户管理、互动记录、Agent、审批中心、日志管理等页面。
- MCP 自动化助手聊天历史保存到后端数据库。
- 桌面端可以通过 `VITE_API_BASE_URL` 或登录页“服务地址”指向生产服务器。
- 后端默认允许 Tauri 桌面端、本地 Vite 开发和线上域名的 CORS 请求。
- 桌面端启动后会检查 GitHub Releases 更新，发现新版本后静默下载安装；已经是最新版本时不显示更新提示。

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
5. 增加桌面端更新日志页，展示当前版本和最近一次检查时间。
6. 如果未来需要离线能力，再考虑本地加密缓存，不建议初版就上本地数据库。
