import { ClockCounterClockwise, Crosshair, User, X } from '@phosphor-icons/react'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import type { CustomerSource } from '../../api/customers-api'
import type { Lead, LeadStatus, LeadUpsertInput, OwnerOption } from '../../api/leads-api'
import { CustomerInteractionsPanel } from '../customers/CustomerInteractionsPanel'
import { SelectField } from '../forms/SelectField'

const statusOptions: Array<{ value: LeadStatus; label: string }> = [
  { value: 'NEW', label: '新线索' }, { value: 'CONTACTED', label: '已联系' },
  { value: 'NURTURING', label: '培育中' }, { value: 'DISQUALIFIED', label: '已失效' },
]
const sourceOptions: Array<{ value: CustomerSource; label: string }> = [
  { value: 'MANUAL', label: '手工录入' }, { value: 'IMPORT', label: '批量导入' },
  { value: 'CHAT', label: '聊天识别' }, { value: 'CRM', label: 'CRM 同步' }, { value: 'API', label: 'API' },
]

interface FormState {
  company: string; industry: string; contactName: string; contactEmail: string; contactPhone: string
  contactTitle: string; source: CustomerSource; status: LeadStatus; ownerMemberId: string; score: string
  nextAction: string; nextFollowUpAt: string
}

function localDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function initial(lead?: Lead | null): FormState {
  return {
    company: lead?.company ?? '', industry: lead?.industry ?? '', contactName: lead?.contactName ?? '',
    contactEmail: lead?.contactEmail ?? '', contactPhone: lead?.contactPhone ?? '', contactTitle: lead?.contactTitle ?? '',
    source: lead?.source ?? 'MANUAL', status: lead?.status === 'QUALIFIED' ? 'NURTURING' : lead?.status ?? 'NEW',
    ownerMemberId: lead?.ownerMemberId ?? '', score: lead?.score?.toString() ?? '', nextAction: lead?.nextAction ?? '',
    nextFollowUpAt: localDateTime(lead?.nextFollowUpAt),
  }
}

