# 桌面端远程更新方案

本文档说明 AI Sales Agent 桌面端应用的远程更新方案。当前桌面端基于 Tauri 构建，更新包发布到 GitHub Releases，客户端通过 Tauri updater 检查、下载、校验并安装新版本。

## 目标

桌面端远程更新要解决三个问题：

1. 用户不用重新去 GitHub 或网站手动下载安装包。
2. 应用可以在启动后自动检查是否存在新版本。
3. 发现新版本后，由用户决定“立即更新”还是“稍后更新”。

推荐的用户体验是：

```text
启动桌面端
        ↓
后台检查 GitHub Releases latest.json
        ↓
没有新版本
        ↓
不显示任何提示

发现新版本
        ↓
顶部显示：发现新版本 0.1.x
        ↓
用户选择：立即更新 / 稍后
        ↓
立即更新后下载更新包并展示进度
        ↓
校验签名
        ↓
安装并重启应用
```

检查失败不应该直接打扰用户。只有下载或安装失败时，才显示“更新异常”。

## 总体链路

完整发布与更新流程如下：

```text
本地修改桌面端代码
        ↓
同步提升桌面端版本号
        ↓
推送代码到 GitHub
        ↓
GitHub Actions 打包 Windows 安装包
        ↓
发布到 GitHub Releases
        ↓
生成 latest.json 更新清单
        ↓
修正 latest.json 中的安装包下载地址
        ↓
桌面端启动时读取 latest.json
        ↓
判断是否有新版本
        ↓
用户确认后下载安装
        ↓
签名校验通过
        ↓
安装并重启
```

## 相关文件

远程更新主要涉及以下文件：

```text
frontend/src-tauri/tauri.conf.json
.github/workflows/desktop-release.yml
frontend/src/desktop/DesktopUpdatePrompt.tsx
frontend/package.json
frontend/src-tauri/Cargo.toml
frontend/src-tauri/Cargo.lock
```

各文件职责如下：

| 文件 | 作用 |
| --- | --- |
| `frontend/src-tauri/tauri.conf.json` | 配置桌面端版本、应用图标、updater 公钥和更新地址 |
| `.github/workflows/desktop-release.yml` | GitHub Actions 桌面端打包与发布流水线 |
| `frontend/src/desktop/DesktopUpdatePrompt.tsx` | 桌面端更新提示、下载进度、安装触发交互 |
| `frontend/package.json` | 前端包版本，需要与桌面端版本同步 |
| `frontend/src-tauri/Cargo.toml` | Tauri/Rust 项目版本 |
| `frontend/src-tauri/Cargo.lock` | 锁定项目自身版本和 Rust 依赖版本 |

## 版本号规则

每次发布桌面端新版本前，必须同步修改以下版本号：

```text
frontend/package.json
frontend/src-tauri/tauri.conf.json
frontend/src-tauri/Cargo.toml
frontend/src-tauri/Cargo.lock
```

例如要发布 `desktop-v0.1.4`，这些地方都应该改成：

```text
0.1.4
```

如果 GitHub Release 发了新包，但桌面端项目版本号没有变，客户端可能会判断“当前已经是最新版本”，从而不会触发更新。

## GitHub Actions 发布方式

当前桌面端发布流水线是：

```text
.github/workflows/desktop-release.yml
```

可以通过两种方式发布。

### 方式一：手动触发

进入 GitHub 仓库：

```text
Actions → Desktop release → Run workflow
```

填写：

```text
release_tag = desktop-v0.1.4
prerelease = false
```

注意：

- `prerelease` 必须为 `false`，否则会发布成“预发行”。
- GitHub 的 `releases/latest` 通常不会把预发行版本当作 latest。
- 如果发布成预发行，桌面端访问 `releases/latest/download/latest.json` 时可能拿不到这个版本。

### 方式二：推送 tag

```bash
git tag desktop-v0.1.4
git push origin desktop-v0.1.4
```

这种方式会触发：

```yaml
on:
  push:
    tags:
      - "desktop-v*"
```

