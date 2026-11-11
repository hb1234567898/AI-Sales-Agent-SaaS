import { ArrowClockwise, BellRinging, CalendarCheck, Check, ClockCountdown, GearSix, PencilSimple, Plus, Robot, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useDeferredValue, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  cancelFollowUp, completeFollowUp, createFollowUp, getFollowUp, getFollowUpMetrics, getFollowUpOptions, getFollowUpRule,
  getFollowUps, runFollowUpRule, saveFollowUpRule, snoozeFollowUp, updateFollowUp,
  type FollowUp, type FollowUpInput, type FollowUpRule, type FollowUpScope,
} from '../api/follow-ups-api'
import { useAuth } from '../auth/use-auth'
import { SelectField } from '../components/forms/SelectField'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import './follow-ups.css'

const filters = [{ value: 'ALL', label: '待处理' }, { value: 'TODAY', label: '今日到期' }, { value: 'OVERDUE', label: '已逾期' }, { value: 'CLOSED', label: '已结束' }]
const openStatuses = new Set(['OPEN', 'IN_PROGRESS', 'SNOOZED'])
const actionTypes = [{ value: 'CALL', label: '电话沟通' }, { value: 'EMAIL', label: '邮件跟进' }, { value: 'MEETING', label: '会议拜访' }, { value: 'OTHER', label: '其他跟进' }]

