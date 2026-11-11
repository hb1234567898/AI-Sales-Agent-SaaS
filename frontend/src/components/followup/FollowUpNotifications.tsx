import { Bell, Check, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { getFollowUpReminders, readAllFollowUpReminders, readFollowUpReminder } from '../../api/follow-ups-api'
import './follow-up-notifications.css'

export function FollowUpNotifications({ pendingApprovals, guest }: { pendingApprovals: number; guest: boolean }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const id = useId()
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['follow-up-reminders'], queryFn: getFollowUpReminders, refetchInterval: 30_000 })
  const refresh = () => client.invalidateQueries({ queryKey: ['follow-up-reminders'] })
  const read = useMutation({ mutationFn: readFollowUpReminder, onSuccess: refresh })
  const readAll = useMutation({ mutationFn: readAllFollowUpReminders, onSuccess: refresh })
  const count = pendingApprovals + (query.data?.unreadCount ?? 0)
  const label = count > 0 ? `${count} 条待处理通知` : '暂无待处理通知'
  useEffect(() => {
    if (!open) return
    const outside = (event: PointerEvent) => { if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false) }
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() } }
    document.addEventListener('pointerdown', outside); document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape) }
  }, [open])
  return <div className="follow-up-notifications" ref={root}>
    <button ref={trigger} className={`icon-button notification-button${count ? ' has-unread' : ''}`} type="button" title={label} aria-label={label} aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? id : undefined} onClick={() => setOpen(value => !value)}><Bell size={19} />{count > 0 && <span className="notification-badge" aria-hidden>{count > 99 ? '99+' : count}</span>}</button>
    {open && <section className="follow-up-notification-popover" id={id} role="dialog" aria-label="待办通知">
      <header><h2>待办通知</h2><button className="icon-button" title="关闭通知" aria-label="关闭通知" onClick={() => { setOpen(false); trigger.current?.focus() }}><X size={17} /></button></header>
      {pendingApprovals > 0 && <Link className="follow-up-approval-link" to="/app/approvals" onClick={() => setOpen(false)}>{pendingApprovals} 条建议待审批</Link>}
      {query.isError ? <p className="form-error" role="alert">{query.error.message}<button className="text-link" onClick={() => void query.refetch()}>重试</button></p> : query.isPending ? <p className="follow-up-notification-empty">正在读取提醒…</p> : query.data?.items.length ? <div className="follow-up-notification-list">{query.data.items.map(item => <article key={item.id}>
        <Link to={`/app/follow-ups?task=${encodeURIComponent(item.followUpId)}`} onClick={() => { if (!guest) read.mutate(item.id); setOpen(false) }}><span className={item.kind === 'OVERDUE' ? 'is-overdue' : ''}>{item.kind === 'OVERDUE' ? '跟进已逾期' : '跟进即将到期'}</span><strong>{item.customerName} · {item.title}</strong><time>{new Date(item.dueAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time></Link>
        <button className="icon-button" type="button" title="标记已读" aria-label={`标记 ${item.title} 已读`} disabled={guest || read.isPending} onClick={() => read.mutate(item.id)}><Check size={16} /></button>
      </article>)}</div> : <p className="follow-up-notification-empty">暂无跟进提醒</p>}
      {(read.isError || readAll.isError) && <p className="form-error" role="alert">{read.error?.message ?? readAll.error?.message}</p>}
      <footer><Link className="text-link" to="/app/follow-ups" onClick={() => setOpen(false)}>全部跟进任务</Link><button className="text-link" disabled={guest || !query.data?.unreadCount || readAll.isPending} onClick={() => readAll.mutate()}>全部已读</button></footer>
    </section>}
  </div>
}
