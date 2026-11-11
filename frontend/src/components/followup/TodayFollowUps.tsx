import { ArrowClockwise, ArrowRight, Buildings, CaretRight, Check, Checks, ClockCountdown, SpinnerGap } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { completeFollowUp, getFollowUpMetrics, getFollowUps, type FollowUp } from '../../api/follow-ups-api'
import { useAuth } from '../../auth/use-auth'
import './today-follow-ups.css'

const originLabels: Record<FollowUp['origin'], string> = {
  AI: 'AI 建议', RULE: '自动跟进', MANUAL: '手动任务', IMPORT: '导入记录',
}

export function TodayFollowUps() {
  const session = useAuth()
  const writable = ['OWNER', 'ADMIN', 'MANAGER', 'SALES'].includes(session.role)
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['follow-ups', 'workbench'], queryFn: () => getFollowUps('ALL', 0, 'MINE', 5), refetchInterval: 30_000 })
  const metrics = useQuery({ queryKey: ['follow-up-metrics', 'MINE'], queryFn: () => getFollowUpMetrics('MINE'), refetchInterval: 30_000 })
  const complete = useMutation({ mutationFn: (task: FollowUp) => completeFollowUp(task.id, task.version), onSuccess: async () => {
    await Promise.all([client.invalidateQueries({ queryKey: ['follow-ups'] }), client.invalidateQueries({ queryKey: ['follow-up-metrics'] }), client.invalidateQueries({ queryKey: ['follow-up-reminders'] })])
  } })
  return (
    <section className="surface priority-panel today-follow-ups" aria-labelledby="today-follow-ups-title">
      <header className="today-follow-up-header">
        <div className="today-follow-up-heading">
          <div className="today-follow-up-title">
            <Checks size={20} aria-hidden />
            <h2 id="today-follow-ups-title">我的跟进待办</h2>
            {session.role === 'GUEST' && <span className="today-follow-up-demo">演示任务</span>}
          </div>
          <div className="today-follow-up-summary" aria-label="待办统计">
            {metrics.data ? <>
              <span>今日到期 <strong>{metrics.data.today}</strong></span>
              <span className={metrics.data.overdue > 0 ? 'is-overdue' : ''}>已逾期 <strong>{metrics.data.overdue}</strong></span>
            </> : <span>{metrics.isError ? '统计暂不可用' : '按计划时间排序'}</span>}
          </div>
        </div>
        <Link className="text-link" to="/app/follow-ups">查看全部 <ArrowRight size={14} aria-hidden /></Link>
      </header>
      {query.isError ? (
        <div className="today-follow-up-state is-error" role="alert">
          <p>{query.error.message}</p>
          <button className="text-link" type="button" onClick={() => void query.refetch()} disabled={query.isFetching}>
            <ArrowClockwise size={14} aria-hidden />重试
          </button>
        </div>
      ) : query.isPending ? (
        <div className="today-follow-up-state" role="status"><SpinnerGap size={18} className="is-spinning" aria-hidden />正在读取待办…</div>
      ) : query.data?.content.length ? (
        <ul className="today-follow-up-list" aria-label="我的跟进任务">
          {query.data.content.map(task => {
            const title = typeof task.recommendedAction.title === 'string' ? task.recommendedAction.title : '跟进客户'
            const dueDate = new Date(task.dueAt)
            const overdue = dueDate.getTime() <= query.dataUpdatedAt
            const pending = complete.isPending && complete.variables?.id === task.id
            return (
              <li key={task.id} className={overdue ? 'is-overdue' : ''}>
                <button className="today-follow-up-check" type="button" title={session.role === 'GUEST' ? '演示任务不可修改' : writable ? '完成任务' : '当前角色无编辑权限'} aria-label={`完成 ${title}`} aria-busy={pending} disabled={!writable || complete.isPending} onClick={() => complete.mutate(task)}>
                  {pending ? <SpinnerGap size={14} className="is-spinning" aria-hidden /> : <Check size={14} aria-hidden />}
                </button>
                <Link className="today-follow-up-task" to={`/app/follow-ups?task=${encodeURIComponent(task.id)}`}>
                  <div className="today-follow-up-main">
                    <strong>{title}</strong>
                    <div className="today-follow-up-meta">
                      <span className="today-follow-up-customer"><Buildings size={13} aria-hidden />{task.customerName}</span>
                      <span className={`today-follow-up-source source-${task.origin.toLowerCase()}`}>{originLabels[task.origin]}</span>
                    </div>
                  </div>
                  <div className="today-follow-up-due">
                    <span className="today-follow-up-status"><ClockCountdown size={13} aria-hidden />{overdue ? '已逾期' : '待跟进'}</span>
                    <time dateTime={task.dueAt}>{dueDate.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time>
                  </div>
                  <CaretRight className="today-follow-up-chevron" size={15} aria-hidden />
                </Link>
              </li>
            )
          })}
        </ul>
      ) : (
        <div className="today-follow-up-empty"><span><Checks size={24} aria-hidden /></span><strong>暂无待办</strong><Link className="text-link" to="/app/follow-ups">安排下一次跟进 <ArrowRight size={14} aria-hidden /></Link></div>
      )}
      {complete.isError && <p className="today-follow-up-state is-error" role="alert">{complete.error.message}</p>}
    </section>
  )
}
