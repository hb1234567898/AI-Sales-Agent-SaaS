# JWT 双 Token 认证设计

## 1. 设计目标

当前项目已经从 Cookie Session 切换为 JWT 双 Token 认证：

- `Access Token`：短期访问令牌，用于调用业务接口。
- `Refresh Token`：长期刷新令牌，用于在 Access Token 过期后续期。

这个设计的目标是让 Web、未来的 Windows 客户端、移动端都可以使用同一套认证方式，不再依赖浏览器 Cookie。同时保留后端主动注销、会话过期、Refresh Token 轮换等安全能力。

相关代码：

- `backend/src/main/java/com/yourcompany/salesagent/auth/api/AuthController.java`
- `backend/src/main/java/com/yourcompany/salesagent/auth/application/AuthService.java`
- `backend/src/main/java/com/yourcompany/salesagent/auth/security/JwtTokenService.java`
- `backend/src/main/java/com/yourcompany/salesagent/auth/security/BearerTokenAuthenticationFilter.java`
- `backend/src/main/java/com/yourcompany/salesagent/auth/security/SecurityConfiguration.java`
- `frontend/src/api/http-client.ts`
- `frontend/src/auth/auth-token-storage.ts`
- `frontend/src/api/auth-api.ts`

## 2. 登录流程

前端调用：

```http
POST /api/v1/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "email": "chen.mo@demo.local",
  "password": "Demo@123456",
  "rememberMe": false
}
```

后端处理流程：

1. 根据邮箱查询本地账号、组织、组织成员信息。
2. 使用 BCrypt 校验密码。
3. 校验失败时累计 `failed_attempts`，达到 5 次后锁定 15 分钟。
4. 校验成功后创建新的 `sessionId`。
5. 签发一对新的 `Access Token` 和 `Refresh Token`。
6. 将 Refresh Token 的 SHA-256 哈希写入 `auth_session.token_hash`。
7. 返回 token 和当前登录用户信息。

响应结构：

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "accessTokenExpiresAt": "2026-08-28T01:15:00Z",
  "refreshToken": "...",
  "refreshTokenExpiresAt": "2026-09-27T00:58:49Z",
  "session": {
    "userId": "...",
    "memberId": "...",
    "organizationId": "...",
    "email": "chen.mo@demo.local",
    "displayName": "陈默",
    "organizationName": "演示销售团队",
    "role": "SALES",
    "expiresAt": "2026-09-27T00:58:49Z"
  }
}
```

## 3. Token 存储

前端根据 `rememberMe` 决定保存位置：

| rememberMe | 存储位置 | 生命周期 |
| --- | --- | --- |
| `false` | `sessionStorage` | 浏览器会话关闭后清理 |
| `true` | `localStorage` | 用户主动退出或 Refresh Token 失效前保留 |

存储 key：

```text
sales-agent:auth-tokens
```

保存内容：

```ts
interface AuthTokens {
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
}
```

## 4. Access Token

Access Token 是短期 JWT，默认有效期：

```text
AUTH_ACCESS_TOKEN_DURATION=PT15M
```

后端签发时会取两者中更早的时间作为过期时间：

- `issuedAt + accessTokenDuration`
- 当前登录会话的 `expiresAt`

Access Token 载荷包含：

| 字段 | 说明 |
| --- | --- |
| `iss` | 签发方，默认 `ai-sales-agent` |
| `sub` | 用户 ID |
| `sid` | 会话 ID |
| `oid` | 组织 ID |
| `mid` | 组织成员 ID |
| `email` | 用户邮箱 |
| `name` | 用户显示名 |
| `org` | 组织名称 |
| `role` | 用户角色 |
| `session_exp` | 当前登录会话最终过期时间 |
| `typ` | 固定为 `access` |
| `iat` | 签发时间 |
| `exp` | Access Token 过期时间 |
| `jti` | Token 唯一 ID |

Access Token 当前是无状态校验：接口请求时不查数据库，只校验 JWT 签名、签发方、类型和过期时间。

优点是性能好，缺点是用户退出后，旧 Access Token 在自身过期前仍可能通过签名校验。因此当前系统通过较短的 15 分钟有效期降低风险。

## 5. Refresh Token

Refresh Token 也是 JWT，但用途只允许刷新，不允许访问业务接口。

Refresh Token 载荷包含：

| 字段 | 说明 |
| --- | --- |
| `iss` | 签发方 |
| `sub` | 用户 ID |
| `sid` | 会话 ID |
| `typ` | 固定为 `refresh` |
| `iat` | 签发时间 |
| `exp` | Refresh Token 过期时间 |
| `jti` | Token 唯一 ID |

Refresh Token 生命周期由登录方式决定：

```text
AUTH_SESSION_DURATION=PT12H
AUTH_REMEMBER_DURATION=P30D
```

也就是：

- 不勾选记住我：默认 12 小时。
- 勾选记住我：默认 30 天。

后端不会把 Refresh Token 明文存数据库，而是存 SHA-256 哈希：

```text
auth_session.token_hash = sha256(refreshToken)
```

这样即使数据库泄露，也无法直接拿到可用 Refresh Token。

## 6. 自动刷新流程

前端所有业务请求都经过 `requestJson`。

请求发出前会自动添加：

```http
Authorization: Bearer <accessToken>
X-Sales-Agent-Access-Token: <accessToken>
```

其中 `X-Sales-Agent-Access-Token` 是为了兼容部分 Nginx/宝塔配置吞掉 `Authorization` 的场景。

当业务接口返回 `401` 时：

1. 前端检查本地是否存在 Refresh Token。
2. 如果存在，调用：

```http
POST /api/v1/auth/refresh
```

3. 后端校验 Refresh Token。
4. 后端签发新的 Access Token 和 Refresh Token。
5. 前端保存新的双 Token。
6. 前端自动重试原请求一次。
7. 如果 Refresh Token 明确返回 `401`，前端清理本地 token 并触发重新登录。

前端使用全局 `refreshPromise` 合并并发刷新请求，避免多个接口同时 401 时重复刷新。

## 7. Refresh Token 轮换

项目当前实现的是旋转 Refresh Token。

每次刷新成功后：

- 旧 Refresh Token 失效。
- 新 Refresh Token 生效。
- 数据库中的 `auth_session.token_hash` 被替换为新 Refresh Token 的哈希。

核心 SQL：

```sql
UPDATE auth_session
SET token_hash = #{newTokenHash},
    last_seen_at = #{now}
