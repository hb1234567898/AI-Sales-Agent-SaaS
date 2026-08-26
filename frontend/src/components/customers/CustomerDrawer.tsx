import { Buildings, Check, ClockCounterClockwise, User, X } from '@phosphor-icons/react'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import type { Customer, CustomerStage, CustomerUpsertInput, OwnerOption } from '../../api/customers-api'
import { SelectField } from '../forms/SelectField'
import { CustomerInteractionsPanel } from './CustomerInteractionsPanel'

const stageOptions: Array<{ value: CustomerStage; label: string }> = [
  { value: 'LEAD', label: '初步接触' },
  { value: 'QUALIFIED', label: '需求确认' },
  { value: 'DISCOVERY', label: '需求调研' },
  { value: 'DEMO', label: '产品试用' },
  { value: 'PROPOSAL', label: '方案确认' },
  { value: 'NEGOTIATION', label: '商务谈判' },
  { value: 'WON', label: '已成交' },
  { value: 'LOST', label: '已流失' },
]

interface CustomerFormState {
  name: string
  website: string
  industry: string
  employeeRange: string
  stage: CustomerStage
  ownerMemberId: string
  score: string
  estimatedValue: string
  nextAction: string
  nextFollowUpAt: string
  primaryContactName: string
  primaryContactEmail: string
  primaryContactPhone: string
}

const emptyForm: CustomerFormState = {
  name: '',
  website: '',
  industry: '',
  employeeRange: '',
  stage: 'LEAD',
  ownerMemberId: '',
  score: '',
  estimatedValue: '',
  nextAction: '',
  nextFollowUpAt: '',
  primaryContactName: '',
  primaryContactEmail: '',
  primaryContactPhone: '',
}

