# 线上登录态 401 与密钥配置问题复盘

## 基本信息

- 日期：2026-08-27 至 2026-08-28
- 环境：宝塔面板 Java 项目 + Nginx 反向代理 + Spring Boot 后端 + React 前端
- 域名：`https://ai.likeasuka.icu`
- 后端端口：`127.0.0.1:8080`
- 相关分支：`codex/feature/jwt-dual-token-auth`
- 关键修复提交：
  - `6b8f02d fix: support proxy-safe access token header`
  - `61eed15 fix: keep session stable on transient auth failures`
  - `9dffb73 fix: authenticate bearer token before anonymous fallback`

## 问题现象

用户登录后可以进入系统，也可以读取部分页面数据，但执行写操作时频繁失败，例如：

- 保存 AI 模型配置时报 `401 Unauthorized`
- 导入客户聊天记录时报 `401 Unauthorized`
- `/api/v1/auth/refresh` 返回 `200`，但刷新后原请求仍然 `401`
- 页面提示“登录状态已失效，请重新登录”
- 早期还出现过：
  - `服务器尚未配置 AUTH_JWT_SIGNING_KEY`
  - `服务器尚未配置 APP_ENCRYPTION_KEY`
  - `APP_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥`

## 影响范围

- GET 类浏览接口多数不受影响，因为系统允许游客读取 `/api/v1/**` 的 GET 请求。
- PUT/POST/DELETE 写接口受影响，因为写接口必须通过 JWT 认证。
- 受影响功能包括 AI 配置保存、聊天导入、客户资料修改等。

## 最终根因

这次问题不是一个点造成的，而是多个配置和代码问题叠加。

### 1. 宝塔启动方式没有读取 systemd 环境变量

最早登录接口返回：

```text
服务器尚未配置 AUTH_JWT_SIGNING_KEY
```

当时服务器上虽然存在 `/etc/ai-sales-agent/backend.env`，但实际运行后端的是宝塔 Java 项目，不是 `ai-sales-agent.service`。因此 systemd 的 `EnvironmentFile` 对宝塔启动的 Java 进程不生效。

证据：

```text
systemctl show ai-sales-agent.service -p MainPID --value
MainPID=0

ss -lntp | grep ':8080'
users:(("java",pid=...,fd=...))
```

说明 8080 上的 Java 进程不是 systemd 托管的服务。

解决方式是在宝塔 Java 项目里直接配置环境变量：

```text
AUTH_JWT_SIGNING_KEY=...
APP_ENCRYPTION_KEY=...
```

并确保 Java 启动命令参数顺序正确：

```bash
/www/server/java/jdk-17.0.8/bin/java -Xmx1024M -Xms256M -jar /www/wwwroot/ai.likeasuka.icu/current/backend/sales-agent.jar --server.port=8080
```

### 2. Nginx 反向代理最初没有透传认证 Header

浏览器 DevTools 里可以看到请求带了：

```text
authorization: Bearer <access-token>
x-sales-agent-access-token: <access-token>
```

但后端诊断响应头曾显示：

```text
X-Sales-Agent-Auth-Token: missing
X-Sales-Agent-Auth-Authorization: false
X-Sales-Agent-Auth-Fallback: false
```

这说明浏览器发给 Nginx 的 Header 没有完整转发到 Spring Boot。