export function FollowUpsPage() {
  const session = useAuth()
  const isGuest = session.role === 'GUEST'
  const writable = ['OWNER', 'ADMIN', 'MANAGER', 'SALES'].includes(session.role)
  const managesTeam = ['OWNER', 'ADMIN', 'MANAGER'].includes(session.role)
  const client = useQueryClient()
  const [filter, setFilter] = useState('ALL')
  const [scope, setScope] = useState<FollowUpScope>('MINE')
  const [page, setPage] = useState(0)
  const [now, setNow] = useState(Date.now)
  const [editor, setEditor] = useState<FollowUp | 'create' | null>(null)
  const [snoozing, setSnoozing] = useState<FollowUp | null>(null)
  const [cancelling, setCancelling] = useState<FollowUp | null>(null)
  const [rulesOpen, setRulesOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('task')
  const selected = useQuery({ queryKey: ['follow-up', selectedId], queryFn: () => getFollowUp(selectedId!), enabled: Boolean(selectedId) })
  const query = useQuery({ queryKey: ['follow-ups', filter, scope, page], queryFn: () => getFollowUps(filter, page, scope), refetchInterval: 30_000 })
  const metrics = useQuery({ queryKey: ['follow-up-metrics', scope], queryFn: () => getFollowUpMetrics(scope), refetchInterval: 30_000 })
  const rule = useQuery({ queryKey: ['follow-up-rule'], queryFn: getFollowUpRule, refetchInterval: rulesOpen ? 5000 : 60_000 })
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 30_000); return () => window.clearInterval(timer) }, [])
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['follow-ups'] }), client.invalidateQueries({ queryKey: ['follow-up'] }),
      client.invalidateQueries({ queryKey: ['follow-up-metrics'] }), client.invalidateQueries({ queryKey: ['follow-up-reminders'] }),
      client.invalidateQueries({ queryKey: ['follow-up-rule'] }),
    ])
  }
  const closeEditor = () => { setEditor(null); if (selectedId) { const next = new URLSearchParams(searchParams); next.delete('task'); setSearchParams(next, { replace: true }) } }
  const save = useMutation({
    mutationFn: ({ task, input }: { task: FollowUp | null; input: FollowUpInput }) => task ? updateFollowUp(task.id, input) : createFollowUp(input),
    onSuccess: async (_, { task }) => { closeEditor(); setNotice(task ? '任务已保存' : '跟进任务已创建'); await refresh() },
  })
  const complete = useMutation({ mutationFn: (task: FollowUp) => completeFollowUp(task.id, task.version), onSuccess: async () => { setNotice('任务已完成'); await refresh() } })
  const cancel = useMutation({ mutationFn: (task: FollowUp) => cancelFollowUp(task.id, task.version), onSuccess: async () => { setCancelling(null); setNotice('任务已取消'); await refresh() } })
  const snooze = useMutation({
    mutationFn: ({ task, dueAt, reason }: { task: FollowUp; dueAt: string; reason: string }) => snoozeFollowUp(task.id, dueAt, reason, task.version),
    onSuccess: async () => { setSnoozing(null); setNotice('跟进时间已延期'); await refresh() },
  })
  const visibleEditor = editor ?? (selectedId ? selected.data ?? null : null)
  const tasks = query.data?.content ?? []

  return (
    <section className="module-page follow-up-page">
      <DemoPageHeader title="跟进任务" description="安排客户沟通，处理到期待办与主动跟进建议。" demo={isGuest} actions={<>
        <button className="button button-secondary" type="button" onClick={() => setRulesOpen(true)}><GearSix size={16} />自动跟进</button>
        <button className="button button-primary" type="button" disabled={!writable} title={!writable ? '当前账号为只读模式' : undefined} onClick={() => { save.reset(); setEditor('create') }}><Plus size={16} />新建任务</button>
      </>} />
      <div className="module-stat-grid" aria-label="跟进任务指标">
        <div><span>当前待办</span><strong>{metrics.data?.pending ?? '—'}</strong><small>{scope === 'TEAM' ? '团队未结束任务' : '我的待办与未分配任务'}</small></div>
        <div><span>今日到期</span><strong>{metrics.data?.today ?? '—'}</strong><small>按北京时间统计</small></div>
        <div><span>已逾期</span><strong className={metrics.data?.overdue ? 'danger-value' : undefined}>{metrics.data?.overdue ?? '—'}</strong><small>超过计划跟进时间</small></div>
        <div><span>已完成</span><strong>{metrics.data?.completed ?? '—'}</strong><small>保留任务处理记录</small></div>
      </div>
      <div className="follow-up-automation-summary"><Robot size={17} /><span>{rule.data?.enabled ? `每天 ${String(rule.data.runHour).padStart(2, '0')}:00 检查 · ${rule.data.inactiveDays} 天未联系客户 · 最多 ${rule.data.maxCustomers} 位` : '自动跟进未开启'}{isGuest ? ' · 演示模式' : ''}</span><button className="text-link" onClick={() => setRulesOpen(true)}>设置规则</button></div>
      {notice && <p className="follow-up-notice" role="status"><Check size={16} />{notice}</p>}
      {metrics.isError && <p className="form-error" role="alert">{metrics.error.message}</p>}
      {selected.isError && <p className="form-error" role="alert">{selected.error.message}</p>}
      {complete.isError && <p className="form-error" role="alert">{complete.error.message}</p>}
      <section className="surface module-panel">
        <div className="module-toolbar">
          <div><h2>任务队列</h2><span>{query.data ? `共 ${query.data.totalElements} 条任务` : '正在读取任务'}</span></div>
          <div className="follow-up-filters">
            {managesTeam && <SelectField ariaLabel="任务范围" value={scope} options={[{ value: 'MINE', label: '我的任务' }, { value: 'TEAM', label: '团队任务' }]} onChange={(value) => { setScope(value as FollowUpScope); setPage(0) }} />}
            <div className="segmented-filter" aria-label="任务筛选">{filters.map((item) => <button className={filter === item.value ? 'is-active' : ''} type="button" key={item.value} onClick={() => { setFilter(item.value); setPage(0) }}>{item.label}</button>)}</div>
          </div>
        </div>
        {query.isError ? <div className="audit-state is-error" role="alert">{query.error.message}<button className="text-link" onClick={() => void query.refetch()}>重试</button></div> : query.isPending ? <div className="audit-state">正在读取跟进任务…</div> : (
          <div className="follow-up-task-list">
            {tasks.map((task) => {
              const active = openStatuses.has(task.status)
              const canEdit = writable && active && (managesTeam || !task.ownerMemberId || task.ownerMemberId === session.memberId)
              const title = taskTitle(task)
              return <article className={`follow-up-task${!active ? ' is-closed' : ''}`} key={task.id}>
                <button className="task-check" type="button" aria-label={`完成 ${title}`} title={active ? '完成任务' : task.status === 'COMPLETED' ? '已完成' : '已取消'} disabled={!canEdit || complete.isPending} onClick={() => complete.mutate(task)}><Check size={14} weight="bold" /></button>
                <div className="follow-up-task-main"><button className="follow-up-title" type="button" onClick={() => { save.reset(); setEditor(task) }}>{title}</button><small>{task.customerName}{task.reason ? ` · ${task.reason}` : ''}</small></div>
                <span className="follow-up-source">{task.origin === 'RULE' ? '自动跟进' : task.origin === 'AI' ? 'AI 建议' : '手动创建'}</span>
                <span className={`priority-badge ${task.priority >= 80 ? 'priority-high' : task.priority >= 60 ? 'priority-medium' : 'priority-low'}`}>{task.priority >= 80 ? '高' : task.priority >= 60 ? '中' : '低'}</span>
                <span className={`follow-up-due${active && new Date(task.dueAt).getTime() <= now ? ' is-overdue' : ''}`}><CalendarCheck size={14} />{active ? dueLabel(task.dueAt, now) : task.status === 'COMPLETED' ? '已完成' : '已取消'}</span>
                <span className="follow-up-owner">{task.ownerName ?? '未分配'}</span>
                <div className="follow-up-row-actions">
                  <button className="icon-button" type="button" aria-label={`编辑 ${title}`} title="编辑任务" disabled={!canEdit} onClick={() => { save.reset(); setEditor(task) }}><PencilSimple size={16} /></button>
                  <button className="icon-button" type="button" aria-label={`延期 ${title}`} title="延期" disabled={!canEdit} onClick={() => { snooze.reset(); setSnoozing(task) }}><ClockCountdown size={16} /></button>
                  <button className="icon-button" type="button" aria-label={`取消 ${title}`} title="取消任务" disabled={!canEdit} onClick={() => { cancel.reset(); setCancelling(task) }}><X size={16} /></button>
                </div>
              </article>
            })}
            {!tasks.length && <div className="approval-empty"><CalendarCheck size={24} /><strong>{filter === 'CLOSED' ? '暂无已结束任务' : '暂无跟进任务'}</strong><span>{isGuest ? '切换筛选查看演示任务' : '新建任务，或开启自动跟进规则'}</span></div>}
          </div>
        )}
        <div className="follow-up-pagination"><span>{query.data ? `第 ${query.data.page + 1} 页 / 共 ${Math.max(1, query.data.totalPages)} 页` : ''}</span><button className="button button-secondary" disabled={page === 0 || query.isFetching} onClick={() => setPage((value) => value - 1)}>上一页</button><button className="button button-secondary" disabled={!query.data || query.data.last || query.isFetching} onClick={() => setPage((value) => value + 1)}>下一页</button></div>
      </section>
      {visibleEditor && <TaskEditor key={visibleEditor === 'create' ? 'create' : visibleEditor.id} task={visibleEditor === 'create' ? null : visibleEditor} memberId={session.memberId} writable={writable && (visibleEditor === 'create' || openStatuses.has(visibleEditor.status))} busy={save.isPending} error={save.error} onClose={closeEditor} onSubmit={(input) => save.mutate({ task: visibleEditor === 'create' ? null : visibleEditor, input })} />}
      {snoozing && <SnoozeEditor task={snoozing} busy={snooze.isPending} error={snooze.error} onClose={() => setSnoozing(null)} onSubmit={(dueAt, reason) => snooze.mutate({ task: snoozing, dueAt, reason })} />}
      {cancelling && <FollowUpDialog title="取消跟进任务" busy={cancel.isPending} onClose={() => setCancelling(null)}><div className="follow-up-dialog-body"><p>确认取消「{taskTitle(cancelling)}」？任务会保留在已结束记录中。</p><ErrorMessage error={cancel.error} /></div><footer><button className="button button-secondary" disabled={cancel.isPending} onClick={() => setCancelling(null)}>返回</button><button className="button button-primary" disabled={cancel.isPending} onClick={() => cancel.mutate(cancelling)}>确认取消</button></footer></FollowUpDialog>}
      {rulesOpen && <RuleEditor rule={rule.data} loading={rule.isPending} loadError={rule.error} writable={writable} onClose={() => setRulesOpen(false)} onRefresh={refresh} />}
    </section>
  )
}