function toLocalDateTime(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function fromCustomer(customer?: Customer | null): CustomerFormState {
  if (!customer) return emptyForm
  return {
    name: customer.name,
    website: customer.website ?? '',
    industry: customer.industry ?? '',
    employeeRange: customer.employeeRange ?? '',
    stage: customer.stage,
    ownerMemberId: customer.ownerMemberId ?? '',
    score: customer.score?.toString() ?? '',
    estimatedValue: customer.estimatedValue?.toString() ?? '',
    nextAction: customer.nextAction ?? '',
    nextFollowUpAt: toLocalDateTime(customer.nextFollowUpAt),
    primaryContactName: customer.primaryContact?.name ?? '',
    primaryContactEmail: customer.primaryContact?.email ?? '',
    primaryContactPhone: customer.primaryContact?.phone ?? '',
  }
}

interface CustomerDrawerProps {
  customer?: Customer | null
  mode: 'create' | 'edit'
  owners: OwnerOption[]
  pending: boolean
  loading?: boolean
  error?: string | null
  onClose: () => void
  onSubmit: (input: CustomerUpsertInput) => Promise<void>
  readOnly?: boolean
}

export function CustomerDrawer({ customer, mode, owners, pending, loading, error, onClose, onSubmit, readOnly = false }: CustomerDrawerProps) {
  const dialogRef = useRef<HTMLElement>(null)
  const [form, setForm] = useState<CustomerFormState>(() => fromCustomer(customer))
  const [localError, setLocalError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'profile' | 'interactions'>('profile')

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', closeOnEscape)
    dialogRef.current?.focus()
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [onClose])

  function update<K extends keyof CustomerFormState>(key: K, value: CustomerFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (readOnly) return
    if (!form.name.trim()) {
      setLocalError('请填写客户名称')
      return
    }
    if (form.score && (Number(form.score) < 0 || Number(form.score) > 100)) {
      setLocalError('意向评分需要在 0 到 100 之间')
      return
    }

    setLocalError(null)
    try {
      await onSubmit({
        name: form.name.trim(),
        website: form.website.trim() || null,
        industry: form.industry.trim() || null,
        employeeRange: form.employeeRange.trim() || null,
        stage: form.stage,
        status: customer?.status ?? 'ACTIVE',
        source: customer?.source ?? 'MANUAL',
        ownerMemberId: form.ownerMemberId || null,
        score: form.score ? Number(form.score) : null,
        estimatedValue: form.estimatedValue ? Number(form.estimatedValue) : null,
        nextAction: form.nextAction.trim() || null,
        nextFollowUpAt: form.nextFollowUpAt ? new Date(form.nextFollowUpAt).toISOString() : null,
        primaryContactName: form.primaryContactName.trim() || null,
        primaryContactEmail: form.primaryContactEmail.trim() || null,
        primaryContactPhone: form.primaryContactPhone.trim() || null,
      })
    } catch {
      // Mutation error is rendered from the parent query state.
    }
  }

  return (
    <div className="drawer-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <aside className="customer-drawer" ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="customer-drawer-title" tabIndex={-1}>
        <header className="customer-drawer-header">
          <div className="drawer-title-icon"><Buildings size={18} /></div>
          <div>
            <span>{mode === 'create' ? '新建客户档案' : '客户档案'}</span>
            <h2 id="customer-drawer-title">{mode === 'create' ? '添加客户' : customer?.name ?? '加载中'}</h2>
          </div>
          <button className="drawer-close" type="button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </header>

        {mode === 'edit' && customer ? (
          <nav className="customer-drawer-tabs" aria-label="客户详情页面">
            <button className={activeTab === 'profile' ? 'is-active' : ''} type="button" onClick={() => setActiveTab('profile')}><Buildings size={14} />客户资料</button>
            <button className={activeTab === 'interactions' ? 'is-active' : ''} type="button" onClick={() => setActiveTab('interactions')}><ClockCounterClockwise size={14} />互动记录</button>
          </nav>
        ) : null}

        {loading ? <div className="drawer-loading">正在读取客户资料…</div> : (
          activeTab === 'interactions' && customer ? <CustomerInteractionsPanel customerId={customer.id} readOnly={readOnly} /> : <form className={`customer-form${readOnly ? ' is-readonly' : ''}`} onSubmit={(event) => void handleSubmit(event)}>
            {readOnly ? <p className="drawer-readonly-note">游客模式下客户资料仅供查看</p> : null}
            <fieldset className="customer-form-fields" disabled={readOnly}>
            <section className="form-section">
              <div className="form-section-title"><Buildings size={15} /><span>企业信息</span></div>
              <div className="customer-form-grid">
                <label className="field-span-2"><span>客户名称 <b>*</b></span><input value={form.name} onChange={(event) => update('name', event.target.value)} maxLength={255} placeholder="例如：云岚科技" autoFocus /></label>
                <label><span>所属行业</span><input value={form.industry} onChange={(event) => update('industry', event.target.value)} maxLength={120} placeholder="例如：企业服务" /></label>
                <label><span>企业规模</span><input value={form.employeeRange} onChange={(event) => update('employeeRange', event.target.value)} maxLength={40} placeholder="例如：100-499 人" /></label>
                <label className="field-span-2"><span>企业网站</span><input type="url" value={form.website} onChange={(event) => update('website', event.target.value)} maxLength={500} placeholder="https://example.com" /></label>
                <label><span>商机阶段</span><SelectField value={form.stage} onChange={(value) => update('stage', value as CustomerStage)} ariaLabel="选择商机阶段" options={stageOptions} /></label>
                <label><span>负责人</span><SelectField value={form.ownerMemberId} onChange={(value) => update('ownerMemberId', value)} ariaLabel="选择负责人" placeholder="暂未分配" options={[{ value: '', label: '暂未分配' }, ...owners.map((owner) => ({ value: owner.id, label: owner.name }))]} /></label>
              </div>
            </section>

            <section className="form-section">
              <div className="form-section-title"><User size={15} /><span>主要联系人</span></div>
              <div className="customer-form-grid">
                <label><span>姓名</span><input value={form.primaryContactName} onChange={(event) => update('primaryContactName', event.target.value)} maxLength={220} placeholder="联系人姓名" /></label>
                <label><span>手机号</span><input value={form.primaryContactPhone} onChange={(event) => update('primaryContactPhone', event.target.value)} maxLength={50} placeholder="手机号或国际号码" /></label>
                <label className="field-span-2"><span>邮箱</span><input type="email" value={form.primaryContactEmail} onChange={(event) => update('primaryContactEmail', event.target.value)} maxLength={320} placeholder="name@company.com" /></label>
              </div>
            </section>

            <section className="form-section">
              <div className="form-section-title"><Check size={15} /><span>销售推进</span></div>
              <div className="customer-form-grid">
                <label><span>意向评分</span><input type="number" min="0" max="100" value={form.score} onChange={(event) => update('score', event.target.value)} placeholder="0-100" /></label>
                <label><span>预计金额（元）</span><input type="number" min="0" step="0.01" value={form.estimatedValue} onChange={(event) => update('estimatedValue', event.target.value)} placeholder="0.00" /></label>
                <label className="field-span-2"><span>下次跟进时间</span><input type="datetime-local" value={form.nextFollowUpAt} onChange={(event) => update('nextFollowUpAt', event.target.value)} /></label>
                <label className="field-span-2"><span>下一步动作</span><textarea value={form.nextAction} onChange={(event) => update('nextAction', event.target.value)} maxLength={500} rows={3} placeholder="例如：发送方案并确认下一次评审时间" /></label>
              </div>
            </section>

            </fieldset>

            {(localError || error) ? <p className="form-error" role="alert">{localError ?? error}</p> : null}

            <footer className="customer-form-actions">
              <button className="button button-secondary" type="button" onClick={onClose} disabled={pending}>{readOnly ? '关闭' : '取消'}</button>
              {!readOnly ? <button className="button button-primary" type="submit" disabled={pending}>{pending ? '保存中…' : mode === 'create' ? '创建客户' : '保存修改'}</button> : null}
            </footer>
          </form>
        )}
      </aside>
    </div>
  )
}
