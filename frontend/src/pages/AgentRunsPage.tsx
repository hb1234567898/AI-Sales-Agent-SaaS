import { ArrowClockwise, CheckCircle, Clock, Robot, WarningCircle } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { createAgentRun, getAgentRuns, getAgentRunSteps, type AgentRun } from '../api/agent-runs-api'
import { useIsGuest } from '../auth/use-auth'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

export function AgentRunsPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const runsQuery = useQuery({ queryKey: ['agent-runs'], queryFn: getAgentRuns })
  const runs = useMemo(() => runsQuery.data?.content ?? [], [runsQuery.data])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const selectedRun = runs.find((run) => run.id === selectedId) ?? runs[0]
  const stepsQuery = useQuery({
    queryKey: ['agent-run-steps', selectedRun?.id],
    queryFn: () => getAgentRunSteps(selectedRun.id),
    enabled: Boolean(selectedRun?.id),
  })
  const createMutation = useMutation({
    mutationFn: createAgentRun,
    onSuccess: (run) => {
      setSelectedId(run.id)
      void queryClient.invalidateQueries({ queryKey: ['agent-runs'] })
      void queryClient.invalidateQueries({ queryKey: ['approvals'] })
    },
  })

  const stats = useMemo(() => {
    const today = new Date().toLocaleDateString('zh-CN')
    const todayRuns = runs.filter((run) => new Date(run.createdAt).toLocaleDateString('zh-CN') === today)
    return {
      todayRuns: todayRuns.length,
      handledCustomers: runs.reduce((sum, run) => sum + run.processedCount, 0),
      pendingApprovals: runs.reduce((sum, run) => sum + run.pendingApprovalCount, 0),
      failed: runs.reduce((sum, run) => sum + run.failedCount, 0),
    }
  }, [runs])

  return (
    <section className="module-page">
      <DemoPageHeader
        title="Agent 运行"
        description="追踪每次运行的输入、步骤、模型分析与业务结果。"
        actions={(
          <button
            className="button button-primary"
            type="button"
            disabled={isGuest || createMutation.isPending}
            title={isGuest ? '游客模式不能触发 Agent' : undefined}
            onClick={() => createMutation.mutate()}
          >
            <ArrowClockwise size={16} />{createMutation.isPending ? '运行中…' : '新建运行'}
          </button>
        )}
      />

      <div className="module-stat-grid" aria-label="Agent 运行指标">
        <div><span>今日运行</span><strong>{stats.todayRuns}</strong><small>手动触发的客户跟进 Agent</small></div>
        <div><span>处理客户</span><strong>{stats.handledCustomers}</strong><small>最近运行累计</small></div>
        <div><span>待审批建议</span><strong>{stats.pendingApprovals}</strong><small>审批后进入跟进任务</small></div>
        <div><span>失败客户</span><strong className={stats.failed > 0 ? 'danger-value' : undefined}>{stats.failed}</strong><small>模型或数据异常</small></div>
      </div>

      {createMutation.isError ? <div className="audit-state is-error">{createMutation.error.message}</div> : null}

      <div className="runs-layout">
        <section className="surface run-history-panel">
          <div className="module-toolbar"><div><h2>运行历史</h2><span>{runsQuery.isPending ? '正在读取运行记录…' : `最近 ${runs.length} 条真实记录`}</span></div></div>
          <div className="run-history-list">
            {runs.map((run) => (
              <button className={selectedRun?.id === run.id ? 'run-history-item is-selected' : 'run-history-item'} type="button" key={run.id} onClick={() => setSelectedId(run.id)}>
                <span className="run-icon"><Robot size={16} /></span>
                <span><strong>{run.name}</strong><small>{shortId(run.id)} · {scopeLabel(run)}</small></span>
                <span className={`run-status ${statusTone(run.status)}`}>{statusLabel(run.status)}</span>
                <time>{formatDateTime(run.createdAt)}</time>
              </button>
            ))}
            {!runsQuery.isPending && runs.length === 0 ? <div className="approval-empty"><Robot size={24} /><strong>暂无运行记录</strong><span>点击“新建运行”开始扫描最近客户互动。</span></div> : null}
          </div>
        </section>

        <section className="surface run-detail-panel">
          {selectedRun ? (
            <>
              <div className="panel-header">
                <div><h2>{selectedRun.name}</h2><p>{shortId(selectedRun.id)} · {triggerLabel(selectedRun.triggerType)}</p></div>
                <span className={`run-status detail-status ${statusTone(selectedRun.status)}`}>{statusLabel(selectedRun.status)}</span>
              </div>
              <div className="run-result">
                <span className={selectedRun.failedCount > 0 ? 'result-icon is-error' : 'result-icon'}>{selectedRun.failedCount > 0 ? <WarningCircle size={22} /> : <CheckCircle size={22} />}</span>
                <div><strong>运行结果</strong><p>{summaryMessage(selectedRun)}</p></div>
              </div>
              <div className="execution-flow" aria-label="执行步骤">
                {(stepsQuery.data?.content ?? []).map((step) => (
                  <div key={step.id}>
                    <span>{step.status === 'FAILED' ? <WarningCircle size={15} /> : <CheckCircle size={15} />}</span>
                    <strong>{step.name}</strong>
                    <small>{step.durationMs ?? 0} ms</small>
                  </div>
                ))}
                {stepsQuery.isPending ? <div><span><Clock size={15} /></span><strong>正在读取步骤</strong><small>请稍候</small></div> : null}
              </div>
              <dl className="run-facts">
                <div><dt>处理范围</dt><dd>{scopeLabel(selectedRun)}</dd></div>
                <div><dt>候选客户</dt><dd>{selectedRun.totalCandidates}</dd></div>
                <div><dt>已处理</dt><dd>{selectedRun.processedCount}</dd></div>
                <div><dt>待审批</dt><dd>{selectedRun.pendingApprovalCount}</dd></div>
                <div><dt>运行耗时</dt><dd><Clock size={13} />{durationLabel(selectedRun)}</dd></div>
                <div><dt>触发方式</dt><dd>{triggerLabel(selectedRun.triggerType)}</dd></div>
              </dl>
            </>
          ) : (
            <div className="approval-empty"><Robot size={24} /><strong>还没有 Agent 数据</strong><span>先导入聊天记录，再触发客户跟进建议 Agent。</span></div>
          )}
        </section>
      </div>
    </section>
  )
}