function FollowUpDialog({ title, busy = false, onClose, children }: { title: string; busy?: boolean; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()
  useEffect(() => { const dialog = ref.current!; const previous = document.activeElement; dialog.showModal(); return () => { dialog.close(); if (previous instanceof HTMLElement) previous.focus() } }, [])
  return <dialog className="follow-up-dialog" ref={ref} aria-labelledby={id} onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}><header><h2 id={id}>{title}</h2><button className="icon-button" type="button" aria-label="关闭" title="关闭" disabled={busy} onClick={onClose}><X size={18} /></button></header>{children}</dialog>
}

function TaskEditor({ task, memberId, writable, busy, error, onClose, onSubmit }: { task: FollowUp | null; memberId: string; writable: boolean; busy: boolean; error: unknown; onClose: () => void; onSubmit: (input: FollowUpInput) => void }) {
  const [search, setSearch] = useState('')
  const keyword = useDeferredValue(search)
  const options = useQuery({ queryKey: ['follow-up-options', keyword], queryFn: () => getFollowUpOptions(keyword) })
  const [customerId, setCustomerId] = useState(task?.customerId ?? '')
  const [ownerId, setOwnerId] = useState(task?.ownerMemberId ?? memberId)
  const [title, setTitle] = useState(task ? taskTitle(task) : '')
  const [dueAt, setDueAt] = useState(() => localDateTime(task?.dueAt ?? new Date(Date.now() + 3600_000).toISOString()))
  const [priority, setPriority] = useState(String(task?.priority ?? 60))
  const [reason, setReason] = useState(task?.reason ?? '')
  const [actionType, setActionType] = useState(actionTypes.some(t => t.value === task?.recommendedActionType) ? task!.recommendedActionType : 'CALL')
  const disabled = busy || !writable
  const customerOptions = task ? [{ value: task.customerId, label: task.customerName }] : options.data?.customers.map(c => ({ value: c.id, label: c.name })) ?? []
  const ownerOptions = options.data?.owners.map(o => ({ value: o.id, label: o.name })) ?? []
  if (task?.ownerMemberId && !ownerOptions.some(o => o.value === task.ownerMemberId)) ownerOptions.push({ value: task.ownerMemberId, label: task.ownerName ?? '原负责人' })
  const validDate = Boolean(dueAt) && Number.isFinite(new Date(dueAt).getTime())
  return <FollowUpDialog title={task ? writable ? '编辑跟进任务' : '任务详情' : '新建跟进任务'} busy={busy} onClose={onClose}>
    <form onSubmit={(event) => { event.preventDefault(); if (validDate) onSubmit({ customerId, ownerMemberId: ownerId, title: title.trim(), dueAt: new Date(dueAt).toISOString(), priority: Number(priority), reason: reason.trim(), actionType, ...(task ? { version: task.version } : {}) }) }}>
      <div className="follow-up-dialog-body follow-up-form-grid">
        {task && typeof task.recommendedAction.mailMessageId === 'string' && <Link className="text-link full-width" to={`/app/mail?message=${encodeURIComponent(task.recommendedAction.mailMessageId)}`}>查看客户来信并回复</Link>}
        {!task && <label className="full-width"><span>查找客户</span><input type="search" placeholder="输入客户名称" maxLength={200} value={search} disabled={disabled} onChange={e => { setSearch(e.target.value); setCustomerId('') }} /></label>}
        <label className="full-width"><span>关联客户</span><SelectField value={customerId} ariaLabel="关联客户" options={customerOptions} disabled={disabled || Boolean(task) || options.isPending} onChange={setCustomerId} /></label>
        <label className="full-width"><span>任务名称</span><input autoFocus required maxLength={200} value={title} disabled={disabled} onChange={e => setTitle(e.target.value)} /></label>
        <label><span>负责人</span><SelectField value={ownerId} ariaLabel="负责人" options={ownerOptions} onChange={setOwnerId} disabled={disabled || options.isPending} /></label>
        <label><span>跟进方式</span><SelectField value={actionType} ariaLabel="跟进方式" options={actionTypes} onChange={setActionType} disabled={disabled} /></label>
        <label><span>计划跟进时间</span><input required type="datetime-local" value={dueAt} disabled={disabled} onChange={e => setDueAt(e.target.value)} /></label>
        <label><span>优先级</span><SelectField value={priority} ariaLabel="优先级" options={[{ value: priority, label: Number(priority) >= 80 ? '高' : Number(priority) >= 60 ? '中' : '低' }, ...[{ value: '90', label: '高' }, { value: '60', label: '中' }, { value: '30', label: '低' }].filter(p => p.value !== priority)]} onChange={setPriority} disabled={disabled} /></label>
        <label className="full-width"><span>跟进备注</span><textarea rows={3} maxLength={2000} value={reason} disabled={disabled} onChange={e => setReason(e.target.value)} /></label>
        <div className="full-width"><ErrorMessage error={options.error ?? error} /></div>
      </div>
      <footer><button className="button button-secondary" disabled={busy} type="button" onClick={onClose}>{writable ? '取消' : '关闭'}</button>{writable && <button className="button button-primary" type="submit" disabled={busy || !customerId || !ownerId || !title.trim() || !validDate || options.isError || options.isPending}>{busy ? '保存中…' : '保存任务'}</button>}</footer>
    </form>
  </FollowUpDialog>
}

