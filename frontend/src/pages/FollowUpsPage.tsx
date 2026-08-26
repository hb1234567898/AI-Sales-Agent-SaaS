import { CalendarCheck, Check, FunnelSimple, Plus } from '@phosphor-icons/react'
import { useMemo, useState } from 'react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { mockFollowUps } from '../data/mock-sales-data'
import { useIsGuest } from '../auth/use-auth'

const filters = ['全部', '今天', '已逾期']

export function FollowUpsPage() {
  const isGuest = useIsGuest()
  const [filter, setFilter] = useState('全部')
  const [completed, setCompleted] = useState<string[]>([])
  const tasks = useMemo(() => mockFollowUps.filter((task) => {
    if (filter === '今天') return task.due.startsWith('今天')
    if (filter === '已逾期') return task.status === '已逾期'
    return true
  }), [filter])

  return (
    <section className="module-page">
      <DemoPageHeader
        title="跟进任务"
        description="管理待办、逾期任务和 AI 建议的下一步动作。"
        actions={<button className="button button-primary" type="button" disabled><Plus size={16} />新建任务</button>}
      />

      <div className="module-stat-grid" aria-label="跟进任务指标">
        <div><span>今日待办</span><strong>12</strong><small>3 项临近截止</small></div>
        <div><span>已逾期</span><strong className="danger-value">1</strong><small>来自澄海数据</small></div>
        <div><span>本周已完成</span><strong>28</strong><small>完成率 82%</small></div>
        <div><span>AI 建议任务</span><strong>7</strong><small>2 项等待确认</small></div>
      </div>

      <section className="surface module-panel">
        <div className="module-toolbar">
          <div>
            <h2>任务队列</h2>
            <span>按截止时间和客户优先级排序</span>
          </div>
          <div className="segmented-filter" aria-label="任务筛选">
            <FunnelSimple size={14} aria-hidden />
            {filters.map((item) => <button className={filter === item ? 'is-active' : ''} type="button" key={item} onClick={() => setFilter(item)}>{item}</button>)}
          </div>
        </div>

        <div className="task-list">
          {tasks.map((task) => {
            const isCompleted = completed.includes(task.id)
            return (
              <article className={`task-row${isCompleted ? ' is-completed' : ''}`} key={task.id}>
                <button
                  className="task-check"
                  type="button"
                  aria-label={isCompleted ? `恢复 ${task.title}` : `完成 ${task.title}`}
                  disabled={isGuest}
                  title={isGuest ? '游客模式不能修改任务状态' : undefined}
                  onClick={() => setCompleted((current) => isCompleted ? current.filter((id) => id !== task.id) : [...current, task.id])}
                >
                  {isCompleted ? <Check size={13} weight="bold" /> : null}
                </button>
                <span className="task-main"><strong>{task.title}</strong><small>{task.company} · {task.contact}</small></span>
                <span className="task-source">{task.source}</span>
                <span className={`priority-badge priority-${task.priority === '高' ? 'high' : task.priority === '中' ? 'medium' : 'low'}`}>{task.priority}</span>
                <span className={task.status === '已逾期' ? 'task-due is-overdue' : 'task-due'}><CalendarCheck size={14} />{task.due}</span>
                <span className="task-owner">{task.owner}</span>
              </article>
            )
          })}
        </div>
      </section>
    </section>
  )
}
