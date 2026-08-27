# AI Sales Follow-up Agent 前端设计

> 状态：MVP 产品与工程基线
> 对应后端设计：[`technical-design.md`](./technical-design.md)
> 核心目标：让销售在 10 秒内知道今天该跟进谁，并安全完成下一步动作

## 1. 设计判定

这是面向 3-30 人销售团队的 B2B SaaS 工作台，不是聊天产品，也不是营销网站。

```text
DESIGN_VARIANCE: 3
MOTION_INTENSITY: 2
VISUAL_DENSITY: 7
```

- 低变化度：保证页面结构稳定，减少销售每天重复使用时的认知成本。
- 低动效：仅用动效表达状态变化、反馈和层级，不做装饰动画。
- 中高密度：桌面端需要同时看到客户、原因、下一步和状态，但不堆叠成监控大屏。

视觉语言使用 **Fluent UI React v9**。它适合企业工作台，具有成熟的可访问组件、Token 和数据密集型模式。项目只使用一个组件设计系统，不混入 Material、Carbon 或 shadcn/ui。Fluent UI React v9 由 React、TypeScript 和 Griffel 构成，官方要求在根节点使用 `FluentProvider` 和主题。参考：[Fluent 2 开发指南](https://fluent2.microsoft.design/get-started/develop)。

## 2. 前端成功标准

前端不是把后端表逐张显示出来，而是完成五件事：

1. **聚焦**：Today 首页只告诉销售今天最值得处理的事情。
2. **解释**：每个建议都展示依据、时间和影响因素。
3. **执行**：生成草稿、审批、发送或创建任务形成连续操作流。
4. **守门**：高风险动作在执行前展示精确内容并要求明确确认。
5. **追踪**：用户能随时看到 Agent 正在做什么、哪里失败、如何恢复。

MVP 体验指标：

| 指标 | 目标 |
|---|---|
| 首次进入 Today 找到第一条建议 | 10 秒内 |
| 从建议到生成邮件草稿 | 3 次操作内 |
| 审批页确认收件人、主题、正文 | 无需跳页 |
| Agent 运行状态可见性 | 状态变化 2 秒内显示 |
| 关键页面键盘可完成 | Today、Approvals、Customers |
| 误发邮件 | 前端交互不提供绕过审批的路径 |

## 3. 技术栈

### 3.1 基线选择

| 能力 | 选择 | 用途 |
|---|---|---|
| 应用框架 | React + TypeScript | 单页业务应用 |
| 构建 | Vite | 开发、构建、代码分割 |
| 路由 | React Router Data Mode | 嵌套路由、错误边界、按路由懒加载 |
| 组件系统 | `@fluentui/react-components` | UI、主题、焦点和可访问性 |
| 图标 | `@fluentui/react-icons` | 保持单一图标体系 |
| 服务端状态 | TanStack Query | 请求缓存、失效、重试、分页 |
| 表单 | React Hook Form + Zod | 表单状态与运行时校验 |
| API 契约 | OpenAPI 生成 TypeScript 类型 | 减少前后端 DTO 漂移 |
| 实时事件 | 原生 EventSource | Agent Run SSE 订阅与回放 |
| 单元测试 | Vitest + React Testing Library | 组件和状态逻辑 |
| API Mock | MSW | 开发和测试的接口模拟 |
| E2E | Playwright | 关键业务闭环 |
| 可访问测试 | axe-core | 自动发现常见 WCAG 问题 |

Vite 当前提供 React TypeScript 模板和生产构建能力，适合与独立 Spring Boot API 配合。参考：[Vite 官方指南](https://vite.dev/guide/)。React Router Data Mode 支持嵌套路由、loader、action、错误边界和懒加载；TanStack Query 使用结构化 query key 管理缓存。参考：[React Router Data Mode](https://reactrouter.com/start/data/routing)、[TanStack Query Keys](https://tanstack.com/query/latest/docs/framework/react/guides/query-keys)。

### 3.2 为什么不选 Next.js

MVP 是登录后的业务工作台，SEO 和服务端页面渲染价值很低。Vite SPA 与 Spring Boot 分工更简单：

- Spring Boot 负责认证、授权、API、SSE 和业务状态。
- React 负责应用路由、交互和展示。
- 生产环境通过同一个域名反向代理，避免 CORS 和 EventSource 认证问题。

如果后续需要营销官网，可单独使用 Next.js，不和业务工作台共享运行时。

## 4. 信息架构

### 4.1 主导航

```text
Today
Customers
Follow-ups
Approvals
Agent runs
Analytics

Settings
  Integrations
  Agent settings
  Members
  Organization
  Usage & billing
```

导航原则：

- `Today` 是登录后默认页。
- `Approvals` 显示待审批数量，只有真实待办才出现 Badge。
- `Agent runs` 是可解释性和故障处理入口，不叫 Logs。
- `Integrations` 放在 Settings，连接异常时在主导航底部出现一条可操作警告。
- 管理员不可见的页面从路由和导航同时移除，不能只做前端隐藏。

### 4.2 路由树

```text
/
├── /login
├── /auth/callback
├── /onboarding
│   ├── /organization
│   ├── /data-source
│   ├── /import
│   └── /first-run
└── /app
    ├── /today
    ├── /customers
    │   └── /:customerId
    ├── /follow-ups
    │   └── /:followUpId
    ├── /approvals
    │   └── /:approvalId
    ├── /agent-runs
    │   └── /:runId
    ├── /analytics
    └── /settings
        ├── /integrations
        ├── /agent
        ├── /members
        ├── /organization
        └── /billing
```

列表选择状态进入 URL。例如 `/approvals/:approvalId` 对应右侧详情面板，刷新和复制链接后仍能恢复上下文。

## 5. App Shell

### 5.1 桌面结构

```text
┌──────────────────────────────────────────────────────────────────┐
│ Organization ▾       Search customers...        Help   Profile  │ 56
├───────────────┬──────────────────────────────────────────────────┤
│ Today         │                                                  │
│ Customers     │                  Route content                   │
│ Follow-ups    │                                                  │
│ Approvals  4  │                                                  │
│ Agent runs    │                                                  │
│ Analytics     │                                                  │
│               │                                                  │
│ Settings      │                                                  │
└───────────────┴──────────────────────────────────────────────────┘
      224px                         fluid
```

- 顶栏高度 56px，左侧栏宽度 224px。
- 主内容最大宽度 1440px；数据表页面可以铺满可用区域。
- 页面内边距：桌面 24px，宽屏 32px，移动端 16px。
- 左侧栏可折叠到 56px，但不自动折叠，避免布局突然变化。
- 全局搜索只搜索客户、联系人和商机，不承担命令面板职责。

### 5.2 移动结构

MVP 以桌面优先，但必须支持销售在手机上查看和审批：

- `< 768px` 隐藏侧栏，使用顶部菜单按钮打开 Drawer。
- Today 变成单列队列，详情使用全屏子路由。
- Approvals 详情使用全屏页面，不使用窄侧栏。
- DataGrid 在手机上转换为摘要行，不横向塞入所有列。
- Analytics 只展示核心指标，不复制桌面全部图表。
- 邮件正文、收件人和发送按钮必须在 360px 宽度下可读可操作。

## 6. 视觉系统

### 6.1 主题

- 默认跟随操作系统浅色或深色偏好。
- 用户可以在 Profile 中选择 `System / Light / Dark`。
- 整个应用一次只使用一个主题，不在页面内部随意反转背景。
- 使用 Fluent `FluentProvider` 定义组织品牌主题，组件不得硬编码颜色。

### 6.2 色彩

主品牌色建议使用克制的深青色，避免通用 AI 紫色：

```text
Brand primary       #006F6A
Brand hover         由 Fluent brand ramp 生成
Light canvas        #F7F9F8
Light surface       #FFFFFF
Dark canvas         #171918
Dark surface        #202321
```

状态色是语义例外，不计入品牌强调色：

```text
Success     已完成、已发送
Warning     待审批、临近到期
Danger      失败、高风险、可能流失
Neutral     草稿、跳过、已取消
```

不得只用颜色表达状态，必须同时提供文字、图标或形状信息。

### 6.3 字体与数字

- 字体使用 Fluent 默认系统字体栈，Windows 优先 Segoe UI。
- 页面标题 28/36，Semibold。
- 区块标题 20/28，Semibold。
- 正文 14/20，Regular。
- 辅助信息 12/16，Regular。
- 金额、优先级、时间和计数启用 tabular numbers。
- 任何正文不小于 12px；主要操作不使用全大写。

### 6.4 间距与形状

使用 4px 基础网格：4、8、12、16、20、24、32、40。

形状规则：

- 输入框和按钮遵循 Fluent 默认圆角。
- 页面面板使用 8px 圆角。
- Tag 和 Badge 才使用全圆角。
- 数据行不套卡片，通过背景、分隔线和 hover 表达层级。
- 阴影只用于 Dialog、Drawer、Popover 和悬浮层，不给所有区块加阴影。

### 6.5 图标

- 只使用 `@fluentui/react-icons`。
- 常规尺寸 20px，紧凑表格 16px，空状态 32px。
- 图标按钮必须有可访问名称和 Tooltip。
- 不用 Emoji 充当导航和状态图标。

## 7. 页面设计

### 7.1 Today

### 页面目标

回答三个问题：今天有多少事情、最先处理谁、Agent 的分析是否新鲜。

### 桌面布局

```text
┌ Today                                      Last analyzed 08:04 ┐
│ Good morning, Lin                         [Run analysis]       │
├───────────────────────────────────────────────────────────────┤
│ High priority 8   At risk 4   Due today 13   Near close 3     │
├───────────────────────────────┬───────────────────────────────┤
│ Priority queue                │ Selected customer             │
│                               │                               │
│ 92  星河智造      Proposal    │ 星河智造                      │
│     5 days without follow-up  │ Deal CNY 120,000              │
│     Opened quote 3 times      │                               │
│                               │ Why this is recommended       │
│ 87  北辰数据      Demo        │ - Asked about private deploy  │
│     Meeting follow-up due     │ - No reply for 5 days         │
│                               │                               │
│                               │ Next action                   │
│                               │ Send case study and book demo │
│                               │                               │
│                               │ [Generate email] [Snooze]     │
└───────────────────────────────┴───────────────────────────────┘
```

### 交互规则

- 指标是可点击过滤器，不做四张漂浮卡片。
- 队列按最终 `priority` 降序，逾期时间作为第二排序项。
- 选择一行只更新详情和 URL，不导航离开 Today。
- 详情必须展示 evidence，不能只显示“AI 认为优先级高”。
- `Generate email` 创建草稿动作，不直接发送。
- `Snooze` 打开轻量 Popover，提供明天、三天后、下周和自定义日期。
- `Dismiss` 放在更多菜单，并要求选择理由，为后续推荐评测提供反馈。
- `Run analysis` 返回 202 后立刻显示运行条，不能等任务完成才给反馈。

### 状态

| 状态 | 页面表现 |
|---|---|
| 初次加载 | 结构相同的 Skeleton，不显示全屏 Spinner |
| 无客户数据 | 引导导入客户，主操作 `Import customers` |
| 有客户但无建议 | 展示“今天没有需要跟进的客户”和最近分析时间 |
| 分析进行中 | 顶部运行条显示已处理数量，旧队列仍可使用 |
| 数据过期 | 显示 warning banner 和 `Run analysis` |
| 部分失败 | 队列可用，顶部显示失败数量及 Run 详情链接 |

### 7.2 Customers

### 列表

使用 Fluent DataGrid 或语义等价的服务端分页表格：

```text
Search | Stage | Owner | Last interaction | Saved view

Customer       Stage       Owner      Deal value     Last activity    Next follow-up
星河智造        Proposal    Lin        ¥120,000       5 days ago       Today
北辰数据        Demo        Chen      ¥80,000        Yesterday        Tomorrow
```

默认列：Customer、Stage、Owner、Deal Value、Last Activity、Next Follow-up、Priority。

规则：

- 搜索、过滤、排序和游标写入 URL search params。
- 默认服务端分页，不一次拉取全部客户。
- 列配置存用户偏好，不存本地敏感数据。
- 行点击进入客户详情；行内按钮只有常用动作，避免操作列堆满图标。
- 手机端显示 Customer、Stage、Next Follow-up，其余进入详情。
- 批量操作只支持 Assign owner、Archive 和 Run analysis；批量发送邮件不进入 MVP。

### 客户详情

```text
┌ 星河智造                         Proposal   AI priority 92 ┐
│ Owner Lin   Deal ¥120,000   Last activity Aug 14         │
├──────────────────────────────────────┬─────────────────────┤
│ Overview | Timeline | Follow-ups     │ AI brief            │
│                                      │ Intent: High        │
│ Aug 14  Quote email sent             │ Risk: Medium        │
│ Aug 15  Email opened                 │                     │
│ Aug 17  Email opened                 │ Evidence            │
│ Aug 18  Follow-up recommended        │ ...                 │
│                                      │ [Generate email]    │
└──────────────────────────────────────┴─────────────────────┘
```

- Timeline 以日期分组，默认倒序。
- 邮件正文默认折叠，只显示 preview，避免页面过长和意外暴露敏感信息。
- AI brief 明确显示分析时间和 Prompt/schema 版本的用户友好标识。
- “客户当前阶段”与“AI 建议阶段”不一致时显示差异，不自动覆盖 CRM。
- CRM 更新动作进入 ActionRequest，按策略决定审批。

### 7.3 Follow-ups

Follow-ups 是跨客户的工作队列，不是 Today 的复制。

视图：

- `Open`：全部未完成建议。
- `Overdue`：已逾期。
- `Snoozed`：暂缓。
- `Completed`：历史完成记录。
- `Dismissed`：被拒绝的建议，用于反馈分析。

列表列：Priority、Customer、Reason、Recommended action、Due、Owner、Status。

支持操作：开始处理、生成草稿、Snooze、Complete、Dismiss。`Complete` 要求选择结果，例如 Sent manually、Called、Meeting booked、No longer relevant。

### 7.4 Approvals

### 页面目标

让审批人清楚知道“将对谁执行什么动作，为什么，最终内容是什么”，并防止误操作。

### 桌面布局

```text
┌ Approvals (4) ────────────────────────────────────────────┐
│ Pending | Approved | Rejected | Expired                   │
├────────────────────────┬──────────────────────────────────┤
│ 星河智造               │ Send email                 HIGH │
│ Send follow-up email   │                                  │
│ Requested 12 min ago   │ To: qin@example.com              │
│                        │ Subject: 私有部署案例与技术沟通   │
│ 北辰数据               │                                  │
│ Create CRM task        │ [完整邮件正文]                   │
│ Requested 31 min ago   │                                  │
│                        │ Why AI suggested this             │
│                        │ Evidence list                     │
│                        │                                  │
│                        │ [Reject]           [Approve]     │
└────────────────────────┴──────────────────────────────────┘
```

### 安全交互

- 收件人、主题、正文、附件、客户、发送邮箱全部在同一面板显示。
- 高风险动作使用明确的 `Approve and send`，不用含糊的 `Confirm`。
- Reject 打开 Dialog，理由可选但提供预设选项。
- Approve 不做乐观更新。按钮进入 pending，等待服务端返回最终 Approval 状态。
- 请求携带 `expectedVersion` 和 `Idempotency-Key`。
- 服务端返回 409 时重新获取内容，并提示“该审批已被其他人处理”。
- 内容哈希变化、Action 过期、邮箱失效时禁用批准并显示原因。
- 批准成功只说明“已批准并进入执行队列”。邮件真正发送后再显示 Sent。
- 发送结果通过 SSE 或查询刷新更新，不能把批准与发送混成同一个状态。

### 键盘

- `J/K` 在审批列表移动。
- `Enter` 打开当前项。
- 不为 Approve 和 Reject 设置单键快捷键，避免误触。
- Dialog 打开后焦点锁定，关闭后回到原列表项。

### 7.5 Agent Runs

### 列表

列：Started、Trigger、Status、Processed、Follow-ups found、Pending approvals、Duration、Initiated by。

状态显示必须与后端一致：

```text
CREATED
QUEUED
RUNNING
WAITING_APPROVAL
COMPLETED
PARTIALLY_COMPLETED
FAILED
CANCELLED
```

用户文案：

| 后端状态 | 前端文案 |
|---|---|
| CREATED | Created |
| QUEUED | Queued |
| RUNNING | Running |
| WAITING_APPROVAL | Waiting for approval |
| COMPLETED | Completed |
| PARTIALLY_COMPLETED | Completed with issues |
| FAILED | Failed |
| CANCELLED | Cancelled |

### Run 详情

```text
Run summary
Status Running   312 / 428 customers   Started 08:01

[progress with text and counts]

08:01:03  Loaded 428 customers                       Complete
08:01:08  Pre-filtered 116 candidates                Complete
08:01:12  Analyzed 星河智造                           Complete
08:01:14  Proposed follow-up email                    Waiting approval
08:01:16  Analyzed 北辰数据                            Complete
08:01:19  Model call failed, retrying                 Warning
```

- 顶部显示服务端 counters，不从 Step 数量推算。
- Timeline 使用 `agent_run_event.sequence_no`，确保 SSE 和 REST 顺序一致。
- 默认只显示业务级步骤，Debug 模式才显示模型和工具技术细节。
- 错误条目显示 Retryable、错误分类和建议操作，不直接暴露堆栈。
- `Cancel run` 只在非终态可见，需要确认 Dialog。
- `Retry failed items` 创建新的子 Run，不修改原 Run 历史。

### 7.6 Integrations

离散连接适合使用面板卡片：

```text
Email
Microsoft Outlook
Connected as sales@company.com
Last synced 6 minutes ago
[Manage]

Customer data
CSV import
428 customers, imported Aug 18
[Import update]
```

- MVP 只突出一个支持的邮箱供应商和 CSV 导入。
- 未实现的集成不显示“Coming soon”网格，避免制造虚假产品范围。
- `REAUTH_REQUIRED` 在所有页面显示可操作 Banner。
- Disconnect 说明影响，默认不删除已同步的业务历史。
- OAuth 使用新窗口或整页跳转；回调后回到原 Settings 页面。

### 7.7 Analytics

Analytics 面向销售老板，不显示 Token 作为主指标。

第一屏指标：

- Follow-ups found
- Suggestions accepted
- Customer replies
- Meetings booked
- Pipeline influenced
- Time saved estimate

第二层分析：按日期、销售、销售阶段、建议动作拆分。模型成本和 Token 放在 `Usage & billing`，只对 Owner/Admin 可见。

数据不足时显示采集说明，不画伪造趋势线。所有百分比显示样本数和统计周期。

### 7.8 Onboarding

Onboarding 只包含四步：

```text
Create organization
Connect or import customer data
Configure follow-up rules
Run first analysis
```

- 每一步有明确完成条件，可返回修改。
- CSV 导入先预览列映射和错误行，再提交。
- 首次分析显示真实 Agent Run 进度。
- 完成后进入 Today，并选中第一条建议。
- 邮箱连接可以跳过，但生成邮件时会再次引导。

## 8. 核心组件

### 8.1 应用级组件

```text
AppShell
├── OrganizationSwitcher
├── PrimaryNavigation
├── GlobalSearch
├── IntegrationHealthBanner
├── RouteProgress
└── RouteErrorBoundary
```

### 8.2 业务组件

```text
TodayPage
├── AnalysisFreshness
├── MetricStrip
├── FollowUpQueue
│   └── FollowUpRow
└── FollowUpInspector
    ├── CustomerSummary
    ├── EvidenceList
    └── RecommendedActionPanel

ApprovalPage
├── ApprovalInbox
│   └── ApprovalRow
└── ApprovalReviewPanel
    ├── ActionHeader
    ├── EmailPreview | CrmChangePreview | TaskPreview
    ├── EvidenceList
    ├── PolicyNotice
    └── ApprovalActionBar

AgentRunPage
├── RunSummary
├── RunProgress
├── RunEventTimeline
└── RunRecoveryActions
```

### 8.3 组件边界原则

- Fluent 基础组件不再包装成同名 Button/Input。
- 只封装带产品语义的组件，例如 `ApprovalActionBar`、`PriorityBadge`。
- 页面组件不直接调用 `fetch`，通过 feature query/mutation hooks。
- 组件 props 使用 API DTO 的最小视图模型，避免把整个 Customer 对象层层传递。
- Empty、Loading、Error 是每个主要区域的显式状态，不是页面最后补上的分支。

## 9. React 工程结构

```text
frontend/
├── src/
│   ├── app/
│   │   ├── App.tsx
│   │   ├── router.tsx
│   │   ├── providers.tsx
│   │   ├── query-client.ts
│   │   └── theme.ts
│   ├── api/
│   │   ├── generated/
│   │   ├── http-client.ts
│   │   ├── errors.ts
│   │   ├── idempotency.ts
│   │   └── sse-client.ts
│   ├── features/
│   │   ├── auth/
│   │   ├── today/
│   │   ├── customers/
│   │   ├── follow-ups/
│   │   ├── approvals/
│   │   ├── agent-runs/
│   │   ├── integrations/
│   │   ├── analytics/
│   │   └── settings/
│   ├── components/
│   │   ├── layout/
│   │   ├── feedback/
│   │   └── data-display/
│   ├── hooks/
│   ├── lib/
│   ├── test/
│   ├── main.tsx
│   └── index.css
├── public/
├── e2e/
├── package.json
├── tsconfig.json
└── vite.config.ts
```

Feature 目录内部结构：

```text
features/approvals/
├── api/
│   ├── approval-queries.ts
│   └── approval-mutations.ts
├── components/
├── pages/
├── model/
│   ├── approval-view-model.ts
│   └── approval-permissions.ts
└── index.ts
```

禁止 feature 之间深路径互相导入。跨 feature 能力从各自 `index.ts` 暴露，或下沉到明确的 shared 组件。

## 10. 类型与 API 契约

### 10.1 前端核心类型

```ts
export type AgentRunStatus =
  | 'CREATED'
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_APPROVAL'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type ActionStatus =
  | 'PROPOSED'
  | 'AWAITING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED';

export type ApprovalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELLED';

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
```

这些联合类型必须由 OpenAPI 生成或由生成类型再导出，不能在多个 feature 手写副本。

### 10.2 API 错误

后端统一返回 Problem Details 风格：

```ts
export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  code: string;
  traceId?: string;
  fieldErrors?: Record<string, string[]>;
}
```

前端处理：

| HTTP 状态 | 行为 |
|---|---|
| 400/422 | 表单字段错误就地显示 |
| 401 | 跳转登录并保存返回地址 |
| 403 | 展示无权限页，不泄露资源细节 |
| 404 | 资源不存在或当前组织不可见 |
| 409 | 刷新资源并解释并发变化 |
| 429 | 显示重试时间，不自动轰炸重试 |
| 5xx | 保留现有数据，显示可重试区域错误 |

## 11. 服务端状态管理

### 11.1 Query key

所有 key 必须含组织 ID：

```ts
export const queryKeys = {
  dashboard: (orgId: string, date: string) =>
    ['org', orgId, 'dashboard', { date }] as const,
  followUps: (orgId: string, filters: FollowUpFilters) =>
    ['org', orgId, 'follow-ups', filters] as const,
  approval: (orgId: string, id: string) =>
    ['org', orgId, 'approvals', id] as const,
  agentRun: (orgId: string, id: string) =>
    ['org', orgId, 'agent-runs', id] as const,
};
```

切换组织时：

1. 取消旧组织未完成请求。
2. 关闭旧组织 SSE。
3. 清除或隔离旧组织缓存。
4. 导航到新组织 Today。
5. 重新检查权限和集成健康状态。

### 11.2 缓存策略

| 数据 | staleTime 建议 | 刷新方式 |
|---|---:|---|
| Today dashboard | 30 秒 | 窗口聚焦 + Run 事件 |
| Customer detail | 60 秒 | 相关 mutation 后失效 |
| Customer timeline | 30 秒 | 发送/同步成功后失效 |
| Pending approvals | 10 秒 | SSE + 定时兜底 |
| Agent run detail | 运行中 0 秒，终态 5 分钟 | SSE + 终态后停止 |
| Integrations | 30 秒 | OAuth callback 后失效 |
| Analytics | 5 分钟 | 手动日期过滤 |

### 11.3 Mutation 原则

- Approve、Reject、Send、Cancel 不使用乐观更新。
- Snooze 和本地队列排序可乐观更新，失败后回滚。
- 每个产生副作用的 POST 都携带 UUID `Idempotency-Key`。
- mutation 成功后精确更新或失效相关 query，不调用全局 `invalidateQueries()`。
- 浏览器离线时不自动排队高风险 mutation。
- 页面卸载不取消已经由服务端接收的业务命令，只停止前端等待。

## 12. SSE 设计

### 12.1 认证选择

系统认证统一使用 JWT Bearer Token。原生 EventSource 不能方便地添加 Authorization header，因此实时流采用 `fetch()` 流式读取并携带 Access Token，或者由后端签发一次性短期 SSE ticket；禁止把 Access Token 或 Refresh Token 放入 URL。

### 12.2 客户端状态机

```text
DISCONNECTED
  -> CONNECTING
  -> OPEN
  -> RECONNECTING
  -> OPEN
  -> CLOSED (run terminal or component unmounted)
```

事件处理规则：

1. 保存最后处理的 `sequence`。
2. 忽略小于等于当前 sequence 的重复事件。
3. 如果收到的 sequence 大于当前值 + 1，立即 REST 回补缺失事件。
4. SSE 只增量更新 UI，定期以 Run REST 快照校准 counters/status。
5. Run 进入终态后关闭连接并最终刷新一次 Run、Today、Approvals。
6. 指数退避重连，上限 30 秒；浏览器 offline 时暂停。

### 12.3 Hook 接口

```ts
interface UseAgentRunStreamResult {
  connectionState: 'connecting' | 'open' | 'reconnecting' | 'closed';
  lastSequence: number;
  gapDetected: boolean;
}

function useAgentRunStream(
  organizationId: string,
  runId: string,
  enabled: boolean,
): UseAgentRunStreamResult;
```

Hook 只负责连接和写入 Query cache，不直接渲染 Toast。

## 13. 表单与确认

### 13.1 表单结构

- Label 始终位于输入框上方。
- Placeholder 不能代替 Label。
- Helper text 位于输入框下方，Error 替换 helper 或紧邻显示。
- 提交失败保留用户输入。
- 服务端字段错误映射到具体字段，未知错误显示表单级消息。

### 13.2 Dialog 规则

Dialog 只用于：

- 不可逆或高影响操作。
- 需要用户在当前上下文做简短决策。
- 删除连接、取消 Run、拒绝审批。

不使用 Dialog 展示长客户详情或复杂表单。长内容使用 Drawer 或独立路由。

### 13.3 Toast 规则

Toast 只用于短暂、非关键反馈：

- 草稿已保存。
- Follow-up 已 Snooze。
- 设置已更新。

审批失败、发送失败、权限问题必须在资源上下文内持续显示，不能只闪过 Toast。

## 14. 权限驱动 UI

前端权限用于减少无效操作，不是安全边界。服务端仍须逐请求校验。

```ts
type Permission =
  | 'CUSTOMER_READ'
  | 'CUSTOMER_WRITE'
  | 'AGENT_RUN'
  | 'AGENT_CANCEL'
  | 'ACTION_APPROVE'
  | 'INTEGRATION_MANAGE'
  | 'MEMBER_MANAGE'
  | 'BILLING_READ';
```

- 无权限的主要操作直接隐藏。
- 用户理解可能需要但当前不可用的操作显示 disabled，并解释所需角色。
- 后端返回 403 后立即刷新 session/permissions，防止角色刚被变更。
- 不能根据角色字符串散落判断，统一使用 `can(permission, resource?)`。

## 15. 可访问性

目标为 WCAG 2.2 AA。

### 15.1 键盘和焦点

- 所有操作使用原生 button/link 语义。
- 路由切换后焦点移动到页面 H1，并播报页面标题。
- DataGrid 支持方向键、Enter 和 Space，不在一个单元格放多个无标签按钮。
- Drawer/Dialog 使用焦点圈闭，关闭后恢复触发元素。
- 跳过导航链接在 Tab 首次聚焦时出现。
- 自定义快捷键不能覆盖浏览器或辅助技术常用按键。

### 15.2 屏幕阅读器

- Run 进度使用 `aria-live="polite"`，按阶段播报，不为每条 SSE 事件刷屏。
- 高风险和错误消息使用清晰文本，必要时 `role="alert"`。
- Priority 92 的可访问名称应为“Priority 92 out of 100”。
- 图表必须有文本摘要或数据表替代。
- Badge 不能成为唯一信息来源。

### 15.3 视觉

- 正文和控件达到 WCAG AA 对比度。
- Focus indicator 在浅色和深色模式均可见。
- 200% 缩放仍能完成 Today 和 Approval 流程。
- 不依赖 hover 才能发现关键操作。
- `prefers-reduced-motion` 下禁用非必要过渡。

## 16. 响应式策略

| 宽度 | 布局 |
|---|---|
| `< 480px` | 单列，列表摘要，详情独立页 |
| `480-767px` | 单列，稍宽表单和面板 |
| `768-1023px` | Drawer 导航，列表/详情切换 |
| `1024-1439px` | 固定侧栏，主内容双栏 |
| `>= 1440px` | 224px 侧栏，Today 45/55 分栏 |

移动端必须完成：

- 查看 Today 队列。
- 查看客户建议和 evidence。
- 审批或拒绝邮件。
- 查看 Run 当前状态。
- Snooze Follow-up。

复杂客户批量编辑、列配置和完整 Analytics 可以桌面优先。

## 17. 性能

### 17.1 目标

| 指标 | 目标 |
|---|---|
| 首次加载 JS gzip | MVP 尽量小于 300 KB，不含按路由懒加载块 |
| LCP | 小于 2.5 秒 |
| INP | 小于 200 ms |
| CLS | 小于 0.1 |
| 页面切换可见反馈 | 100 ms 内 |

### 17.2 策略

- route-level lazy import：Analytics、Settings、Agent Run Detail 单独分包。
- Fluent 组件使用命名导入，检查构建产物是否正确 tree-shake。
- 表格使用服务端游标分页；单页超过 200 行再引入虚拟化。
- Timeline 正文按需加载，首屏只取 preview。
- 搜索输入 debounce 250-300ms，并取消旧请求。
- 不在全局 Store 保存大型列表副本。
- 不为低动效工作台引入 Motion 或 GSAP。
- 生产构建生成 source map 时上传到错误平台，不公开暴露源码。

## 18. 前端安全

- API 不依赖认证 Cookie，Access Token 只通过 `Authorization: Bearer` 发送；所有生产流量强制 HTTPS。
- Access Token 默认 15 分钟有效，Refresh Token 每次使用后轮换，退出登录时撤销服务端刷新会话。
- “记住我”把双 Token 保存到 localStorage，否则保存到 sessionStorage。由于 Token 可被 JavaScript 读取，必须使用严格 CSP、禁止不可信脚本并把 XSS 防护列为上线阻断项。
- 不使用 `dangerouslySetInnerHTML` 渲染邮件正文。
- 邮件 HTML 在隔离 sandbox iframe 中显示；默认禁止脚本、表单和顶级导航。
- 外链添加安全属性并清晰标识离开应用。
- CSV 预览将以 `= + - @` 开头的内容按普通文本处理，防止公式注入传播。
- 除认证模块管理的双 Token 外，不把 Customer、Email、Prompt 或 Token 写入 localStorage、analytics payload 或前端错误日志。
- URL 中只放资源 ID 和过滤条件，不放邮件正文、联系人邮箱等敏感字段。
- 组织切换必须销毁旧组织缓存和 SSE。
- CSP 至少限制 script、frame、connect、img 来源；邮件预览使用更严格独立策略。

## 19. 产品分析事件

只记录产品行为，不记录客户敏感内容：

```text
today_viewed
follow_up_selected
follow_up_snoozed
follow_up_dismissed
email_draft_requested
approval_opened
approval_approved
approval_rejected
agent_run_started
agent_run_detail_viewed
integration_connect_started
integration_connected
```

通用属性：organization plan、user role、surface、result、duration bucket。禁止记录 customer name、email body、recipient 或 model prompt。

漏斗：

```text
Suggestion viewed
 -> Action generated
 -> Action approved
 -> Action succeeded
 -> Customer replied
 -> Meeting booked
```

## 20. 测试策略

### 20.1 单元与组件测试

- 后端状态到文案、颜色、可用动作的完整映射。
- Priority、金额、时区和相对时间格式化。
- Approval 按钮在 PENDING、过期、哈希变化、无权限时的行为。
- SSE 去重、乱序、gap 和终态关闭。
- Query key 必须包含 organizationId。
- 表单客户端与服务端错误映射。
- 所有 Loading、Empty、Error、Partial success 状态。

### 20.2 集成测试

使用 MSW 模拟：

- Today 首次加载和后台刷新。
- 创建 Run 后从 QUEUED 到 RUNNING 再到 WAITING_APPROVAL。
- 两个审批者并发处理导致 409。
- Approve 成功但 Tool 执行失败。
- SSE 断线后从 Last-Event-ID 回放。
- 401 session 过期和登录恢复。
- 组织切换后旧数据不可见。

### 20.3 E2E

必须通过的闭环：

1. 导入客户并完成列映射。
2. 运行 Agent 并在 Run Detail 看到实时步骤。
3. Today 出现建议，生成邮件草稿。
4. Approvals 检查内容并批准。
5. Action 从 APPROVED 到 EXECUTING 再到 SUCCEEDED。
6. Customer Timeline 出现 EMAIL_SENT。
7. 邮箱断开时 Action 被安全阻止并提供修复入口。

### 20.4 视觉与可访问测试

- Storybook 覆盖核心业务组件的状态矩阵。
- Playwright 截图覆盖浅色、深色、1440px、1024px、390px。
- axe-core 扫描 Today、Customers、Approvals、Run Detail。
- 仅键盘完成 Today 到审批闭环。
- 200% 缩放和 reduced motion 手动检查。

## 21. 页面与后端接口映射

| 页面/动作 | API |
|---|---|
| Today | `GET /api/v1/dashboard` |
| Customer list | `GET /api/v1/customers` |
| Customer detail | `GET /api/v1/customers/{id}` |
| Timeline | `GET /api/v1/customers/{id}/timeline` |
| Follow-up queue | `GET /api/v1/follow-ups` |
| Generate email | `POST /api/v1/follow-ups/{id}/draft-email` |
| Snooze/Complete/Dismiss | 对应 Follow-up command endpoints |
| Approval inbox | `GET /api/v1/approvals?status=PENDING` |
| Approve | `POST /api/v1/approvals/{id}/approve` |
| Reject | `POST /api/v1/approvals/{id}/reject` |
| Start Agent | `POST /api/v1/agent-runs` |
| Run list/detail | `GET /api/v1/agent-runs...` |
| Run events | `GET /api/v1/agent-runs/{id}/events` |
| Cancel/retry | Agent Run command endpoints |
| Integrations | `/api/v1/integrations...` |

后端原设计已经覆盖 Follow-up 的 `snooze`、`dismiss`、`complete` 命令接口。为了让前端在启动和组织切换时获得可靠的用户上下文，还需补齐 session/permission 接口：

```http
GET /api/v1/session
GET /api/v1/organizations/{id}/permissions
```

## 22. 实施顺序

### Sprint 1：基础壳与客户数据

- Vite、React Router、FluentProvider、QueryClient。
- AppShell、组织上下文、权限、错误边界。
- Customers 列表和详情、Timeline。
- MSW mock 和基础 Storybook。

### Sprint 2：Today 与 Follow-up

- Dashboard、MetricStrip、Queue、Inspector。
- Follow-up 列表、Snooze、Dismiss、Complete。
- Skeleton、Empty、Error、Partial success 状态。

### Sprint 3：Agent Run

- Run 创建、列表、详情。
- SSE Hook、断线回放、状态校准。
- 运行进度条、Timeline、Cancel 和 Retry failed items。

### Sprint 4：Approval 与邮箱闭环

- Approval Inbox 和 Review Panel。
- 邮件安全预览、内容哈希、并发 409 处理。
- Approve/Reject 到 Action 执行状态展示。
- Timeline EMAIL_SENT 回写验证。

### Sprint 5：集成与商业指标

- CSV 导入向导、邮箱 OAuth、连接健康状态。
- Analytics、Usage & Billing。
- 可访问性、响应式、性能和 E2E 收尾。

## 23. 前端验收标准

- 登录后默认进入 Today，不出现空白 Chat 输入框。
- 用户能解释任意一条高优先级建议来自哪些客户事件。
- 高风险动作在审批前展示完整最终内容和目标。
- Approve 不等于 Sent，两个状态在 UI 中清楚区分。
- Agent Run 的所有后端状态都有唯一且准确的 UI 表达。
- SSE 断线、重复、乱序和漏事件均不会破坏页面状态。
- 切换组织后不显示上一组织任何缓存数据。
- Today、Approval 和 Run Detail 都有 Loading、Empty、Error 和 Partial success 状态。
- 桌面和 390px 移动端均可完成查看建议和审批流程。
- 仅键盘可以完成主要工作流。
- 页面达到 WCAG 2.2 AA，浅色和深色主题均通过对比检查。
- 前端日志和分析事件不包含客户姓名、邮箱、正文或 Prompt。
- 所有副作用命令携带幂等键，高风险命令不做乐观更新。
