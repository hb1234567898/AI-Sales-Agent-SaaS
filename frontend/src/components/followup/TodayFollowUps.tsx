import { ArrowRight, Check, ClockCountdown } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { completeFollowUp, getFollowUpMetrics, getFollowUps, type FollowUp } from '../../api/follow-ups-api'
import { useAuth } from '../../auth/use-auth'
import './follow-up-notifications.css'

export function TodayFollowUps() {
  const session = useAuth()
  const writable = ['OWNER', 'ADMIN', 'MANAGER', 'SALES'].includes(session.role)
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['follow-ups', 'workbench'], queryFn: () => getFollowUps('ALL', 0, 'MINE', 5), refetchInterval: 30_000 })
  const metrics = useQuery({ queryKey: ['follow-up-metrics', 'MINE'], queryFn: () => getFollowUpMetrics('MINE'), refetchInterval: 30_000 })
  const complete = useMutation({ mutationFn: (task: FollowUp) => completeFollowUp(task.id, task.version), onSuccess: async () => {
    await Promise.all([client.invalidateQueries({ queryKey: ['follow-ups'] }), client.invalidateQueries({ queryKey: ['follow-up-metrics'] }), client.invalidateQueries({ queryKey: ['follow-up-reminders'] })])
  } })
  return <section className="surface priority-panel today-follow-ups">
    <div className="panel-header"><div><h2>我的跟进待办</h2><p>{session.role === 'GUEST' ? '演示任务' : '实时任务'} · {metrics.data ? `${metrics.data.today} 项今日到期，${metrics.data.overdue} 项已逾期` : '按计划时间排序'}</p></div><Link className="text-link" to="/app/follow-ups">查看全部 <ArrowRight size={14} /></Link></div>
    {query.isError ? <p className="audit-state is-error" role="alert">{query.error.message}</p> : query.isPending ? <p className="audit-state">正在读取待办…</p> : query.data?.content.length ? <div className="today-follow-up-list">{query.data.content.map(task => {
      const title = typeof task.recommendedAction.title === 'string' ? task.recommendedAction.title : '跟进客户'
      const overdue = Date.parse(task.dueAt) <= query.dataUpdatedAt
      return <article key={task.id}><button className="task-check" title="完成任务" aria-label={`完成 ${title}`} disabled={!writable || complete.isPending} onClick={() => complete.mutate(task)}><Check size={13} /></button><Link to={`/app/follow-ups?task=${encodeURIComponent(task.id)}`}><strong>{title}</strong><span>{task.customerName} · {task.origin === 'RULE' ? '自动跟进' : task.origin === 'AI' ? 'AI 建议' : '手动任务'}</span></Link><time className={overdue ? 'is-overdue' : ''}><ClockCountdown size={14} />{overdue ? '已逾期 · ' : ''}{new Date(task.dueAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time></article>
    })}</div> : <div className="approval-empty"><Check size={22} /><strong>暂无待办</strong><Link className="text-link" to="/app/follow-ups">安排下一次跟进</Link></div>}
    {complete.isError && <p className="audit-state is-error" role="alert">{complete.error.message}</p>}
  </section>
}
