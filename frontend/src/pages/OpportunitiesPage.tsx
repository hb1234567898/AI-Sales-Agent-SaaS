import { ArrowClockwise, ArrowRight, Kanban, ListBullets, MagnifyingGlass, PencilSimple, Plus, WarningCircle, X } from '@phosphor-icons/react'
import { useDeferredValue, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { useCommercialRole } from '../components/commercial/commercial-utils'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { SelectField } from '../components/forms/SelectField'
import { useIsGuest } from '../auth/use-auth'
import { getCustomers, getCustomerOwners } from '../api/customers-api'
import { changeOpportunityStage, createOpportunity, forecastCategories, getOpportunities, getOpportunity, getOpportunityHistory, getOpportunityMetrics, opportunityStages, updateOpportunity, type ForecastCategory, type Opportunity, type OpportunityInput, type OpportunityStage } from '../api/opportunities-api'
import { mockOpportunities } from '../data/mock-crm-data'

const stages = Object.keys(opportunityStages) as OpportunityStage[]
const openStages: OpportunityStage[] = stages.filter((stage) => stage !== 'WON' && stage !== 'LOST')
const money = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 })
const demoOpportunities: Opportunity[] = mockOpportunities.map((item) => ({
  id: item.id, customerId: '', company: item.company, name: item.name, stage: item.stage,
  status: item.stage === 'WON' ? 'WON' : 'OPEN', amount: item.amount, probability: item.probability,
  ownerMemberId: item.owner, ownerName: item.owner, expectedCloseDate: null, nextStep: item.nextStep,
  forecastCategory: item.forecastCategory, closeReason: null,
  stageChangedAt: new Date(Date.now() - item.daysInStage * 86400000).toISOString(), updatedAt: '', version: 0,
}))
function daysInStage(item: Opportunity) { return Math.max(0, Math.floor((Date.now() - new Date(item.stageChangedAt).getTime()) / 86400000)) }
function compactMoney(value: number) { return `¥${(value / 10000).toFixed(1)}万` }
function errorText(error: unknown) { return error instanceof Error ? error.message : '请求失败，请重试' }

