# CI/CD 与服务器部署

## 发布流程

- Pull Request 到 `develop`、`staging` 或 `main`：运行前后端检查并生成发布包，不部署。
- 推送到 `develop`：运行检查并生成发布包，不部署。
- 推送到 `staging`：构建后自动部署到 GitHub `staging` Environment。
- 推送到 `main`：构建后进入 GitHub `production` Environment；建议配置 Required reviewers，批准后才部署。
- 手动运行：选择 `build-only` 时只构建；选择 `staging` 时必须从 `staging` 分支运行；选择 `production` 时必须从 `main` 分支运行。

构建产物包含后端可执行 JAR、前端静态文件以及服务器部署模板。业务密钥不会写入产物。

手动部署生产环境时，在 Actions → CI and release → Run workflow 中将分支选为 `main`，并将 `deploy_environment` 选为 `production`。仅重新运行旧的 `workflow_dispatch` 记录不会获得新增参数，需要从工作流页面发起一次新的运行。

## 首次准备服务器

服务器需要 Java 17、Nginx、curl、tar、systemd，以及一套可用的 PostgreSQL。当前部署方案按要求统一使用 root。以下命令以 root 执行：

```bash
sudo install -d -m 0755 -o root -g root /www/wwwroot/ai.likeasuka.icu /www/wwwroot/ai.likeasuka.icu/releases
sudo install -d -m 0755 -o root -g root /www/wwwroot/ai.likeasuka.icu/logs
sudo install -d -m 0700 -o root -g root /etc/ai-sales-agent

sudo install -m 0755 deploy/server/deploy-sales-agent.sh /usr/local/sbin/deploy-sales-agent
sudo install -m 0644 deploy/server/ai-sales-agent.service /etc/systemd/system/ai-sales-agent.service
sudo install -m 0644 deploy/server/nginx.conf /etc/nginx/conf.d/ai-sales-agent.conf
sudo install -m 0600 -o root -g root deploy/server/backend.env.example /etc/ai-sales-agent/backend.env
```

编辑 `/etc/ai-sales-agent/backend.env`，填入真实数据库密码、模型配置加密密钥和 JWT 签名密钥。不要把该文件提交到 Git。模型 API Key 在系统设置页录入，不写入服务器环境文件。

检查并启动服务配置：

```bash
sudo systemctl daemon-reload
sudo systemctl enable ai-sales-agent.service
sudo nginx -t
sudo systemctl reload nginx
```

第一次部署之前，后端服务没有 JAR，暂时无法启动是正常现象。

## 配置 root 部署密钥

GitHub Actions 通过 root 的 SSH 密钥连接服务器。此方式权限较高，私钥必须只用于本仓库部署，并定期轮换。

先在可信的本地电脑生成一对只用于部署的密钥：

```bash
ssh-keygen -t ed25519 -C "github-actions-ai-sales-agent" -f ai-sales-agent-deploy
```

私钥 `ai-sales-agent-deploy` 的完整内容保存为 GitHub Secret `DEPLOY_SSH_KEY`，不要上传到服务器或提交到仓库。把公钥 `ai-sales-agent-deploy.pub` 的内容安装到服务器 root 账号：

```bash
sudo install -d -m 0700 -o root -g root /root/.ssh
sudo touch /root/.ssh/authorized_keys
sudo chown root:root /root/.ssh/authorized_keys
sudo chmod 0600 /root/.ssh/authorized_keys
sudo nano /root/.ssh/authorized_keys
```

在编辑器中追加 `ai-sales-agent-deploy.pub` 的完整一行；不要覆盖 root 已有的其他登录公钥。

## GitHub Environments 与 Secrets

在仓库 Settings → Environments 创建 `staging` 和 `production`。production 建议在仓库套餐支持时配置 Required reviewers，并限制只有 `main` 可以部署。

每个 Environment 配置：

| Secret | 说明 |
| --- | --- |
| `DEPLOY_HOST` | 服务器域名或 IP，例如 `117.72.109.112` |
| `DEPLOY_PORT` | SSH 端口；留空时使用 22 |
| `DEPLOY_SSH_KEY` | 对应 CI 公钥的 Ed25519 私钥 |
| `DEPLOY_KNOWN_HOSTS` | 经人工核对指纹后的服务器 known_hosts 记录 |

`DEPLOY_KNOWN_HOSTS` 可以在可信网络执行以下命令获取，但必须先在服务器控制台核对显示的主机指纹：

```bash
ssh-keyscan -p 22 117.72.109.112
```

工作流启用了严格主机校验，不使用 `StrictHostKeyChecking=no`。

## Nginx 与 HTTPS

模板默认监听 80 端口，前端由 Nginx 直接提供，`/api/` 和 `/actuator/health` 代理到 `127.0.0.1:8080`。配置域名后应申请证书并强制跳转 HTTPS，避免 Bearer Token 在明文 HTTP 中传输。

如果使用宝塔 Nginx，请把站点运行目录设置为 `/www/wwwroot/ai.likeasuka.icu/current/frontend`，再把 `deploy/server/nginx.conf` 中的 `location` 段复制到对应站点配置；不要直接覆盖宝塔生成的 Nginx 主配置。

## 发布与回滚

发布包会解压到：

```text
/www/wwwroot/ai.likeasuka.icu/releases/<commit-sha>
```

部署脚本通过 `/www/wwwroot/ai.likeasuka.icu/current` 软链接原子切换版本，重启 systemd 后最多检查健康状态 60 秒。检查失败会恢复上一个应用版本，并保留最近 5 个发布目录。

Flyway 数据库迁移不会自动回滚。因此数据库变更必须向后兼容，生产部署前也必须备份数据库；应用回滚不能替代数据库回滚方案。

常用排查命令：

```bash
sudo systemctl status ai-sales-agent --no-pager
sudo journalctl -u ai-sales-agent -n 200 --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
sudo nginx -t
```