WHERE id = #{sessionId}
  AND token_hash = #{currentTokenHash}
  AND expires_at > #{now}
  AND revoked_at IS NULL
```

这个条件更新有两个作用：

- 确保只有当前有效 Refresh Token 才能轮换。
- 如果旧 Refresh Token 被重复使用，更新行数为 0，会被判定为无效刷新。

## 8. 后端认证过滤器

后端通过 `BearerTokenAuthenticationFilter` 从请求头读取 Access Token。

读取优先级：

1. `Authorization: Bearer <token>`
2. `X-Sales-Agent-Access-Token: <token>`

读取成功后，过滤器调用：

```java
authService.resolveAccessToken(token)
```

解析成功后，将 JWT 中的用户信息封装为 `AuthPrincipal`，再写入 Spring Security 上下文：

```java
var authentication = new UsernamePasswordAuthenticationToken(
    principal,
    null,
    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
);
SecurityContextHolder.setContext(context);
```

当前过滤器必须放在 `AnonymousAuthenticationFilter` 之前：

```java
.addFilterBefore(bearerTokenFilter, AnonymousAuthenticationFilter.class)
```

并且当上下文中已经存在 anonymous 认证时，也允许继续解析 JWT：

```java
private static boolean shouldResolveToken(Authentication authentication) {
    return authentication == null || authentication instanceof AnonymousAuthenticationToken;
}
```

这是线上 401 问题的关键修复点。如果过滤器执行太晚，Spring Security 可能已经放入 anonymous 用户，导致 JWT 不被解析。

## 9. 接口权限规则

当前 `SecurityConfiguration` 中的权限规则：

| 路径 | 权限 |
| --- | --- |
| `/api/v1/auth/login` | 放行 |
| `/api/v1/auth/refresh` | 放行 |
| `/actuator/health` | 放行 |
| `/actuator/info` | 放行 |
| `/api/v1/auth/session` | 必须登录 |
| `GET /api/v1/**` | 放行，支持游客浏览 |
| 其他请求 | 必须登录 |

这也是为什么系统里经常出现：

- 页面数据能看。
- 保存、导入、修改会 401。

因为 GET 默认允许游客读取，而 POST/PUT/DELETE 必须带有效 JWT。

## 10. 登出流程

前端调用：

```http
POST /api/v1/auth/logout
```

后端根据当前 Access Token 中的 `sessionId`，撤销数据库会话：

```sql
UPDATE auth_session
SET revoked_at = #{now}
WHERE id = #{sessionId}
  AND revoked_at IS NULL
```

前端无论后端是否成功，都会清理本地 token。

需要注意：当前 Access Token 是无状态的。登出后，Refresh Token 会立即失效，但旧 Access Token 在短时间内仍可能有效，直到它自身过期。

如果后续需要“立即踢下线”，可以考虑：

- Access Token 解析后额外查询 `auth_session.revoked_at`。
- 或者增加 Access Token blacklist。
- 或者缩短 Access Token 有效期。

## 11. 密钥配置

JWT 签名使用：

```text
AUTH_JWT_SIGNING_KEY
```

要求：

```text
Base64 解码后至少 32 字节
```

推荐生成：

```bash
openssl rand -base64 48 | tr -d '\n='
```

AI API Key 加密使用：

```text
APP_ENCRYPTION_KEY
```

要求：

```text
Base64 解码后刚好 32 字节
```

推荐生成：

```bash
openssl rand -base64 32 | tr -d '\n='
```

验证 `APP_ENCRYPTION_KEY` 长度：

```bash
APP_ENCRYPTION_KEY='生成结果' python3 -c "import os,base64; k=os.environ['APP_ENCRYPTION_KEY']; k += '=' * (-len(k) % 4); print(len(base64.b64decode(k)))"
```

输出必须是：

```text
32
```

注意：这两个密钥不能混用。`AUTH_JWT_SIGNING_KEY` 用于 JWT HMAC 签名，`APP_ENCRYPTION_KEY` 用于 AES-GCM 加密模型 API Key。

## 12. Nginx 配置要求

如果通过 Nginx 或宝塔反向代理 `/api/`，必须透传认证 Header：

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header X-Sales-Agent-Access-Token $http_x_sales_agent_access_token;
```

否则浏览器虽然发出了 token，但 Spring Boot 后端可能收不到，最终写接口会返回 `401 Unauthorized`。

## 13. 诊断响应头

为了排查线上 401，后端当前在认证失败响应中加入了三个诊断头：

```text
X-Sales-Agent-Auth-Token
X-Sales-Agent-Auth-Authorization
X-Sales-Agent-Auth-Fallback
```

含义：

| 响应头 | 含义 |
| --- | --- |
| `X-Sales-Agent-Auth-Authorization` | 后端是否收到 `Authorization` Header |
| `X-Sales-Agent-Auth-Fallback` | 后端是否收到 `X-Sales-Agent-Access-Token` Header |
| `X-Sales-Agent-Auth-Token` | token 解析状态 |

`X-Sales-Agent-Auth-Token` 常见值：

| 值 | 含义 |
| --- | --- |
| `missing` | 后端没有收到可用 token |
| `invalid` | 后端收到 token，但 JWT 校验失败 |
| `accepted` | token 已被后端接受 |
| `null` | 过滤器没有进入解析分支，通常是过滤器顺序问题或非预期链路 |

线上稳定后，可以考虑用配置开关控制这些诊断头，避免生产环境长期暴露内部状态。

## 14. 当前实现优点

- 不依赖 Cookie，便于 Web、桌面端、移动端共用。
- Access Token 短期有效，业务接口认证无需查库。
- Refresh Token 只保存哈希，数据库不保存明文令牌。
- Refresh Token 支持旋转，旧 token 被重复使用时会失效。
- 支持主动登出和会话撤销。
- 前端可自动刷新并重试原请求，用户体验平滑。
- 同时发送标准 `Authorization` 和兜底 Header，适配宝塔/Nginx 反代。

## 15. 当前限制与后续增强

当前仍有一些可以增强的点：

- Access Token 无状态，登出后不能做到立刻失效。
- Refresh Token 重放攻击目前只会刷新失败，后续可以增加安全审计日志。
- 可以增加“登录设备管理”页面，查看和撤销指定会话。
- 诊断响应头后续应增加开关，避免生产长期暴露。
- 移动端或 Windows 客户端不应使用普通 localStorage，应使用系统安全存储。
- 可以增加密钥轮换方案，但要注意旧 token 和已加密 API Key 的兼容问题。

