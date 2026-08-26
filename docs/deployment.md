# CI/CD 与服务器部署

## 发布流程

- Pull Request 到 `develop`、`staging` 或 `main`：运行前后端检查并生成发布包，不部署。
- 推送到 `develop`：运行检查并生成发布包，不部署。
- 推送到 `staging`：构建后自动部署到 GitHub `staging` Environment。
- 推送到 `main`：构建后进入 GitHub `production` Environment；建议配置 Required reviewers，批准后才部署。

构建产物包含后端可执行 JAR、前端静态文件以及服务器部署模板。业务密钥不会写入产物。

## 首次准备服务器

服务器需要 Java 17、Nginx、curl、tar、systemd、`runuser`（通常由 `util-linux` 提供），以及一套可用的 PostgreSQL。以下命令以具备 sudo 权限的管理员执行：

```bash
sudo useradd --system --home /opt/ai-sales-agent --shell /usr/sbin/nologin sales-agent
sudo install -d -m 0755 -o root -g root /opt/ai-sales-agent /opt/ai-sales-agent/releases
sudo install -d -m 0755 -o sales-agent -g sales-agent /opt/ai-sales-agent/logs
sudo install -d -m 0750 -o root -g sales-agent /etc/ai-sales-agent

sudo install -m 0755 deploy/server/deploy-sales-agent.sh /usr/local/sbin/deploy-sales-agent
sudo install -m 0644 deploy/server/ai-sales-agent.service /etc/systemd/system/ai-sales-agent.service
sudo install -m 0644 deploy/server/nginx.conf /etc/nginx/conf.d/ai-sales-agent.conf
sudo install -m 0640 -o root -g sales-agent deploy/server/backend.env.example /etc/ai-sales-agent/backend.env
```

编辑 `/etc/ai-sales-agent/backend.env`，填入真实数据库密码和千问 Key。不要把该文件提交到 Git。

检查并启动服务配置：

```bash
sudo systemctl daemon-reload
sudo systemctl enable ai-sales-agent.service
sudo nginx -t
sudo systemctl reload nginx
```

第一次部署之前，后端服务没有 JAR，暂时无法启动是正常现象。

## 创建专用部署账号

不要让 GitHub Actions 直接使用 root。创建只能通过 SSH 登录和执行指定发布脚本的账号，例如 `sales-deploy`，把 CI 公钥写入该账号的 `authorized_keys`。

先在可信的本地电脑生成一对只用于部署的密钥：

```bash
ssh-keygen -t ed25519 -C "github-actions-ai-sales-agent" -f ai-sales-agent-deploy
```

私钥 `ai-sales-agent-deploy` 的完整内容保存为 GitHub Secret `DEPLOY_SSH_KEY`，不要上传到服务器或提交到仓库。把公钥 `ai-sales-agent-deploy.pub` 的内容安装到服务器：

```bash
sudo useradd --create-home --shell /bin/bash sales-deploy
sudo install -d -m 0700 -o sales-deploy -g sales-deploy /home/sales-deploy/.ssh
sudo install -m 0600 -o sales-deploy -g sales-deploy ai-sales-agent-deploy.pub /home/sales-deploy/.ssh/authorized_keys
```

将 `deploy/server/deploy-sudoers.example` 中的账号名改成实际部署账号，使用 `visudo -cf` 校验后放到 `/etc/sudoers.d/ai-sales-agent-deploy`。该规则只允许以 root 执行 `/usr/local/sbin/deploy-sales-agent`。

## GitHub Environments 与 Secrets

在仓库 Settings → Environments 创建 `staging` 和 `production`。production 建议在仓库套餐支持时配置 Required reviewers，并限制只有 `main` 可以部署。

每个 Environment 配置：

| Secret | 说明 |
| --- | --- |
| `DEPLOY_HOST` | 服务器域名或 IP，例如 `117.72.109.112` |
| `DEPLOY_PORT` | SSH 端口；留空时使用 22 |
| `DEPLOY_USER` | 专用部署账号，例如 `sales-deploy` |
| `DEPLOY_SSH_KEY` | 对应 CI 公钥的 Ed25519 私钥 |
| `DEPLOY_KNOWN_HOSTS` | 经人工核对指纹后的服务器 known_hosts 记录 |

`DEPLOY_KNOWN_HOSTS` 可以在可信网络执行以下命令获取，但必须先在服务器控制台核对显示的主机指纹：

```bash
ssh-keyscan -p 22 117.72.109.112
```

工作流启用了严格主机校验，不使用 `StrictHostKeyChecking=no`。

## Nginx 与 HTTPS

模板默认监听 80 端口，前端由 Nginx 直接提供，`/api/` 和 `/actuator/health` 代理到 `127.0.0.1:8080`。配置域名后应申请证书并启用 HTTPS，同时保持 `AUTH_COOKIE_SECURE=true`。

如果使用宝塔 Nginx，请把 `deploy/server/nginx.conf` 中的 `location` 段复制到对应站点配置，而不是直接覆盖宝塔生成的主配置。

## 发布与回滚

发布包会解压到：

```text
/opt/ai-sales-agent/releases/<commit-sha>
```

部署脚本通过 `/opt/ai-sales-agent/current` 软链接原子切换版本，重启 systemd 后最多检查健康状态 60 秒。检查失败会恢复上一个应用版本，并保留最近 5 个发布目录。

Flyway 数据库迁移不会自动回滚。因此数据库变更必须向后兼容，生产部署前也必须备份数据库；应用回滚不能替代数据库回滚方案。

常用排查命令：

```bash
sudo systemctl status ai-sales-agent --no-pager
sudo journalctl -u ai-sales-agent -n 200 --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
sudo nginx -t
```