function SnoozeEditor({ task, busy, error, onClose, onSubmit }: { task: FollowUp; busy: boolean; error: unknown; onClose: () => void; onSubmit: (dueAt: string, reason: string) => void }) {
  const [dueAt, setDueAt] = useState(() => localDateTime(new Date(Math.max(Date.now(), Date.parse(task.dueAt)) + 86400_000).toISOString()))
  const [now, setNow] = useState(Date.now)
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 30_000); return () => window.clearInterval(timer) }, [])
  const [reason, setReason] = useState('')
  const valid = Boolean(dueAt) && Date.parse(dueAt) > now
  return <FollowUpDialog title="延期跟进" busy={busy} onClose={onClose}><form onSubmit={event => { event.preventDefault(); if (valid) onSubmit(new Date(dueAt).toISOString(), reason.trim()) }}><div className="follow-up-dialog-body">
    <p className="follow-up-context">{task.customerName} · {taskTitle(task)}</p>
    <label><span>新的跟进时间</span><input required type="datetime-local" value={dueAt} disabled={busy} onChange={e => setDueAt(e.target.value)} /></label>
    <label><span>延期备注</span><textarea maxLength={2000} rows={3} value={reason} disabled={busy} onChange={e => setReason(e.target.value)} /></label>
    <ErrorMessage error={error} />
  </div><footer><button className="button button-secondary" type="button" disabled={busy} onClick={onClose}>取消</button><button className="button button-primary" type="submit" disabled={busy || !valid}>确认延期</button></footer></form></FollowUpDialog>
}