export function OpportunitiesPage() {
  const commercialRole = useCommercialRole()
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [view, setView] = useState<'BOARD' | 'LIST'>('BOARD')
  const [owner, setOwner] = useState('')
  const [stage, setStage] = useState('')
  const [search, setSearch] = useState('')
  const query = useDeferredValue(search.trim())
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Opportunity | null>(null)
  const [editor, setEditor] = useState<Opportunity | 'new' | null>(null)
  const [stageEditor, setStageEditor] = useState<Opportunity | null>(null)
  const [notice, setNotice] = useState('')
  const availability = useQuery({ queryKey: ['opportunities', 'availability'], queryFn: () => getOpportunities({ size: 1 }), enabled: !isGuest })
  const demo = isGuest || (availability.isSuccess && availability.data.totalElements === 0)
  const list = useQuery({ queryKey: ['opportunities', 'list', query, owner, stage, page], queryFn: () => getOpportunities({ query, owner, stage, page }), enabled: !isGuest && !demo })
  const metrics = useQuery({ queryKey: ['opportunities', 'metrics'], queryFn: getOpportunityMetrics, enabled: !isGuest && !demo })
  const owners = useQuery({ queryKey: ['customer-owners'], queryFn: getCustomerOwners, enabled: !isGuest })
  const selectedIsDemo = Boolean(selected?.id.startsWith('OPP-'))
  const detail = useQuery({ queryKey: ['opportunity', selected?.id], queryFn: () => getOpportunity(selected!.id), enabled: Boolean(selected) && !selectedIsDemo })
  const history = useQuery({ queryKey: ['opportunity-history', selected?.id], queryFn: () => getOpportunityHistory(selected!.id), enabled: Boolean(selected) && !selectedIsDemo })
  const current = selectedIsDemo ? selected : detail.data ?? selected
  const source = demo ? demoOpportunities : list.data?.content ?? []
  const visible = demo ? source.filter((item) => (!owner || item.ownerMemberId === owner) && (!stage || item.stage === stage) && `${item.name}${item.company}`.toLowerCase().includes(query.toLowerCase())) : source
  const active = demoOpportunities.filter((item) => item.status === 'OPEN')
  const stats = demo ? {
    activeCount: active.length, pipelineAmount: active.reduce((sum, item) => sum + (item.amount ?? 0), 0),
    weightedAmount: active.reduce((sum, item) => sum + (item.amount ?? 0) * (item.probability ?? 0) / 100, 0),
    stalledCount: active.filter((item) => daysInStage(item) >= 14).length,
  } : metrics.data
  const ownerOptions = demo
    ? [...new Set(demoOpportunities.map((item) => item.ownerName!))].map((name) => ({ value: name, label: name }))
    : (owners.data ?? []).map((item) => ({ value: item.id, label: item.name }))

  async function refresh(result?: Opportunity) {
    if (result) { queryClient.setQueryData(['opportunity', result.id], result); setSelected(result) }
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['opportunities'] }), queryClient.invalidateQueries({ queryKey: ['opportunity-history'] })])
  }
  const save = useMutation({
    mutationFn: (input: OpportunityInput) => editor && editor !== 'new' ? updateOpportunity(editor.id, input) : createOpportunity(input),
    onSuccess: async (result) => { setEditor(null); setOwner(''); setStage(''); setSearch(''); setPage(0); setNotice('商机已保存'); await refresh(result) },
  })
  const changeStage = useMutation({
    mutationFn: (input: { stage: OpportunityStage; reason: string | null }) => changeOpportunityStage(stageEditor!.id, { ...input, version: stageEditor!.version }),
    onSuccess: async (result) => { setStageEditor(null); setNotice('销售阶段已更新'); await refresh(result) },
  })
  const loadError = availability.error ?? list.error
  const loading = !demo && (availability.isPending || list.isPending)
  const boardStages = stage ? [stage as OpportunityStage] : stages

  return (
    <section className="module-page crm-page">
      <DemoPageHeader demo={demo} title="商机管道" description="管理销售机会、推进阶段和预计成交金额。" actions={<button className="button button-primary" type="button" disabled={isGuest} onClick={() => { save.reset(); setEditor('new') }}><Plus size={16} />新建商机</button>} />
      <div className="module-stat-grid" aria-label="商机指标">
        <div><span>活跃商机</span><strong>{stats?.activeCount ?? '—'}</strong><small>不含赢单、输单与归档</small></div>
        <div><span>管道金额</span><strong>{stats ? compactMoney(stats.pipelineAmount) : '—'}</strong><small>全部活跃商机金额</small></div>
        <div><span>加权金额</span><strong>{stats ? compactMoney(stats.weightedAmount) : '—'}</strong><small>按成交概率加权</small></div>
        <div><span>阶段停滞</span><strong className={stats?.stalledCount ? 'danger-value' : undefined}>{stats?.stalledCount ?? '—'}</strong><small>当前阶段超过 14 天</small></div>
      </div>
      <section className="surface module-panel opportunity-panel">
        <div className="module-toolbar">
          <div><h2>销售管道</h2><span>{demo ? '模拟商机' : `${list.data?.totalElements ?? 0} 条商机`}</span></div>
          <div className="toolbar-controls">
            <label className="compact-search"><MagnifyingGlass size={15} aria-hidden /><input value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} placeholder="搜索商机或客户" aria-label="搜索商机" /></label>
            <SelectField className="is-compact" value={owner} onChange={(value) => { setOwner(value); setPage(0) }} ariaLabel="筛选负责人" options={[{ value: '', label: '全部负责人' }, ...ownerOptions]} />
            <SelectField className="is-compact" value={stage} onChange={(value) => { setStage(value); setPage(0) }} ariaLabel="筛选阶段" options={[{ value: '', label: '全部阶段' }, ...stages.map((value) => ({ value, label: opportunityStages[value] }))]} />
            <div className="icon-segment" aria-label="商机视图"><button className={view === 'BOARD' ? 'is-active' : ''} type="button" onClick={() => setView('BOARD')} aria-label="看板视图"><Kanban size={16} /></button><button className={view === 'LIST' ? 'is-active' : ''} type="button" onClick={() => setView('LIST')} aria-label="列表视图"><ListBullets size={16} /></button></div>
          </div>
        </div>
        {notice && <div className="crm-inline-notice" role="status">{notice}<button type="button" onClick={() => setNotice('')}>关闭</button></div>}
        {loadError && !isGuest ? <div className="opportunity-state" role="alert"><WarningCircle size={20} /><p>{errorText(loadError)}</p><button className="button button-secondary" onClick={() => void refresh()}><ArrowClockwise size={16} />重试</button></div>
          : loading ? <p className="opportunity-state" role="status">正在读取商机…</p>
          : <>
            {view === 'BOARD' ? <div className="pipeline-board opportunity-live-board">{boardStages.map((value) => {
              const items = visible.filter((item) => item.stage === value)
              return <section className="pipeline-column" key={value} aria-label={opportunityStages[value]}>
                <header><span>{opportunityStages[value]}</span><strong>{items.length}</strong><small>{compactMoney(items.reduce((sum, item) => sum + (item.amount ?? 0), 0))}</small></header>
                <div>{items.length === 0 && <p className="opportunity-column-empty">暂无商机</p>}{items.map((item) => <button className="opportunity-card" type="button" key={item.id} onClick={() => setSelected(item)}>
                  <span className="opportunity-card-company">{item.company}</span><strong>{item.name}</strong>
                  <span className="opportunity-card-value">{money.format(item.amount ?? 0)}<small>{item.probability ?? 0}%</small></span>
                  <span className="opportunity-card-meta"><small>{item.ownerName ?? '未分配'}</small><small>{item.expectedCloseDate ?? '未设置'}</small></span>
                  {item.status === 'OPEN' && daysInStage(item) >= 14 && <span className="stalled-warning"><WarningCircle size={12} />已停留 {daysInStage(item)} 天</span>}
                </button>)}</div>
              </section>
            })}</div> : <div className="workspace-table-wrap"><table className="workspace-table opportunities-table"><thead><tr><th>商机</th><th>阶段</th><th>金额</th><th>概率</th><th>预测分类</th><th>预计成交</th><th>负责人</th><th>下一步</th></tr></thead><tbody>{visible.map((item) => <tr key={item.id}><td><button className="table-action" onClick={() => setSelected(item)}>{item.name}</button><small className="table-subcopy">{item.company}</small></td><td>{opportunityStages[item.stage]}</td><td>{money.format(item.amount ?? 0)}</td><td>{item.probability ?? 0}%</td><td>{forecastCategories[item.forecastCategory]}</td><td>{item.expectedCloseDate ?? '未设置'}</td><td>{item.ownerName ?? '未分配'}</td><td>{item.nextStep ?? '未设置'}</td></tr>)}</tbody></table></div>}
            {!visible.length && <p className="opportunity-state">没有符合条件的商机</p>}
            {!demo && list.data && list.data.totalPages > 1 && <footer className="table-pagination"><span>第 {page + 1} / {list.data.totalPages} 页</span><div><button disabled={list.data.first} onClick={() => setPage((value) => value - 1)}>上一页</button><button disabled={list.data.last} onClick={() => setPage((value) => value + 1)}>下一页</button></div></footer>}
          </>}
        {metrics.isError && !demo && <p className="form-error" role="alert">统计读取失败：{errorText(metrics.error)}</p>}
      </section>
      {current && <div className="crm-detail-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setSelected(null)}><aside className="crm-detail-panel opportunity-detail" role="dialog" aria-modal="true" aria-labelledby="opportunity-detail-title">
        <header><div><span>{current.company}</span><h2 id="opportunity-detail-title">{current.name}</h2></div><button className="icon-button" onClick={() => setSelected(null)} aria-label="关闭商机详情"><X size={20} /></button></header>
        {selectedIsDemo && <p className="opportunity-demo-note">模拟数据</p>}
        {detail.isError && !selectedIsDemo && <p className="form-error" role="alert">{errorText(detail.error)}</p>}
        <dl><div><dt>商机金额</dt><dd>{money.format(current.amount ?? 0)}</dd></div><div><dt>成交概率</dt><dd>{current.probability ?? 0}%</dd></div><div><dt>销售阶段</dt><dd>{opportunityStages[current.stage]}</dd></div><div><dt>预测分类</dt><dd>{forecastCategories[current.forecastCategory]}</dd></div><div><dt>负责人</dt><dd>{current.ownerName ?? '未分配'}</dd></div><div><dt>预计成交</dt><dd>{current.expectedCloseDate ?? '未设置'}</dd></div></dl>
        <section><span>下一步动作</span><p>{current.nextStep ?? '未设置'}</p></section>
        {current.closeReason && <section><span>关闭原因</span><p>{current.closeReason}</p></section>}
        {!selectedIsDemo && !isGuest && commercialRole.writer && current.status === 'OPEN' && !detail.isError && (commercialRole.manager || current.ownerMemberId === commercialRole.memberId) && <section><Link className="button button-secondary" to={`/app/quotes?opportunity=${current.id}`}>为此商机创建报价<ArrowRight size={15} /></Link></section>}
        {!selectedIsDemo && <section><h3>变更历史</h3>{history.isPending ? <p>正在读取…</p> : history.isError ? <p className="form-error" role="alert">{errorText(history.error)}</p> : history.data?.length ? <ol className="opportunity-history">{history.data.map((event) => <li key={event.id}><strong>{event.summary}</strong><span>{event.fromStage !== event.toStage && `${event.fromStage ? opportunityStages[event.fromStage] + ' → ' : ''}${opportunityStages[event.toStage]} · `}{event.actorName}</span><time>{new Date(event.createdAt).toLocaleString('zh-CN')}</time></li>)}</ol> : <p>暂无变更记录</p>}</section>}
        <footer><button className="button button-secondary" disabled={selectedIsDemo || isGuest || current.status !== 'OPEN' || detail.isError} onClick={() => { save.reset(); setEditor(current) }}><PencilSimple size={16} />编辑</button><button className="button button-primary" disabled={selectedIsDemo || isGuest || current.status !== 'OPEN' || detail.isError} onClick={() => { changeStage.reset(); setStageEditor(current) }}>变更阶段<ArrowRight size={15} /></button></footer>
      </aside></div>}
      {editor && <OpportunityEditor key={editor === 'new' ? 'new' : editor.id} value={editor === 'new' ? null : editor} busy={save.isPending} error={save.error} onClose={() => !save.isPending && setEditor(null)} onSubmit={(input) => save.mutate(input)} />}
      {stageEditor && <StageEditor value={stageEditor} busy={changeStage.isPending} error={changeStage.error} onClose={() => !changeStage.isPending && setStageEditor(null)} onSubmit={(input) => changeStage.mutate(input)} />}
    </section>
  )
}

