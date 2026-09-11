import { CalendarCheck, Check, FunnelSimple, Plus } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { completeFollowUp, getFollowUps, type FollowUp } from '../api/follow-ups-api'
import { useIsGuest } from '../auth/use-auth'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

const filters = [
  { value: 'ALL', label: '全部' },
  { value: 'TODAY', label: '今天' },
  { value: 'OVERDUE', label: '已逾期' },
]

export function FollowUpsPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('ALL')
  const query = useQuery({ queryKey: ['follow-ups', filter], queryFn: () => getFollowUps(filter) })
  const tasks = query.data?.content ?? []
  const now = query.dataUpdatedAt
  const completeMutation = useMutation({
    mutationFn: completeFollowUp,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-ups'] }),
  })
  const todayCount = tasks.filter((task) => isTodayOrEarlier(task.dueAt)).length
  const overdueCount = tasks.filter((task) => new Date(task.dueAt).getTime() < now).length
  const aiCount = tasks.filter((task) => task.recommendedAction.source === 'AI_AGENT').length

  return (
    <section className="module-page">
      <DemoPageHeader
        title="跟进任务"
        description="管理待办、逾期任务和 AI 建议的下一步动作。"
        actions={<button className="button button-primary" type="button" disabled title="手动新建任务后续开放"><Plus size={16} />新建任务</button>}
      />

      <div className="module-stat-grid" aria-label="跟进任务指标">
        <div><span>当前待办</span><strong>{tasks.length}</strong><small>来自真实跟进队列</small></div>
        <div><span>今日/逾期待办</span><strong>{todayCount}</strong><small>需要优先处理</small></div>
        <div><span>已逾期</span><strong className={overdueCount > 0 ? 'danger-value' : undefined}>{overdueCount}</strong><small>超过计划跟进时间</small></div>
        <div><span>AI 建议任务</span><strong>{aiCount}</strong><small>审批通过后生成</small></div>
      </div>

      <section className="surface module-panel">
        <div className="module-toolbar">
          <div>
            <h2>任务队列</h2>
            <span>{query.isPending ? '正在读取跟进任务…' : '按截止时间和客户优先级排序'}</span>
          </div>
          <div className="segmented-filter" aria-label="任务筛选">
            <FunnelSimple size={14} aria-hidden />
            {filters.map((item) => <button className={filter === item.value ? 'is-active' : ''} type="button" key={item.value} onClick={() => setFilter(item.value)}>{item.label}</button>)}
          </div>
        </div>

        {completeMutation.isError ? <div className="audit-state is-error">{completeMutation.error.message}</div> : null}

        <div className="task-list">
          {tasks.map((task) => (
            <article className="task-row" key={task.id}>
              <button
                className="task-check"
                type="button"
                aria-label={`完成 ${taskTitle(task)}`}
                disabled={isGuest || completeMutation.isPending}
                title={isGuest ? '游客模式不能修改任务状态' : undefined}
                onClick={() => completeMutation.mutate(task.id)}
              >
                <Check size={13} weight="bold" />
              </button>
              <span className="task-main"><strong>{taskTitle(task)}</strong><small>{task.customerName} · {task.reason}</small></span>
              <span className="task-source">AI Agent</span>
              <span className={`priority-badge ${priorityClass(task.priority)}`}>{priorityLabel(task.priority)}</span>
              <span className={new Date(task.dueAt).getTime() < now ? 'task-due is-overdue' : 'task-due'}><CalendarCheck size={14} />{dueLabel(task.dueAt, now)}</span>
              <span className="task-owner">{task.ownerName ?? '未分配'}</span>
            </article>
          ))}
          {!query.isPending && tasks.length === 0 ? <div className="approval-empty"><Check size={24} /><strong>暂无跟进任务</strong><span>批准 Agent 建议后，任务会进入这里。</span></div> : null}
        </div>
      </section>
    </section>
  )
}

function taskTitle(task: FollowUp) {
  const title = task.recommendedAction.title
  return typeof title === 'string' ? title : '跟进客户下一步动作'
}

function priorityLabel(priority: number) {
  if (priority >= 80) return '高'
  if (priority >= 60) return '中'
  return '低'
}

function priorityClass(priority: number) {
  if (priority >= 80) return 'priority-high'
  if (priority >= 60) return 'priority-medium'
  return 'priority-low'
}

function isTodayOrEarlier(value: string) {
  const date = new Date(value)
  const tomorrow = new Date()
  tomorrow.setHours(24, 0, 0, 0)
  return date.getTime() < tomorrow.getTime()
}

function dueLabel(value: string, nowMs: number) {
  const date = new Date(value)
  const now = new Date(nowMs)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(date)
  target.setHours(0, 0, 0, 0)
  const dayDiff = Math.round((target.getTime() - today.getTime()) / 86_400_000)
  const time = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(date)
  if (date.getTime() < now.getTime()) return `已逾期 · ${time}`
  if (dayDiff === 0) return `今天 ${time}`
  if (dayDiff === 1) return `明天 ${time}`
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date)
}
