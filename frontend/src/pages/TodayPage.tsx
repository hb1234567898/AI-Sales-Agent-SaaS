import {
  ArrowClockwise,
  ArrowRight,
  CheckCircle,
  ClockCountdown,
  Checks,
  Database,
  Lightning,
  Robot,
  WarningCircle,
} from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { useAuth } from '../auth/use-auth'
import { getFollowUpMetrics } from '../api/follow-ups-api'
import { getPendingApprovals } from '../api/approvals-api'
import { getAgentRuns } from '../api/agent-runs-api'
import { getAiModelStatus } from '../api/ai-settings-api'
import { getSystemHealth } from '../api/system-api'
import { TodayFollowUps } from '../components/followup/TodayFollowUps'

function StatusBadge({ status }: { status: 'success' | 'error' | 'muted' | 'loading' }) {
  const label = {
    success: '已连接',
    error: '未连接',
    muted: '未配置',
    loading: '检查中',
  }[status]

  return <span className={`status-badge status-${status}`}>{label}</span>
}

export function TodayPage() {
  const session = useAuth()
  const tasks = useQuery({ queryKey: ['follow-up-metrics', 'MINE'], queryFn: () => getFollowUpMetrics('MINE'), refetchInterval: 30_000 })
  const approvals = useQuery({ queryKey: ['approvals', 'topbar', 'PENDING'], queryFn: getPendingApprovals, refetchInterval: 30_000 })
  const runs = useQuery({ queryKey: ['agent-runs'], queryFn: getAgentRuns, refetchInterval: 30_000 })
  const model = useQuery({ queryKey: ['ai-model-status'], queryFn: getAiModelStatus })
  const metrics = [
    { label: '今日到期', value: tasks.data?.today, note: '我的跟进任务', icon: ClockCountdown, to: '/app/follow-ups' },
    { label: '已逾期', value: tasks.data?.overdue, note: '优先安排处理', icon: WarningCircle, to: '/app/follow-ups' },
    { label: '待人工审批', value: approvals.data?.totalElements, note: '确认后执行 AI 动作', icon: Robot, to: '/app/approvals' },
    { label: '已完成跟进', value: tasks.data?.completed, note: '我的累计完成任务', icon: Checks, to: '/app/follow-ups' },
  ]
  const statusLabels: Record<string, string> = { WAITING_APPROVAL: '待审批', COMPLETED: '已完成', PARTIALLY_COMPLETED: '部分完成', FAILED: '失败', RUNNING: '运行中', QUEUED: '排队中' }

  const healthQuery = useQuery({
    queryKey: ['system-health'],
    queryFn: getSystemHealth,
  })

  const apiStatus = healthQuery.isPending
    ? 'loading'
    : healthQuery.isSuccess
      ? 'success'
      : 'error'

  return (
    <section className="dashboard-page">
      <header className="page-heading dashboard-heading">
        <div>
          <div className="eyebrow-row">
            <p className="eyebrow">销售运营中心</p>
            {session.role === 'GUEST' && <span className="demo-badge">演示数据</span>}
          </div>
          <h1>今日工作台</h1>
          <p>集中处理最值得推进的客户与 AI 动作。</p>
        </div>
        <div className="page-actions">
          <button
            className="button button-secondary"
            type="button"
            onClick={() => void Promise.all([healthQuery.refetch(), tasks.refetch(), approvals.refetch(), runs.refetch(), model.refetch()])}
            disabled={healthQuery.isFetching || tasks.isFetching || approvals.isFetching || runs.isFetching || model.isFetching}
          >
            <ArrowClockwise size={16} className={healthQuery.isFetching ? 'is-spinning' : ''} />
            刷新状态
          </button>
          <Link className="button button-primary" to="/app/agent-runs">
            <Lightning size={16} weight="fill" />
            查看分析与运行
          </Link>
        </div>
      </header>

      <div className="metric-strip" aria-label="今日指标">
        {metrics.map(({ label, value, note, icon: Icon, to }) => (
          <Link className="metric-item" key={label} to={to}>
            <div className="metric-label">
              <Icon size={17} aria-hidden />
              <span>{label}</span>
            </div>
            <strong className="metric-value">{value ?? '—'}</strong>
            <span className="metric-note">{note}</span>
          </Link>
        ))}
      </div>

      {(tasks.isError || approvals.isError) && <p role="alert" className="connection-error">部分统计暂时无法加载，未获取的数据以 — 显示。请刷新重试。</p>}
      <div className="dashboard-grid">
        <div className="dashboard-primary">
          <TodayFollowUps />

          <section className="surface runs-panel">
            <div className="panel-header">
              <div>
                <h2>最近 Agent 运行</h2>
                <p>查看客户分析、动作生成与执行结果</p>
              </div>
              <Link className="text-link" to="/app/agent-runs">
                运行记录 <ArrowRight size={14} />
              </Link>
            </div>
            <div className="run-list">
              {runs.isPending && <p className="dashboard-state" role="status">正在读取运行记录…</p>}
              {runs.isError && <p className="dashboard-state" role="alert">运行记录加载失败，请刷新重试。</p>}
              {runs.isSuccess && !runs.data.content.length && <p className="dashboard-state">暂无运行记录，可前往 MCP 助手开始分析。</p>}
              {runs.data?.content.slice(0, 5).map((run) => (
                <div className="run-row" key={run.id}>
                  <span className="run-icon"><Robot size={16} /></span>
                  <span className="run-identity"><strong>{run.name}</strong><small>已处理 {run.processedCount} / {run.totalCandidates} 个客户</small></span>
                  <span className="run-time"><strong>{new Date(run.createdAt).toLocaleDateString('zh-CN')}</strong><small>{new Date(run.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</small></span>
                  <span className={`run-status ${run.status === 'WAITING_APPROVAL' ? 'is-review' : ''}`}>{statusLabels[run.status] ?? run.status}</span>
                  <Link className="row-action" to="/app/agent-runs" aria-label={`查看 ${run.name}`}><ArrowRight size={14} /></Link>
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="dashboard-rail" aria-label="工作台侧栏">
          <section className="surface rail-panel review-panel">
            <div className="panel-header compact-header">
              <div><h2>待人工审批</h2><p>统一确认 AI 建议与客户邮件</p></div>
              <span className="count-label">{approvals.data?.totalElements ?? '—'}</span>
            </div>
            <div className="review-list">
              {approvals.isPending && <p className="dashboard-state" role="status">正在读取审批…</p>}
              {approvals.isError && <p className="dashboard-state" role="alert">审批加载失败，请刷新重试。</p>}
              {approvals.isSuccess && !approvals.data.content.length && <p className="dashboard-state">暂无待审批动作</p>}
              {approvals.data?.content.slice(0, 3).map(approval => <article key={approval.id}>
                <div className="review-heading"><strong>{approval.customerName}</strong><span>{approval.riskLevel === 'HIGH' ? '高风险' : '待确认'}</span></div>
                <p>{approval.reason}</p>
              </article>)}
            </div>
            <Link className="rail-link" to="/app/approvals">打开审批中心 <ArrowRight size={14} /></Link>
          </section>
          <section className="surface rail-panel">
            <div className="panel-header compact-header"><div><h2>常用工作入口</h2><p>从客户沟通推进到成交</p></div></div>
            <nav className="workbench-shortcuts" aria-label="常用工作入口">
              <Link to="/app/mcp">MCP 助手<span>分析客户与生成行动</span><ArrowRight size={15} /></Link>
              <Link to="/app/mail">客户邮件<span>处理收信与回复</span><ArrowRight size={15} /></Link>
              <Link to="/app/quotes">报价单<span>关联商机与产品价格</span><ArrowRight size={15} /></Link>
              <Link to="/app/contracts">合同与回款<span>推进签约与收款</span><ArrowRight size={15} /></Link>
              <Link to="/app/knowledge">企业知识库<span>管理销售资料与引用</span><ArrowRight size={15} /></Link>
            </nav>
          </section>

          <section className="surface rail-panel connection-panel">
            <div className="panel-header compact-header">
              <div className="title-with-icon">
                <span className="section-icon"><Database size={17} /></span>
                <div><h2>系统连接</h2><p>数据与服务状态</p></div>
              </div>
            </div>
            <div className="connection-list">
              <div>
                <span className="connection-name">
                  <Link className="rail-link" to="/app/settings">管理服务配置 <ArrowRight size={14} /></Link>
            {healthQuery.isError ? <WarningCircle size={16} /> : <CheckCircle size={16} />}
                  后端 API
                </span>
                <StatusBadge status={apiStatus} />
              </div>

              <div><span className="connection-name"><Robot size={16} />AI 模型</span><span className="status-badge">{model.isPending ? '检查中' : model.isError ? '状态不可用' : model.data?.ready ? '已配置' : '待配置'}</span></div>
            </div>
            <Link className="rail-link" to="/app/settings">管理服务配置 <ArrowRight size={14} /></Link>
            {healthQuery.isError ? <p className="connection-error">服务暂时不可用，请稍后刷新重试。</p> : null}
          </section>
        </aside>
      </div>
    </section>
  )
}