function OpportunityEditor({ value, busy, error, onClose, onSubmit }: { value: Opportunity | null; busy: boolean; error: unknown; onClose: () => void; onSubmit: (input: OpportunityInput) => void }) {
  const [customerId, setCustomerId] = useState(value?.customerId ?? '')
  const [customerSearch, setCustomerSearch] = useState('')
  const query = useDeferredValue(customerSearch)
  const [name, setName] = useState(value?.name ?? '')
  const [ownerId, setOwnerId] = useState(value?.ownerMemberId ?? '')
  const [amount, setAmount] = useState(String(value?.amount ?? 0))
  const [probability, setProbability] = useState(String(value?.probability ?? 20))
  const [closeDate, setCloseDate] = useState(value?.expectedCloseDate ?? '')
  const [forecast, setForecast] = useState<ForecastCategory>(value?.forecastCategory ?? 'PIPELINE')
  const [nextStep, setNextStep] = useState(value?.nextStep ?? '')
  const customers = useQuery({ queryKey: ['customers', 'opportunity-options', query], queryFn: () => getCustomers({ query, page: 0, size: 50 }), enabled: !value })
  const owners = useQuery({ queryKey: ['customer-owners'], queryFn: getCustomerOwners })
  return <div className="admin-modal-backdrop opportunity-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="admin-modal opportunity-editor" role="dialog" aria-modal="true" aria-labelledby="opportunity-editor-title">
    <header><h2 id="opportunity-editor-title">{value ? '编辑商机' : '新建商机'}</h2><button className="icon-button" disabled={busy} onClick={onClose} aria-label="关闭"><X size={18} /></button></header>
    <form onSubmit={(event) => { event.preventDefault(); onSubmit({ customerId, name: name.trim(), ownerMemberId: ownerId, amount: Number(amount), probability: Number(probability), expectedCloseDate: closeDate || null, forecastCategory: forecast, nextStep: nextStep.trim() || null, ...(value ? { version: value.version } : {}) }) }}>
      {!value && <label><span>搜索客户</span><input value={customerSearch} onChange={(event) => { setCustomerSearch(event.target.value); setCustomerId('') }} /></label>}
      <label><span>关联客户</span><select required disabled={Boolean(value) || busy || (!value && customers.isPending)} value={customerId} onChange={(event) => setCustomerId(event.target.value)}><option value="">请选择客户</option>{value ? <option value={value.customerId}>{value.company}</option> : customers.data?.content.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}</select></label>
      {!value && customers.isSuccess && !customers.data.content.length && <p>未找到客户，请先在客户页创建。</p>}
      {customers.isError && <p className="form-error" role="alert">客户读取失败：{errorText(customers.error)}</p>}
      <label><span>商机名称</span><input required maxLength={255} value={name} disabled={busy} onChange={(event) => setName(event.target.value)} /></label>
      <label><span>负责人</span><select required value={ownerId} disabled={busy || owners.isPending} onChange={(event) => setOwnerId(event.target.value)}><option value="">请选择负责人</option>{owners.data?.map((owner) => <option value={owner.id} key={owner.id}>{owner.name}</option>)}</select></label>
      {owners.isError && <p className="form-error" role="alert">负责人读取失败：{errorText(owners.error)}</p>}
      <div className="opportunity-form-grid"><label><span>金额（元）</span><input required type="number" min="0" max="999999999999999" step="0.0001" value={amount} disabled={busy} onChange={(event) => setAmount(event.target.value)} /></label><label><span>成交概率（%）</span><input required type="number" min="0" max="100" step="1" value={probability} disabled={busy} onChange={(event) => setProbability(event.target.value)} /></label></div>
      <div className="opportunity-form-grid"><label><span>预计成交日期</span><input type="date" value={closeDate} disabled={busy} onChange={(event) => setCloseDate(event.target.value)} /></label><label><span>预测分类</span><select value={forecast} disabled={busy} onChange={(event) => setForecast(event.target.value as ForecastCategory)}>{Object.entries(forecastCategories).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></div>
      <label><span>下一步动作</span><textarea maxLength={1000} value={nextStep} disabled={busy} onChange={(event) => setNextStep(event.target.value)} /></label>
      {Boolean(error) && <p className="form-error" role="alert">{errorText(error)}</p>}
      <footer><button className="button button-secondary" type="button" disabled={busy} onClick={onClose}>取消</button><button className="button button-primary" type="submit" disabled={busy || !customerId || !ownerId || !name.trim() || owners.isError}>{busy ? '保存中…' : '保存商机'}</button></footer>
    </form>
  </section></div>
}

function StageEditor({ value, busy, error, onClose, onSubmit }: { value: Opportunity; busy: boolean; error: unknown; onClose: () => void; onSubmit: (input: { stage: OpportunityStage; reason: string | null }) => void }) {
  const next = openStages[openStages.indexOf(value.stage) + 1] ?? 'WON'
  const [stage, setStage] = useState<OpportunityStage>(next)
  const [reason, setReason] = useState('')
  return <div className="admin-modal-backdrop opportunity-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="admin-modal opportunity-editor" role="dialog" aria-modal="true" aria-labelledby="opportunity-stage-title"><header><h2 id="opportunity-stage-title">变更销售阶段</h2><button className="icon-button" disabled={busy} onClick={onClose} aria-label="关闭"><X size={18} /></button></header><form onSubmit={(event) => { event.preventDefault(); onSubmit({ stage, reason: reason.trim() || null }) }}><p>{value.name} · 当前阶段：{opportunityStages[value.stage]}</p><label><span>目标阶段</span><select value={stage} disabled={busy} onChange={(event) => setStage(event.target.value as OpportunityStage)}>{stages.filter((item) => item !== value.stage).map((item) => <option key={item} value={item}>{opportunityStages[item]}</option>)}</select></label><label><span>{stage === 'LOST' ? '输单原因' : '变更备注'}</span><textarea required={stage === 'LOST'} maxLength={1000} disabled={busy} value={reason} onChange={(event) => setReason(event.target.value)} /></label>{Boolean(error) && <p className="form-error" role="alert">{errorText(error)}</p>}<footer><button className="button button-secondary" type="button" disabled={busy} onClick={onClose}>取消</button><button className="button button-primary" type="submit" disabled={busy || (stage === 'LOST' && !reason.trim())}>{busy ? '保存中…' : '确认变更'}</button></footer></form></section></div>
}