解决方式是在宝塔站点 Nginx 配置的 `/api/` 反代段增加：

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header X-Sales-Agent-Access-Token $http_x_sales_agent_access_token;
```

完整示例：

```nginx
location ^~ /api/ {
    proxy_pass http://127.0.0.1:8080;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    proxy_set_header Authorization $http_authorization;
    proxy_set_header X-Sales-Agent-Access-Token $http_x_sales_agent_access_token;
}
```

保存后执行：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 3. 真正导致“Header 到了但仍 401”的后端 Bug：JWT Filter 顺序错误

在修复 Nginx Header 后，响应头变成：

```text
X-Sales-Agent-Auth-Authorization: true
X-Sales-Agent-Auth-Fallback: true
X-Sales-Agent-Auth-Token: null
```

这个结果非常关键：

- `Authorization=true`：后端已经收到 `Authorization` Header
- `Fallback=true`：后端也收到 `X-Sales-Agent-Access-Token`
- `Token=null`：JWT 过滤器没有进入解析 token 的逻辑

代码中原来的过滤器注册位置是：

```java
.addFilterBefore(bearerTokenFilter, AuthorizationFilter.class);
```

这个位置太靠后。在 Spring Security 链路里，`AnonymousAuthenticationFilter` 可能已经先放入了 anonymous 认证对象。JWT 过滤器原逻辑只在 `SecurityContextHolder.getContext().getAuthentication() == null` 时才解析 token。于是它看到上下文里已经有 anonymous，就误以为“不需要解析”，直接跳过。

结果就是：请求明明带了 token，后端也收到了，但没有把它转换成真实登录用户，最终写接口被判定未认证。

最终修复：

```java
.addFilterBefore(bearerTokenFilter, AnonymousAuthenticationFilter.class);
```

并且让过滤器在当前认证对象是 anonymous 时也继续解析 JWT：

```java
private static boolean shouldResolveToken(Authentication authentication) {
    return authentication == null || authentication instanceof AnonymousAuthenticationToken;
}
```

对应提交：

```text
9dffb73 fix: authenticate bearer token before anonymous fallback
```

### 4. 前端对 401 的处理过于激进，放大了问题

原逻辑中，业务接口返回 401 后会触发 refresh；如果后续 `/api/v1/auth/session` 也返回 401，前端会清空 token 并跳转登录页。

在线上后端认证链路异常时，这会造成用户体感上的“修改东西就直接退出登录”。

修复后：

- 只有 refresh token 明确失效时才清理本地 token
- `/auth/session` 短暂 401 不再立即踢用户回登录页
- 业务接口 401 不再直接清空登录态

对应提交：

```text
61eed15 fix: keep session stable on transient auth failures
```

### 5. APP_ENCRYPTION_KEY 与 AUTH_JWT_SIGNING_KEY 的长度规则不同

`AUTH_JWT_SIGNING_KEY` 用于 JWT HMAC 签名，后端要求 Base64 解码后至少 32 字节。

`APP_ENCRYPTION_KEY` 用于 AES-GCM 加密模型 API Key，后端要求 Base64 解码后刚好 32 字节。

宝塔环境变量输入框可能会吞掉 Base64 末尾的 `=` padding，因此推荐生成不带 `=` 的 Base64：

```bash
openssl rand -base64 32 | tr -d '\n='
```

然后配置：

```text
APP_ENCRYPTION_KEY=生成结果
```

验证解码长度：

```bash
APP_ENCRYPTION_KEY='生成结果' python3 -c "import os,base64; k=os.environ['APP_ENCRYPTION_KEY']; k += '=' * (-len(k) % 4); print(len(base64.b64decode(k)))"
```

输出必须是：

```text
32
```

`AUTH_JWT_SIGNING_KEY` 可以使用：

```bash
openssl rand -base64 48 | tr -d '\n='
```

## 排查过程

### 阶段一：以为是数据库或后端没有启动

早期后端部署阶段曾因 Java 版本、数据库角色、Flyway、PostgreSQL 扩展等问题无法启动。逐步修复后，后端可以监听 8080，但登录接口仍报 503。

关键判断：

- `curl 127.0.0.1:8080` 失败时，先查 Java 进程是否存在
- systemd `MainPID=0`，但 8080 有 Java 进程，说明实际由宝塔启动
- 因此 systemd 环境文件不是当前进程的真实配置来源

### 阶段二：确认 AUTH_JWT_SIGNING_KEY 没进入运行进程

虽然文件里有：

```text
AUTH_JWT_SIGNING_KEY=<已配置>
```

但后端仍报：

```text
服务器尚未配置 AUTH_JWT_SIGNING_KEY
```

这说明配置文件存在不等于运行进程已读取。最终改为在宝塔 Java 项目环境变量里配置。

### 阶段三：切换为 JWT 双 Token 后，写接口仍 401

前端已经从 Cookie Session 改为：

- `Authorization: Bearer <access-token>`
- `X-Sales-Agent-Access-Token: <access-token>`

并且 `/api/v1/auth/refresh` 能返回新 token。但写接口仍然 401。

这说明 refresh token 有效，但 access token 没有被后端识别。

### 阶段四：通过诊断响应头定位 Header 是否到达后端

后端新增了三个诊断响应头：

```text
X-Sales-Agent-Auth-Token
X-Sales-Agent-Auth-Authorization
X-Sales-Agent-Auth-Fallback
```

用于判断：

```text
missing / false / false：Header 没到后端，查 Nginx
invalid / true / true：Header 到了，但 token 验签失败，查密钥
accepted / true / true：认证成功，若仍 401 则查授权规则
null / true / true：Filter 顺序或执行路径异常
```

最终线上出现 `null / true / true`，定位到 Spring Security filter 顺序问题。

### 阶段五：修复后端过滤器顺序

将 JWT filter 从 `AuthorizationFilter` 前调整到 `AnonymousAuthenticationFilter` 前，并允许替换 anonymous 认证。

新增测试覆盖：

- fallback header 可认证
- 无 token 时标记 missing
- token 无法解析时标记 invalid
- 已存在 anonymous 认证时，JWT 能覆盖为真实用户

验证结果：

```text
后端测试：28 passed
```

### 阶段六：降低前端误踢登录的概率

为了避免后端短暂异常导致用户直接退出，前端调整为：

- refresh 401 才清理 token
- session 401 但本地 token 仍存在时，不直接跳登录
- 写接口 401 不再直接触发强制退出

验证结果：

```text
前端测试：16 passed
前端 lint：通过
前端 build：通过
```

## 正确部署步骤

1. 部署最新后端 JAR，分支必须包含：

```text
9dffb73 fix: authenticate bearer token before anonymous fallback
```

2. 宝塔 Java 项目环境变量至少包含：

```text
AUTH_JWT_SIGNING_KEY=Base64随机密钥
APP_ENCRYPTION_KEY=Base64解码后刚好32字节的密钥
```

3. 宝塔 Java 启动命令使用：

```bash
/www/server/java/jdk-17.0.8/bin/java -Xmx1024M -Xms256M -jar /www/wwwroot/ai.likeasuka.icu/current/backend/sales-agent.jar --server.port=8080
```

4. 宝塔 Nginx `/api/` 反向代理必须透传：

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header X-Sales-Agent-Access-Token $http_x_sales_agent_access_token;
```