function statusLabel(status: string) {
  if (status === 'WAITING_APPROVAL') return '待审批'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'PARTIALLY_COMPLETED') return '部分完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '运行中'
  return status
}

function statusTone(status: string) {
  if (status === 'WAITING_APPROVAL') return 'is-review'
  if (status === 'FAILED' || status === 'PARTIALLY_COMPLETED') return 'is-error'
  return ''
}

function triggerLabel(value: string) {
  return value === 'MANUAL' ? '手动触发' : value
}

function scopeLabel(run: AgentRun) {
  const recentDays = typeof run.scope.recentDays === 'number' ? run.scope.recentDays : 30
  const maxCustomers = typeof run.scope.maxCustomers === 'number' ? run.scope.maxCustomers : run.totalCandidates
  return `近 ${recentDays} 天 · 最多 ${maxCustomers} 个客户`
}

function summaryMessage(run: AgentRun) {
  return typeof run.outputSummary.message === 'string'
    ? run.outputSummary.message
    : run.errorMessage ?? `处理 ${run.processedCount} 个客户，生成 ${run.pendingApprovalCount} 条待审批建议`
}

function durationLabel(run: AgentRun) {
  if (!run.startedAt) return '-'
  const end = run.completedAt ?? new Date().toISOString()
  const seconds = Math.max(0, Math.round((new Date(end).getTime() - new Date(run.startedAt).getTime()) / 1000))
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}

function shortId(value: string) {
  return value.length > 14 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
