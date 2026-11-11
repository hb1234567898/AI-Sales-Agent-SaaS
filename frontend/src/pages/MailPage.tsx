import { ArrowClockwise, ArrowLeft, Brain, Check, EnvelopeSimple, Paperclip, PencilSimple, ShieldCheck, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useDeferredValue, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAuth } from '../auth/use-auth'
import { getMailAccount, getMailMessage, getMailMessages, getMailThread, handleMail, linkMail, analyzeMail, replyMail, syncMail, type MailMessage } from '../api/mail-api'
import { getFollowUpOptions } from '../api/follow-ups-api'
import { getCustomerFiles } from '../api/files-api'
import { approveApproval, getApproval, rejectApproval } from '../api/approvals-api'
import { SelectField } from '../components/forms/SelectField'
import './mail.css'

const filters = [{ value: 'PENDING', label: '待回复' }, { value: 'UNMATCHED', label: '待关联' }, { value: 'CLOSED', label: '已处理' }, { value: 'SENT', label: '已发送' }, { value: 'ALL', label: '全部' }]
const statuses: Record<string, string> = { RECEIVED: '待回复', UNMATCHED: '待关联', HANDLED: '已处理', REPLIED: '已回复', SENT: '已发送', SENDING: '发送中', UNKNOWN: '发送结果需核对', FAILED: '发送失败', IGNORED: '已忽略' }
export function MailPage() {
  const actor = useAuth(), client = useQueryClient()
  const [filter, setFilter] = useState('PENDING'), [page, setPage] = useState(0)
  const [params, setParams] = useSearchParams(), id = params.get('message')
  const account = useQuery({ queryKey: ['mail-account'], queryFn: getMailAccount, refetchInterval: 30_000 })
  const list = useQuery({ queryKey: ['mail-messages', filter, page], queryFn: () => getMailMessages(filter, page), refetchInterval: 30_000 })
  const refresh = async () => { await Promise.all(['mail-messages', 'mail-message', 'mail-thread', 'mail-account', 'follow-ups', 'follow-up-metrics', 'follow-up-reminders', 'approvals', 'customer-interactions'].map(key => client.invalidateQueries({ queryKey: [key] }))) }
  const sync = useMutation({ mutationFn: syncMail, onSuccess: refresh })
  const choose = (id: string | null) => { const next = new URLSearchParams(params); if (id) next.set('message', id); else next.delete('message'); setParams(next) }
  return <section className="module-page mail-page">
    <header className="page-heading module-heading"><div><p className="eyebrow">客户沟通{actor.role === 'GUEST' ? ' · 演示数据' : ''}</p><h1>客户邮件</h1><p>{account.data?.lastSyncAt ? `最近同步 ${date(account.data.lastSyncAt)}` : '邮件往来与客户回复'}</p></div><div className="page-actions"><Link className="button button-secondary" to="/app/settings#mailbox-settings">收信配置</Link><button className="button button-primary" disabled={!['OWNER', 'ADMIN'].includes(actor.role) || !account.data?.enabled || sync.isPending || account.data?.syncing} onClick={() => sync.mutate()}><ArrowClockwise size={16} />{sync.isPending || account.data?.syncing ? '同步中…' : '同步邮件'}</button></div></header>
    <ErrorMessage error={sync.error ?? account.error} />
    {sync.data && <p className="mail-feedback" role="status">{sync.data.busy ? '邮箱正在同步，请稍后刷新' : `新增 ${sync.data.imported} 封邮件，其中 ${sync.data.unmatched} 封待关联`}</p>}
    {account.data?.lastError && <p className="form-error" role="alert">{account.data.lastError}</p>}
    <div className="mail-toolbar"><div className="segmented-filter" aria-label="邮件筛选">{filters.map(f => <button className={filter === f.value ? 'is-active' : ''} key={f.value} onClick={() => { setFilter(f.value); setPage(0) }}>{f.label}</button>)}</div><span>{list.data?.totalElements ?? 0} 封</span></div>
    <div className={`mail-workspace${id ? ' has-selection' : ''}`}>
      <section className="mail-list" aria-label="邮件列表">
        {list.isError ? <div className="audit-state is-error" role="alert">{list.error.message}<button className="text-link" onClick={() => void list.refetch()}>重试</button></div> : list.isPending ? <p className="audit-state">正在读取邮件…</p> : !list.data.content.length ? <div className="mail-empty"><EnvelopeSimple size={28} /><strong>暂无邮件</strong><span>{account.data?.enabled ? '同步后查看客户来信' : '等待管理员配置并开启收信'}</span></div> : list.data.content.map(mail => <button key={mail.id} className={`mail-list-item${mail.id === id ? ' is-selected' : ''}`} onClick={() => choose(mail.id)}><span><strong>{mail.customerName ?? mail.fromAddress}</strong><time>{shortDate(mail.occurredAt)}</time></span><b>{mail.subject}</b><p>{mail.bodyText}</p><small className={mail.status === 'UNKNOWN' ? 'is-warning' : ''}>{statuses[mail.status] ?? mail.status}{mail.attachmentNames?.length ? ` · ${mail.attachmentNames.length} 个附件` : ''}</small></button>)}
        <footer className="mail-list-pagination"><button className="icon-button" title="上一页" aria-label="上一页邮件" disabled={page === 0 || list.isFetching} onClick={() => setPage(p => p - 1)}><ArrowLeft size={16} /></button><span>{page + 1} / {Math.max(1, list.data?.totalPages ?? 1)}</span><button className="icon-button" title="下一页" aria-label="下一页邮件" disabled={!list.data || list.data.last || list.isFetching} onClick={() => setPage(p => p + 1)}><ArrowLeft style={{ transform: 'rotate(180deg)' }} size={16} /></button></footer>
      </section>
      {id ? <MailDetail key={id} id={id} onBack={() => choose(null)} refresh={refresh} /> : <div className="mail-detail mail-empty"><EnvelopeSimple size={32} /><span>选择一封邮件</span></div>}
    </div>
  </section>
}
function MailDetail({ id, onBack, refresh }: { id: string; onBack: () => void; refresh: () => Promise<void> }) {
  const actor = useAuth(), writable = ['OWNER', 'ADMIN', 'MANAGER', 'SALES'].includes(actor.role)
  const query = useQuery({ queryKey: ['mail-message', id], queryFn: () => getMailMessage(id), refetchInterval: 15_000 })
  const [replying, setReplying] = useState(false), [reviewing, setReviewing] = useState(false)
  const handle = useMutation({ mutationFn: () => handleMail(id, query.data!.version), onSuccess: refresh })
  const analysis = useMutation({ mutationFn: () => analyzeMail(id) })
  const thread = useQuery({ queryKey: ['mail-thread', id], queryFn: () => getMailThread(id), enabled: Boolean(query.data), refetchInterval: 30_000 })
  const mail = query.data
  const pending = mail?.approvalStatus === 'PENDING' || (mail?.approvalStatus === 'APPROVED' && ['APPROVED', 'EXECUTING'].includes(mail.actionStatus ?? ''))
  return <section className="mail-detail" aria-label="邮件详情"><header className="mail-detail-toolbar"><button className="icon-button" aria-label="返回邮件列表" title="返回邮件列表" onClick={onBack}><ArrowLeft size={18} /></button><span>{mail ? statuses[mail.status] ?? mail.status : '邮件详情'}</span>{mail?.customerName && <span className="mail-customer">{mail.customerName}</span>}</header>
    {query.isError ? <p className="audit-state is-error" role="alert">{query.error.message}</p> : !mail ? <p className="audit-state">正在读取正文…</p> : <div className="mail-detail-scroll">
      <div className="mail-envelope"><h2>{mail.subject}</h2><dl><dt>发件人</dt><dd>{mail.fromAddress}</dd><dt>收件人</dt><dd>{mail.recipients.join('、')}</dd><dt>时间</dt><dd>{date(mail.occurredAt)}</dd></dl></div>
      {['UNKNOWN', 'SENDING'].includes(mail.status) && <p className="mail-warning">{mail.status === 'UNKNOWN' ? '请到原邮箱核对是否已发出。系统不会自动重发。' : '发送记录处理中，请勿重复提交。'}</p>}
      {mail.deliveryUncertain && <p className="mail-warning">此前回复的发送结果尚未确认，请到原邮箱核对。确认后可标记已处理，不能直接重复发送。</p>}
      <pre className="mail-body">{mail.bodyText || '（无文本正文）'}</pre>
      {Boolean(mail.attachmentNames?.length) && <div className="mail-attachments">{mail.attachmentNames.map((name, i) => <span key={`${i}-${name}`}><Paperclip size={14} />{name}</span>)}<small>来信附件请在原邮箱查看，不会自动执行或导入知识库。</small></div>}
      {mail.status === 'UNMATCHED' && writable && ['OWNER', 'ADMIN', 'MANAGER'].includes(actor.role) && <LinkCustomer mail={mail} refresh={refresh} />}
      {mail.status === 'UNMATCHED' && writable && ['OWNER', 'ADMIN', 'MANAGER'].includes(actor.role) && <div className="mail-detail-actions"><button className="button button-secondary" disabled={handle.isPending} onClick={() => handle.mutate()}><X size={16} />忽略邮件</button></div>}
      {mail.customerId && mail.direction === 'INBOUND' && <div className="mail-detail-actions"><button className="button button-secondary" disabled={!writable || analysis.isPending} onClick={() => analysis.mutate()}><Brain size={16} />{analysis.isPending ? '分析中…' : '分析客户意图'}</button>{mail.status === 'RECEIVED' && <><button className="button button-secondary" disabled={!writable || pending || handle.isPending} onClick={() => handle.mutate()}><Check size={16} />标记已处理</button><button className="button button-primary" disabled={!writable || pending || mail.deliveryUncertain} onClick={() => setReplying(value => !value)}><PencilSimple size={16} />撰写回复</button></>}</div>}
      <ErrorMessage error={handle.error ?? analysis.error} />
      {analysis.data && <section className="mail-analysis"><h3>客户意图分析</h3><p>{analysis.data.summary}</p><p><strong>建议下一步：</strong>{analysis.data.suggestedNextAction ?? '待确认'}</p>{analysis.data.risks.length > 0 && <p>风险：{analysis.data.risks.join('；')}</p>}</section>}
      {mail.replyApprovalId && <div className="mail-approval-state"><ShieldCheck size={18} /><span>{pending ? '回复已提交审批' : mail.actionStatus === 'SUCCEEDED' ? '回复发送已完成' : mail.approvalStatus === 'REJECTED' ? '回复审批已拒绝，可重新撰写' : '查看回复审批与发送结果'}</span><button className="button button-secondary" onClick={() => setReviewing(true)}>审核回复</button></div>}
      {replying && mail.status === 'RECEIVED' && !pending && <ReplyForm mail={mail} onCancel={() => setReplying(false)} onSuccess={async () => { setReplying(false); setReviewing(true); await refresh() }} />}
      {thread.data && thread.data.length > 1 && <details className="mail-thread"><summary>同一邮件会话 · {thread.data.length} 封</summary>{[...thread.data].reverse().filter(m => m.id !== id).map(m => <article key={m.id}><strong>{m.direction === 'OUTBOUND' ? '发出' : '收到'} · {m.subject}</strong><time>{date(m.occurredAt)}</time><pre>{m.bodyText}</pre></article>)}</details>}
      <ErrorMessage error={thread.error} />
    </div>}
    {reviewing && mail?.replyApprovalId && <ReplyReview approvalId={mail.replyApprovalId} writable={writable} onClose={() => setReviewing(false)} refresh={refresh} />}
  </section>
}
function LinkCustomer({ mail, refresh }: { mail: MailMessage; refresh: () => Promise<void> }) {
  const [search, setSearch] = useState(''), [customer, setCustomer] = useState(''), keyword = useDeferredValue(search)
  const options = useQuery({ queryKey: ['follow-up-options', keyword], queryFn: () => getFollowUpOptions(keyword) })
  const link = useMutation({ mutationFn: () => linkMail(mail.id, customer, mail.version), onSuccess: refresh })
  return <form className="mail-link-form" onSubmit={e => { e.preventDefault(); link.mutate() }}><h3>关联客户</h3><label><span>查找客户</span><input type="search" maxLength={200} value={search} onChange={e => { setSearch(e.target.value); setCustomer('') }} /></label><SelectField ariaLabel="关联邮件客户" value={customer} options={options.data?.customers.map(c => ({ value: c.id, label: c.name })) ?? []} disabled={options.isPending || link.isPending} onChange={setCustomer} /><ErrorMessage error={link.error ?? options.error} /><button className="button button-primary" disabled={!customer || link.isPending}>确认关联</button></form>
}
function ReplyForm({ mail, onCancel, onSuccess }: { mail: MailMessage; onCancel: () => void; onSuccess: () => Promise<void> }) {
  const [subject, setSubject] = useState(() => /^re:/i.test(mail.subject) ? mail.subject : `Re: ${mail.subject}`.slice(0, 500))
  const [body, setBody] = useState(''), [attachments, setAttachments] = useState<string[]>([])
  const files = useQuery({ queryKey: ['customer-files', mail.customerId], queryFn: () => getCustomerFiles(mail.customerId!) })
  const submit = useMutation({ mutationFn: () => replyMail(mail.id, { subject: subject.trim(), body: body.trim(), attachmentIds: attachments, version: mail.version }), onSuccess })
  return <form className="mail-reply-form" onSubmit={e => { e.preventDefault(); submit.mutate() }}><header><h3>撰写回复</h3><button type="button" className="icon-button" aria-label="关闭回复" title="关闭回复" disabled={submit.isPending} onClick={onCancel}><X size={16} /></button></header><p>收件人：{mail.fromAddress}</p><label><span>回复主题</span><input required maxLength={500} value={subject} disabled={submit.isPending} onChange={e => setSubject(e.target.value)} /></label><label><span>回复正文</span><textarea autoFocus required maxLength={20000} rows={8} value={body} disabled={submit.isPending} onChange={e => setBody(e.target.value)} /></label>
    {Boolean(files.data?.length) && <fieldset><legend>客户附件</legend>{files.data!.map(file => <label key={file.id}><input type="checkbox" checked={attachments.includes(file.id)} disabled={submit.isPending || (attachments.length >= 10 && !attachments.includes(file.id))} onChange={e => setAttachments(old => e.target.checked ? [...old, file.id] : old.filter(id => id !== file.id))} /><span>{file.filename}</span></label>)}</fieldset>}
    <ErrorMessage error={submit.error ?? files.error} /><footer><button className="button button-secondary" type="button" disabled={submit.isPending} onClick={onCancel}>取消</button><button className="button button-primary" disabled={submit.isPending || !subject.trim() || !body.trim()}><ShieldCheck size={16} />{submit.isPending ? '提交中…' : '提交回复审批'}</button></footer></form>
}
function ReplyReview({ approvalId, writable, onClose, refresh }: { approvalId: string; writable: boolean; onClose: () => void; refresh: () => Promise<void> }) {
  const ref = useRef<HTMLDialogElement>(null)
  const query = useQuery({ queryKey: ['approval', approvalId], queryFn: () => getApproval(approvalId), refetchInterval: 10_000 })
  const decision = useMutation({ mutationFn: (approve: boolean) => approve ? approveApproval(query.data!) : rejectApproval(query.data!), onSuccess: async result => { await query.refetch(); await refresh(); return result } })
  useEffect(() => { const dialog = ref.current!, previous = document.activeElement; dialog.showModal(); return () => { dialog.close(); if (previous instanceof HTMLElement) previous.focus() } }, [])
  const item = query.data
  return <dialog ref={ref} className="mail-review-dialog" aria-label="审核邮件回复" onCancel={e => { e.preventDefault(); if (!decision.isPending) onClose() }}><header><h2>审核邮件回复</h2><button className="icon-button" aria-label="关闭审核" title="关闭审核" disabled={decision.isPending} onClick={onClose}><X size={18} /></button></header><div className="mail-review-body">{query.isPending ? <p>正在读取审批快照…</p> : item ? <><p>收件人：{String(item.preview.to ?? '')}</p><strong>{String(item.preview.subject ?? '')}</strong><pre>{String(item.preview.body ?? '')}</pre>{Array.isArray(item.preview.attachments) && <p>附件：{item.preview.attachments.map((a: { name?: string }) => a.name ?? '附件').join('、') || '无'}</p>}<p>{item.status === 'PENDING' ? '高风险操作 · 批准后将真实发送邮件' : item.actionStatus === 'SUCCEEDED' ? '邮件已发送' : item.status === 'REJECTED' ? '已拒绝' : `发送状态：${item.actionStatus}`}</p>{item.failureMessage && <p className="form-error">{item.failureMessage}</p>}</> : null}<ErrorMessage error={query.error ?? decision.error} /></div><footer><button className="button button-secondary" disabled={!writable || item?.status !== 'PENDING' || decision.isPending} onClick={() => decision.mutate(false)}>拒绝</button><button className="button button-primary" disabled={!writable || item?.status !== 'PENDING' || decision.isPending} onClick={() => decision.mutate(true)}>{decision.isPending ? '处理中…' : '批准并发送'}</button></footer></dialog>
}
function ErrorMessage({ error }: { error: unknown }) { return error ? <p className="form-error" role="alert">{error instanceof Error ? error.message : '请求失败，请重试'}</p> : null }
function date(value: string) { return new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
function shortDate(value: string) { return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit' }) }