function RuleEditor({ rule, loading, loadError, writable, onClose, onRefresh }: { rule: FollowUpRule | undefined; loading: boolean; loadError: unknown; writable: boolean; onClose: () => void; onRefresh: () => Promise<void> }) {
  const [busy, setBusy] = useState(false)
  return <FollowUpDialog title="自动跟进规则" busy={busy} onClose={onClose}>{loading ? <div className="follow-up-dialog-body">正在读取规则…</div> : !rule ? <div className="follow-up-dialog-body"><ErrorMessage error={loadError} /></div> : <RuleForm rule={rule} writable={writable} onClose={onClose} onRefresh={onRefresh} onBusyChange={setBusy} />}</FollowUpDialog>
}

function RuleForm({ rule, writable, onClose, onRefresh, onBusyChange }: { rule: FollowUpRule; writable: boolean; onClose: () => void; onRefresh: () => Promise<void>; onBusyChange: (busy: boolean) => void }) {
  const [values, setValues] = useState(() => ruleValues(rule))
  const [baseline, setBaseline] = useState(() => JSON.stringify(ruleValues(rule)))
  const [savedMessage, setSavedMessage] = useState('')
  const save = useMutation({ mutationFn: saveFollowUpRule, onSuccess: async (result) => { setValues(ruleValues(result)); setBaseline(JSON.stringify(ruleValues(result))); setSavedMessage('规则已保存'); await onRefresh() } })
  const run = useMutation({ mutationFn: runFollowUpRule, onSuccess: async (result) => { setSavedMessage(result.status === 'QUEUED' || result.status === 'RUNNING' ? `已生成 ${result.createdCount} 条待办，AI 分析已排队` : `今天已检查，生成 ${result.createdCount} 条待办`); await onRefresh() } })
  const dirty = JSON.stringify(values) !== baseline
  const busy = save.isPending || run.isPending
  useEffect(() => { onBusyChange(busy) }, [busy, onBusyChange])
  const lastRun = rule.lastRun
  const set = <K extends keyof typeof values>(key: K, value: typeof values[K]) => { setValues(old => ({ ...old, [key]: value })); setSavedMessage('') }
  return <form onSubmit={e => { e.preventDefault(); save.mutate(values) }}>
    <div className="follow-up-dialog-body follow-up-form-grid">
      <label className="follow-up-toggle full-width"><span><strong>开启主动跟进</strong><small>按客户计划时间和未联系天数生成我的待办</small></span><input type="checkbox" checked={values.enabled} disabled={!writable || busy} onChange={e => set('enabled', e.target.checked)} /></label>
      <label><span>连续未联系天数</span><input type="number" min={1} max={180} required value={values.inactiveDays} disabled={!writable || busy} onChange={e => set('inactiveDays', Number(e.target.value))} /></label>
      <label><span>每日检查时间（北京时间）</span><SelectField value={String(values.runHour)} ariaLabel="每日检查时间" options={Array.from({ length: 24 }, (_, i) => ({ value: String(i), label: `${String(i).padStart(2, '0')}:00` }))} disabled={!writable || busy} onChange={v => set('runHour', Number(v))} /></label>
      <label><span>每日最多检查客户数</span><input type="number" min={1} max={20} required value={values.maxCustomers} disabled={!writable || busy} onChange={e => set('maxCustomers', Number(e.target.value))} /></label>
      <label><span>任务提前提醒（分钟）</span><input type="number" min={0} max={10080} required value={values.reminderMinutes} disabled={!writable || busy} onChange={e => set('reminderMinutes', Number(e.target.value))} /></label>
      <label className="follow-up-toggle full-width"><span><strong>同时生成 AI 跟进建议</strong><small>使用个人 Token 额度；邮件等客户触达操作仍需审批</small></span><input type="checkbox" checked={values.autoAnalyze} disabled={!writable || busy} onChange={e => set('autoAnalyze', e.target.checked)} /></label>
      <p className="follow-up-rule-note full-width"><BellRinging size={16} />到期和逾期提醒在站内通知中显示。已有待办或近期已跟进的客户不会重复生成。</p>
      {lastRun && <div className="follow-up-rule-last full-width"><strong>最近检查 · {dateTime(lastRun.createdAt)}</strong><span>生成 {lastRun.createdCount} 条待办 · {({ QUEUED: 'AI 分析排队中', RUNNING: 'AI 分析进行中', COMPLETED: '已完成', FAILED: '需处理异常', CANCELLED: 'AI 分析已取消' } as Record<string, string>)[lastRun.status] ?? lastRun.status}</span>{lastRun.errorMessage && <p className="form-error">{lastRun.errorMessage}</p>}{lastRun.agentRunId && <Link className="text-link" to="/app/agent-runs" onClick={onClose}>查看 Agent 运行</Link>}</div>}
      <div className="full-width"><ErrorMessage error={save.error ?? run.error} />{savedMessage && <p className="follow-up-notice" role="status">{savedMessage}</p>}</div>
    </div>
    <footer><button className="button button-secondary" type="button" onClick={onClose} disabled={busy}>关闭</button><button className="button button-secondary" type="button" title={dirty ? '请先保存规则' : '同一天只检查一次'} disabled={!writable || busy || dirty || !rule.enabled || ['QUEUED', 'RUNNING'].includes(lastRun?.status ?? '')} onClick={() => run.mutate()}><ArrowClockwise size={16} />立即检查</button><button className="button button-primary" type="submit" disabled={!writable || busy}>{save.isPending ? '保存中…' : '保存规则'}</button></footer>
  </form>
}

function ruleValues(rule: FollowUpRule) { const { enabled, inactiveDays, runHour, maxCustomers, autoAnalyze, reminderMinutes, version } = rule; return { enabled, inactiveDays, runHour, maxCustomers, autoAnalyze, reminderMinutes, version } }
function ErrorMessage({ error }: { error: unknown }) { return error ? <p className="form-error" role="alert">{error instanceof Error ? error.message : '操作失败，请稍后重试'}</p> : null }
function taskTitle(task: FollowUp) { return typeof task.recommendedAction.title === 'string' ? task.recommendedAction.title : '跟进客户下一步动作' }
function localDateTime(value: string) { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16) }
function dateTime(value: string) { return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function dueLabel(value: string, now: number) { return `${Date.parse(value) <= now ? '已逾期 · ' : ''}${dateTime(value)}` }