5. 重载 Nginx，重启宝塔 Java 项目。

6. 浏览器退出登录后重新登录，避免继续使用旧 token。

## 验证方式

在浏览器 DevTools 中执行写操作，例如保存 AI 配置或导入聊天。

如果仍然 401，查看该红色请求的 Response Headers。

正确结果应该是：

```text
X-Sales-Agent-Auth-Token: accepted
X-Sales-Agent-Auth-Authorization: true
```

如果看到：

```text
X-Sales-Agent-Auth-Token: missing
```

继续查 Nginx Header 转发。

如果看到：

```text
X-Sales-Agent-Auth-Token: invalid
```

继续查 `AUTH_JWT_SIGNING_KEY` 是否和签发 token 时一致，或者是否还有旧 Java 进程未停止。

如果看不到这三个诊断响应头，说明当前线上后端 JAR 不是最新版本，或者 401 不是 Spring Boot 返回的。

## 非根因说明

### 服务器在新加坡不是原因

新加坡和中国都是 UTC+8。并且 JWT 的 `iat`、`exp` 使用 Unix 时间戳，后端用 `Instant` 判断过期，不依赖服务器显示时区。

因此“服务器在国外导致时间对不上”不是这次 401 的根因。

### 浏览器没有发 token 不是原因

用户截图中的 Request Headers 已经明确包含：

```text
authorization: Bearer <access-token>
x-sales-agent-access-token: <access-token>
```

所以浏览器发 token 没问题。问题发生在 Nginx 到后端的转发阶段，以及后端 Spring Security filter 执行阶段。

## 预防措施

- CI 中保留 `BearerTokenAuthenticationFilterTests`，尤其是 anonymous 覆盖用例。
- 保留短期诊断响应头，直到线上认证稳定后再考虑移除。
- 宝塔部署方式与 systemd 部署方式需要二选一，不要混用。
- 如果继续使用宝塔 Java 项目，部署文档应单独补一节“宝塔启动与环境变量配置”。
- 密钥类环境变量不要写入 JAR，也不要提交到 Git；统一由运行环境注入。
- `APP_ENCRYPTION_KEY` 一旦用于加密线上 API Key，不要随意更换，否则已保存的密文无法解密。
- 线上调试时不要在聊天或截图里暴露完整 JWT、API Key、数据库密码等敏感信息。