export function LeadDrawer({ lead, owners, pending, loading, error, readOnly, onClose, onSubmit }: {
  lead?: Lead | null; owners: OwnerOption[]; pending: boolean; loading?: boolean; error?: string | null; readOnly: boolean
  onClose: () => void; onSubmit: (input: LeadUpsertInput) => Promise<void>
}) {
  const ref = useRef<HTMLElement>(null)
  const [form, setForm] = useState(() => initial(lead))
  const [tab, setTab] = useState<'profile' | 'interactions'>('profile')
  const [localError, setLocalError] = useState<string | null>(null)
  const edit = Boolean(lead)

  useEffect(() => {
    const previous = document.body.style.overflow
    const escape = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.body.style.overflow = 'hidden'; document.addEventListener('keydown', escape); ref.current?.focus()
    return () => { document.body.style.overflow = previous; document.removeEventListener('keydown', escape) }
  }, [onClose])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((current) => ({ ...current, [key]: value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!form.company.trim() || !form.contactName.trim()) return setLocalError('请填写企业名称和联系人')
    if (form.score && (Number(form.score) < 0 || Number(form.score) > 100)) return setLocalError('评分必须在 0 到 100 之间')
    setLocalError(null)
    await onSubmit({
      company: form.company.trim(), industry: form.industry.trim() || null, contactName: form.contactName.trim(),
      contactEmail: form.contactEmail.trim() || null, contactPhone: form.contactPhone.trim() || null,
      contactTitle: form.contactTitle.trim() || null, source: form.source, status: form.status,
      ownerMemberId: form.ownerMemberId || null, score: form.score ? Number(form.score) : null,
      nextAction: form.nextAction.trim() || null,
      nextFollowUpAt: form.nextFollowUpAt ? new Date(form.nextFollowUpAt).toISOString() : null,
    })
  }

  return <div className="drawer-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <aside className="customer-drawer" ref={ref} role="dialog" aria-modal="true" aria-labelledby="lead-drawer-title" tabIndex={-1}>
      <header className="customer-drawer-header"><div className="drawer-title-icon"><Crosshair size={18} /></div><div><span>{edit ? '线索档案' : '录入销售线索'}</span><h2 id="lead-drawer-title">{edit ? lead?.company : '新建线索'}</h2></div><button className="drawer-close" type="button" onClick={onClose} aria-label="关闭"><X size={18} /></button></header>
      {edit ? <nav className="customer-drawer-tabs" aria-label="线索详情页面"><button className={tab === 'profile' ? 'is-active' : ''} type="button" onClick={() => setTab('profile')}><Crosshair size={14} />线索资料</button><button className={tab === 'interactions' ? 'is-active' : ''} type="button" onClick={() => setTab('interactions')}><ClockCounterClockwise size={14} />跟进记录</button></nav> : null}
      {loading ? <div className="drawer-loading">正在读取线索…</div> : tab === 'interactions' && lead ? <CustomerInteractionsPanel customerId={lead.id} readOnly={readOnly} /> :
        <form className="customer-form" onSubmit={(event) => void submit(event)}>
          <fieldset className="customer-form-fields" disabled={readOnly}>
            <section className="form-section"><div className="form-section-title"><Crosshair size={15} /><span>线索信息</span></div><div className="customer-form-grid">
              <label className="field-span-2"><span>企业名称 <b>*</b></span><input autoFocus value={form.company} onChange={(e) => set('company', e.target.value)} maxLength={255} /></label>
              <label><span>所属行业</span><input value={form.industry} onChange={(e) => set('industry', e.target.value)} maxLength={120} /></label>
              <label><span>来源</span><SelectField value={form.source} onChange={(value) => set('source', value as CustomerSource)} ariaLabel="选择线索来源" options={sourceOptions} /></label>
              <label><span>状态</span><SelectField value={form.status} onChange={(value) => set('status', value as LeadStatus)} ariaLabel="选择线索状态" options={statusOptions} /></label>
              <label><span>负责人</span><SelectField value={form.ownerMemberId} onChange={(value) => set('ownerMemberId', value)} ariaLabel="选择负责人" options={[{ value: '', label: '暂未分配' }, ...owners.map((owner) => ({ value: owner.id, label: owner.name }))]} /></label>
            </div></section>
            <section className="form-section"><div className="form-section-title"><User size={15} /><span>联系人</span></div><div className="customer-form-grid">
              <label><span>姓名 <b>*</b></span><input value={form.contactName} onChange={(e) => set('contactName', e.target.value)} maxLength={220} /></label>
              <label><span>职位</span><input value={form.contactTitle} onChange={(e) => set('contactTitle', e.target.value)} maxLength={120} /></label>
              <label><span>手机号</span><input value={form.contactPhone} onChange={(e) => set('contactPhone', e.target.value)} maxLength={50} /></label>
              <label><span>邮箱</span><input type="email" value={form.contactEmail} onChange={(e) => set('contactEmail', e.target.value)} maxLength={320} /></label>
            </div></section>
            <section className="form-section"><div className="form-section-title"><ClockCounterClockwise size={15} /><span>销售推进</span></div><div className="customer-form-grid">
              <label><span>线索评分</span><input type="number" min="0" max="100" value={form.score} onChange={(e) => set('score', e.target.value)} /></label>
              <label><span>下次跟进</span><input type="datetime-local" value={form.nextFollowUpAt} onChange={(e) => set('nextFollowUpAt', e.target.value)} /></label>
              <label className="field-span-2"><span>下一步动作</span><textarea rows={3} value={form.nextAction} onChange={(e) => set('nextAction', e.target.value)} maxLength={500} /></label>
            </div></section>
          </fieldset>
          {(localError || error) ? <p className="form-error" role="alert">{localError ?? error}</p> : null}
          <footer className="customer-form-actions"><button className="button button-secondary" type="button" onClick={onClose}>{readOnly ? '关闭' : '取消'}</button>{!readOnly ? <button className="button button-primary" type="submit" disabled={pending}>{pending ? '保存中…' : edit ? '保存修改' : '创建线索'}</button> : null}</footer>
        </form>}
    </aside>
  </div>
}