## GitHub Release 产物

发布成功后，GitHub Releases 应该包含：

```text
AI Sales Agent_0.1.4_x64-setup.exe
AI Sales Agent_0.1.4_x64-setup.exe.sig
latest.json
Source code (zip)
Source code (tar.gz)
```

其中：

| 文件 | 作用 |
| --- | --- |
| `.exe` | Windows 安装包 |
| `.exe.sig` | Tauri updater 使用的安装包签名 |
| `latest.json` | 客户端检查更新时读取的更新清单 |

## latest.json 关键字段

`latest.json` 中最重要的是：

```json
{
  "version": "0.1.4",
  "platforms": {
    "windows-x86_64-nsis": {
      "signature": "...",
      "url": "https://github.com/hb1234567898/AI-Sales-Agent-SaaS/releases/download/desktop-v0.1.4/AI%20Sales%20Agent_0.1.4_x64-setup.exe"
    }
  }
}
```

其中：

- `version` 用来判断是否存在新版本。
- `signature` 用来校验下载到的安装包是否可信。
- `url` 是真正的安装包下载地址。

`url` 必须是这种格式：

```text
https://github.com/.../releases/download/<tag>/<installer.exe>
```

不要使用这种格式：

```text
https://api.github.com/repos/.../releases/assets/<asset-id>
```

后者是 GitHub API asset 地址。桌面端直接访问时，可能拿到 JSON、403、限流响应，而不是安装包本体，最终导致 Tauri updater 签名校验失败，表现为“更新异常”。

当前流水线已经增加了 `Patch updater manifest download URL` 步骤，用于发布后自动修正 `latest.json` 里的下载地址。

## 签名密钥

Tauri updater 要求更新包必须签名。

仓库中只应该提交公钥：

```text
frontend/src-tauri/tauri.conf.json
```

私钥必须放在 GitHub Secrets 中，不能提交到仓库：

```text
TAURI_SIGNING_PRIVATE_KEY
```

如果私钥设置了密码，还需要配置：

```text
TAURI_SIGNING_PRIVATE_KEY_PASSWORD
```

注意：

一旦用户安装了带某个 updater 公钥的版本，后续自动更新包必须继续使用对应私钥签名。私钥丢失后，老用户无法通过自动更新升级，只能重新下载安装包。

## 客户端交互设计

推荐把桌面端更新分成四种状态：

| 状态 | UI 表现 |
| --- | --- |
| 检查失败 | 静默忽略，不显示 |
| 已是最新版本 | 不显示 |
| 发现新版本 | 静默进入后台下载，下载期间显示进度 |
| 更新包已下载 | 显示“新版本 x.x.x 已下载”，提供“安装并重启 / 稍后” |
| 下载或安装失败 | 显示“更新异常”，鼠标悬停展示具体错误 |

推荐交互：

```text
新版本 0.1.4 已下载
[稍后] [安装并重启]
```

用户点击“稍后”：

```text
关闭本次提示
下次启动应用再检查
```

发现新版本后：

```text
开始下载
显示下载进度
下载完成后等待用户确认
```

用户点击“安装并重启”：

```text
签名通过后启动安装程序
Windows 会关闭当前应用并执行更新
```

下载中可以显示：

```text
正在下载 32%
```

安装中可以显示：

```text
正在安装更新
```

安装成功可以显示：

```text
更新完成，正在重启
```

## 推荐实现逻辑

客户端伪代码如下：

```ts
async function checkForUpdates() {
  try {
    const update = await check()

    if (!update) {
      return
    }

    showDownloading(update.version)
    await update.download((event) => {
      updateProgress(event)
    })
    showDownloaded(update.version)
  } catch (error) {
    console.warn('桌面端更新检查失败，已静默忽略。', error)
  }
}

async function installUpdate(update) {
  try {
    await update.install()
    await relaunch()
  } catch (error) {
    showUpdateError(error)
  }
}
```

关键原则：

