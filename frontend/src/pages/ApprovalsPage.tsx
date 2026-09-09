import { Check, Clock, EnvelopeSimple, ShieldCheck, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approveApproval, getPendingApprovals, rejectApproval, type Approval } from '../api/approvals-api'
import { useIsGuest } from '../auth/use-auth'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

export function ApprovalsPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['approvals', 'PENDING'], queryFn: getPendingApprovals })
  const pending = query.data?.content ?? []
  const approveMutation = useMutation({
    mutationFn: approveApproval,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['approvals'] })
      void queryClient.invalidateQueries({ queryKey: ['follow-ups'] })
      void queryClient.invalidateQueries({ queryKey: ['agent-runs'] })
    },
  })
  const rejectMutation = useMutation({
    mutationFn: rejectApproval,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['approvals'] })
      void queryClient.invalidateQueries({ queryKey: ['agent-runs'] })
    },
  })
  const decidingId = approveMutation.variables?.id ?? rejectMutation.variables?.id

  return (
    <section className="module-page">
      <DemoPageHeader title="审批" description="审核 Agent 生成的客户跟进建议和高风险动作。" />

      <div className="approval-layout">
        <section className="surface module-panel">
          <div className="module-toolbar">
            <div><h2>待审批队列</h2><span>{query.isPending ? '正在读取审批项…' : `${pending.length} 项等待人工确认`}</span></div>
            <span className="count-label">{pending.length}</span>
          </div>
          {(approveMutation.isError || rejectMutation.isError) ? <div className="audit-state is-error">{(approveMutation.error ?? rejectMutation.error)?.message}</div> : null}
          <div className="approval-list">
            {pending.map((approval) => {
              const busy = decidingId === approval.id && (approveMutation.isPending || rejectMutation.isPending)
              return (
                <article className="approval-row" key={approval.id}>
                  <div className="approval-topline">
                    <span className="approval-type"><ShieldCheck size={16} />{actionTypeLabel(approval.actionType)}</span>
                    <span className={`risk-label ${riskClass(approval.riskLevel)}`}>{riskLabel(approval.riskLevel)}风险</span>
                    <span className="approval-time"><Clock size={14} />{formatDateTime(approval.requestedAt)}</span>
                  </div>
                  <h3>{previewAction(approval)}</h3>
                  <p>{approval.reason}</p>
                  <EmailPreview approval={approval} />
                  <dl className="approval-meta">
                    <div><dt>客户</dt><dd>{approval.customerName}</dd></div>
                    <div><dt>发起方</dt><dd>{approval.requester}</dd></div>
                    <div><dt>优先级</dt><dd>{previewText(approval.preview.priority) ?? '-'}</dd></div>
                    <div><dt>编号</dt><dd>{shortId(approval.id)}</dd></div>
                  </dl>
                  <div className="approval-actions">
                    <button className="button button-secondary" type="button" disabled={isGuest || busy} title={isGuest ? '游客模式不能处理审批' : undefined} onClick={() => rejectMutation.mutate(approval)}><X size={15} />拒绝</button>
                    <button className="button button-primary" type="button" disabled={isGuest || busy} title={isGuest ? '游客模式不能处理审批' : undefined} onClick={() => approveMutation.mutate(approval)}><Check size={15} />批准</button>
                  </div>
                </article>
              )
            })}
            {!query.isPending && pending.length === 0 ? <div className="approval-empty"><Check size={24} /><strong>全部处理完成</strong><span>触发 Agent 后，新的跟进建议会出现在这里。</span></div> : null}
          </div>
        </section>

        <aside className="approval-sidebar">
          <section className="surface policy-panel">
            <div className="panel-header"><div><h2>今日审批概况</h2><p>真实待审批队列</p></div></div>
            <dl className="policy-stats">
              <div><dt>待处理</dt><dd>{pending.length}</dd></div>
              <div><dt>中风险</dt><dd>{pending.filter((approval) => approval.riskLevel === 'MEDIUM').length}</dd></div>
              <div><dt>低风险</dt><dd>{pending.filter((approval) => approval.riskLevel === 'LOW').length}</dd></div>
            </dl>
          </section>
          <section className="surface policy-panel">
            <div className="panel-header"><div><h2>审批策略</h2><p>当前生效规则</p></div></div>
            <div className="policy-list">
              <div><span>AI 跟进建议</span><strong>人工确认</strong></div>
              <div><span>创建内部任务</span><strong>批准后写入</strong></div>
              <div><span>外部发送动作</span><strong>审批后发送</strong></div>
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}

function actionTypeLabel(value: string) {
  if (value === 'CREATE_INTERNAL_FOLLOW_UP') return '创建跟进任务'
  if (value === 'SEND_EMAIL') return '发送邮件'
  if (value === 'GENERATE_EMAIL_DRAFT') return '生成邮件草稿'
  if (value === 'CREATE_CRM_TASK') return '创建 CRM 任务'
  return value
}

function riskClass(value: string) {
  if (value === 'HIGH') return 'risk-high'
  if (value === 'MEDIUM') return 'risk-medium'
  return 'risk-low'
}

function riskLabel(value: string) {
  if (value === 'HIGH') return '高'
  if (value === 'MEDIUM') return '中'
  return '低'
}

function EmailPreview({ approval }: { approval: Approval }) {
  if (approval.actionType !== 'SEND_EMAIL') return null
  const to = previewText(approval.preview.to)
  const subject = previewText(approval.preview.subject)
  const body = previewText(approval.preview.body)
  return (
    <section className="email-approval-preview" aria-label="邮件发送预览">
      <div className="email-approval-title"><EnvelopeSimple size={15} />邮件内容</div>
      <dl>
        <div><dt>收件人</dt><dd>{to || '未解析到收件人'}</dd></div>
        <div><dt>主题</dt><dd>{subject || '(无主题)'}</dd></div>
      </dl>
      <p>{body || '暂无邮件正文'}</p>
    </section>
  )
}

function previewAction(approval: Approval) {
  return previewText(approval.preview.action) ?? '确认 AI 生成的下一步跟进建议'
}

function previewText(value: unknown) {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : null
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
