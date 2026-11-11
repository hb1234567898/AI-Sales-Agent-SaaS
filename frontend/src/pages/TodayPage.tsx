import {
  ArrowClockwise,
  ArrowRight,
  CheckCircle,
  ClockCountdown,
  Database,
  Lightning,
  Robot,
  SealCheck,
  UserFocus,
  WarningCircle,
} from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { getSystemHealth } from '../api/system-api'
import { TodayFollowUps } from '../components/followup/TodayFollowUps'

const metrics = [
  { label: '今日待跟进', value: 12, note: '3 项临近截止', icon: ClockCountdown },
  { label: '高优先级客户', value: 4, note: '新增 2 个信号', icon: UserFocus },
  { label: '待人工审批', value: 3, note: '1 项建议优先处理', icon: SealCheck },
  { label: '本周已触达', value: 28, note: '回复率 32%', icon: Lightning },
]

const aiReviews = [
  { company: '云岚科技', type: '客户邮件', detail: '确认方案范围与下次会议时间', confidence: '92%' },
  { company: '恒川智造', type: 'CRM 更新', detail: '阶段调整为技术评审', confidence: '87%' },
]

const recentRuns = [
  { id: 'RUN-0241', name: '高意向客户分析', scope: '42 个客户', time: '10:24', duration: '34 秒', status: '已完成' },
  { id: 'RUN-0240', name: '跟进动作生成', scope: '8 个客户', time: '09:48', duration: '21 秒', status: '待审核' },
  { id: 'RUN-0239', name: '沉默客户识别', scope: '126 个客户', time: '08:35', duration: '1 分 12 秒', status: '已完成' },
  { id: 'RUN-0238', name: '商机阶段校验', scope: '23 个客户', time: '07:50', duration: '48 秒', status: '已完成' },
  { id: 'RUN-0237', name: '跟进效果归因', scope: '64 个客户', time: '17:20', duration: '2 分 03 秒', status: '已完成' },
]

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
            <span className="demo-badge">演示数据</span>
          </div>
          <h1>今日工作台</h1>
          <p>集中处理最值得推进的客户与 AI 动作。</p>
        </div>
        <div className="page-actions">
          <button
            className="button button-secondary"
            type="button"
            onClick={() => void healthQuery.refetch()}
            disabled={healthQuery.isFetching}
          >
            <ArrowClockwise size={16} className={healthQuery.isFetching ? 'is-spinning' : ''} />
            刷新状态
          </button>
          <Link className="button button-primary" to="/app/agent-runs">
            <Lightning size={16} weight="fill" />
            运行客户分析
          </Link>
        </div>
      </header>

      <div className="metric-strip" aria-label="今日指标">
        {metrics.map(({ label, value, note, icon: Icon }) => (
          <article className="metric-item" key={label}>
            <div className="metric-label">
              <Icon size={17} aria-hidden />
              <span>{label}</span>
            </div>
            <strong className="metric-value">{value}</strong>
            <span className="metric-note">{note}</span>
          </article>
        ))}
      </div>

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
              {recentRuns.map((run) => (
                <div className="run-row" key={run.id}>
                  <span className="run-icon"><Robot size={16} /></span>
                  <span className="run-identity"><strong>{run.name}</strong><small>{run.id} · {run.scope}</small></span>
                  <span className="run-time"><strong>{run.time}</strong><small>{run.duration}</small></span>
                  <span className={`run-status ${run.status === '待审核' ? 'is-review' : ''}`}>{run.status}</span>
                  <Link className="row-action" to="/app/agent-runs" aria-label={`查看 ${run.name}`}><ArrowRight size={14} /></Link>
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="dashboard-rail" aria-label="工作台侧栏">
          <section className="surface rail-panel review-panel">
            <div className="panel-header compact-header">
              <div className="title-with-icon">
                <span className="section-icon"><Robot size={17} /></span>
                <div><h2>AI 动作审核</h2><p>需要确认的建议</p></div>
              </div>
              <span className="count-label">2</span>
            </div>
            <div className="review-list">
              {aiReviews.map((review) => (
                <article key={review.company}>
                  <div className="review-heading">
                    <span><strong>{review.company}</strong><small>{review.type}</small></span>
                    <span className="confidence">{review.confidence}</span>
                  </div>
                  <p>{review.detail}</p>
                  <div><button type="button" className="compact-button" disabled>暂不处理</button><Link to="/app/approvals" className="compact-button is-primary">审核</Link></div>
                </article>
              ))}
            </div>
          </section>

          <section className="surface rail-panel approval-panel">
            <div className="panel-header compact-header">
              <div className="title-with-icon">
                <span className="section-icon"><SealCheck size={17} /></span>
                <div><h2>待人工审批</h2><p>高风险操作控制</p></div>
              </div>
              <span className="count-label">3</span>
            </div>
            <dl className="mini-stat-list">
              <div><dt>发送客户消息</dt><dd>2</dd></div>
              <div><dt>更新 CRM 字段</dt><dd>1</dd></div>
              <div><dt>调整任务优先级</dt><dd>0</dd></div>
            </dl>
            <Link className="rail-link" to="/app/approvals">打开审批中心 <ArrowRight size={14} /></Link>
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
                  {healthQuery.isError ? <WarningCircle size={16} /> : <CheckCircle size={16} />}
                  后端 API
                </span>
                <StatusBadge status={apiStatus} />
              </div>
              <div><span className="connection-name"><Database size={16} />CRM 集成</span><StatusBadge status="muted" /></div>
              <div><span className="connection-name"><Robot size={16} />AI 模型</span><StatusBadge status="muted" /></div>
            </div>
            {healthQuery.isError ? <p className="connection-error">请确认后端已在 8080 端口启动。</p> : null}
          </section>
        </aside>
      </div>
    </section>
  )
}