- `check()` 失败不要显示“更新异常”。
- `check()` 成功但没有新版本，不显示任何提示。
- `check()` 成功且发现新版本，后台调用 `download()`，不要调用 `downloadAndInstall()`。
- `download()` 成功后，等待用户点击“安装并重启”再调用 `install()`。
- `download()` 或 `install()` 失败，才显示“更新异常”。

## 常见问题

### 1. 有新版本但显示“更新异常”

优先检查 `latest.json` 里的 `url`。

如果是：

```text
https://api.github.com/repos/.../releases/assets/...
```

就容易失败。

应该修正为：

```text
https://github.com/.../releases/download/<tag>/<installer.exe>
```

### 2. 明明发了版本，但客户端没有提示更新

检查以下几点：

1. 桌面端版本号是否真的提升。
2. GitHub Release 是否是正式版，而不是预发行。
3. `latest.json` 是否能通过浏览器访问。
4. `latest.json` 里的 `version` 是否大于当前安装版本。
5. 客户端内置更新地址是否仍然指向正确仓库。

### 3. 桌面图标更新后还是旧图标

可能有两个原因：

1. Release 是用旧提交打包的，没有包含新 icon。
2. Windows 缓存了旧快捷方式图标。

处理方式：

```text
确认 Release 对应提交包含新 icon
        ↓
卸载旧版
        ↓
重新安装新版
        ↓
必要时删除旧快捷方式或重启资源管理器
```

### 4. 预发行导致 latest.json 找不到

如果 GitHub Release 被发布为预发行，`releases/latest` 可能不会指向它。

解决方式：

- 发布时把 `prerelease` 设为 `false`。
- 或删除错误的预发行版本，重新发布正式 Release。

### 5. 更新签名校验失败

常见原因：

1. `latest.json` 中的签名和安装包不匹配。
2. 使用了错误的 `TAURI_SIGNING_PRIVATE_KEY`。
3. 安装包被重新上传，但 `.sig` 没有同步更新。
4. 客户端内置的公钥和 GitHub Actions 使用的私钥不是一对。

解决方式：

```text
确认 tauri.conf.json 中 pubkey
        ↓
确认 GitHub Secrets 中 TAURI_SIGNING_PRIVATE_KEY
        ↓
重新跑 Desktop release
        ↓
不要手工替换 exe 或 sig 中的单个文件
```

## 推荐发布检查清单

发布前：

- [ ] 版本号已经从 `0.1.x` 提升到新版本。
- [ ] `frontend/package.json` 已更新。
- [ ] `frontend/src-tauri/tauri.conf.json` 已更新。
- [ ] `frontend/src-tauri/Cargo.toml` 已更新。
- [ ] `frontend/src-tauri/Cargo.lock` 中项目自身版本已更新。
- [ ] 代码已合并到 `main`。

发布时：

- [ ] 运行 `Desktop release` workflow。
- [ ] `release_tag` 填写正确，例如 `desktop-v0.1.4`。
- [ ] `prerelease` 为 `false`。
- [ ] GitHub Secrets 中存在 `TAURI_SIGNING_PRIVATE_KEY`。

发布后：

- [ ] Release 中存在 `.exe`。
- [ ] Release 中存在 `.exe.sig`。
- [ ] Release 中存在 `latest.json`。
- [ ] `latest.json` 的 `url` 是 `github.com/.../releases/download/...`。
- [ ] `latest.json` 的 `version` 是最新版本。
- [ ] 旧版本桌面端能检测到新版本。
- [ ] 发现新版本后只下载更新包，不自动安装。
- [ ] 点击“安装并重启”后能完成安装和重启。

## 当前建议

后续建议把当前桌面端更新从“静默下载并安装”调整为“发现新版本后由用户确认安装”。

这样更符合桌面应用习惯，也能避免用户在不知情的情况下触发安装程序。

推荐最终行为：

```text
检查失败：静默
没有新版本：不显示
发现新版本：让用户选择
下载中：显示进度
安装失败：显示明确错误
安装成功：重启应用
```
