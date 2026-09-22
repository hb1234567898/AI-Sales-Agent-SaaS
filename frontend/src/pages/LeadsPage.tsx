import { ArrowSquareOut, CaretLeft, CaretRight, FunnelSimple, MagnifyingGlass, PencilSimple, Plus, SpinnerGap, UploadSimple, UserPlus, UsersThree, WarningCircle, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, useDeferredValue, useRef, useState } from 'react'
import { assignLead, convertLead, createLead, getLead, getLeadMetrics, getLeadOwners, getLeads, importLeads, updateLead, type Lead, type LeadConversionInput, type LeadStatus, type LeadUpsertInput } from '../api/leads-api'
import { useAuth, useIsGuest } from '../auth/use-auth'
import { LeadDrawer } from '../components/leads/LeadDrawer'
import { SelectField } from '../components/forms/SelectField'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

const statusLabels: Record<LeadStatus, string> = { NEW: '新线索', CONTACTED: '已联系', NURTURING: '培育中', QUALIFIED: '已转化', DISQUALIFIED: '已失效' }
const statusOptions = [{ value: '', label: '全部状态' }, ...Object.entries(statusLabels).map(([value, label]) => ({ value, label }))]
type DrawerState = { mode: 'create' } | { mode: 'edit'; id: string }

export function LeadsPage() {
  const isGuest = useIsGuest()
  const { memberId } = useAuth()
  const queryClient = useQueryClient()
  const importRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query.trim())
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [drawer, setDrawer] = useState<DrawerState | null>(null)
  const [conversion, setConversion] = useState<Lead | null>(null)
  const [notice, setNotice] = useState('')

  const leadsQuery = useQuery({ queryKey: ['leads', deferredQuery, status, page], queryFn: () => getLeads({ query: deferredQuery, status: status ? status as LeadStatus : undefined, page, size: 20 }) })
  const metricsQuery = useQuery({ queryKey: ['lead-metrics'], queryFn: getLeadMetrics })
  const ownersQuery = useQuery({ queryKey: ['lead-owners'], queryFn: getLeadOwners })
  const selectedQuery = useQuery({ queryKey: ['lead', drawer?.mode === 'edit' ? drawer.id : null], queryFn: () => getLead((drawer as { mode: 'edit'; id: string }).id), enabled: drawer?.mode === 'edit' })
  const refresh = async () => Promise.all([queryClient.invalidateQueries({ queryKey: ['leads'] }), queryClient.invalidateQueries({ queryKey: ['lead-metrics'] }), queryClient.invalidateQueries({ queryKey: ['customers'] }), queryClient.invalidateQueries({ queryKey: ['customer-metrics'] })])

  const saveMutation = useMutation({ mutationFn: ({ state, input }: { state: DrawerState; input: LeadUpsertInput }) => state.mode === 'create' ? createLead(input) : updateLead(state.id, input), onSuccess: async (lead) => { queryClient.setQueryData(['lead', lead.id], lead); await refresh(); setDrawer(null); setNotice(`${lead.company} 已保存。`) } })
  const assignMutation = useMutation({ mutationFn: (lead: Lead) => assignLead(lead.id, memberId!), onSuccess: async (lead) => { await refresh(); setNotice(`已将 ${lead.company} 分配给当前销售。`) } })
  const convertMutation = useMutation({ mutationFn: ({ lead, input }: { lead: Lead; input: LeadConversionInput }) => convertLead(lead.id, input), onSuccess: async () => { const name = conversion?.company; await refresh(); setConversion(null); setNotice(`${name} 已转为客户并创建商机。`) } })
  const importMutation = useMutation({ mutationFn: importLeads, onSuccess: async (result) => { await refresh(); setNotice(`导入完成：成功 ${result.created} 条，跳过 ${result.skipped} 条${result.errors.length ? `；${result.errors.slice(0, 2).join('；')}` : ''}`) } })

  async function save(input: LeadUpsertInput) { if (drawer) await saveMutation.mutateAsync({ state: drawer, input }) }
  async function importCsv(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; event.target.value = ''
    if (!file) return
    try { const rows = parseLeadCsv(await file.text()); if (!rows.length) throw new Error('CSV 中没有可导入的线索，请确认包含“企业名称”和“联系人”列'); importMutation.mutate(rows) }
    catch (error) { setNotice(error instanceof Error ? error.message : 'CSV 解析失败') }
  }

  const leads = leadsQuery.data?.content ?? []
  const metrics = metricsQuery.data
  return <section className="module-page crm-page">
    <DemoPageHeader demo={false} title="线索池" description="统一接收、清洗、分配和转化全渠道销售线索。" actions={<><input ref={importRef} hidden type="file" accept=".csv,text/csv" onChange={(event) => void importCsv(event)} /><button className="button button-secondary" type="button" disabled={isGuest || importMutation.isPending} onClick={() => importRef.current?.click()}><UploadSimple size={16} />{importMutation.isPending ? '导入中' : '导入 CSV'}</button><button className="button button-primary" type="button" disabled={isGuest} onClick={() => { saveMutation.reset(); setDrawer({ mode: 'create' }) }}><Plus size={16} />新建线索</button></>} />
    <div className="module-stat-grid" aria-label="线索指标"><div><span>待处理线索</span><strong>{metrics?.pending ?? '—'}</strong><small>等待联系或持续培育</small></div><div><span>已转化线索</span><strong>{metrics?.qualified ?? '—'}</strong><small>已创建真实商机</small></div><div><span>未分配线索</span><strong className={metrics?.unassigned ? 'danger-value' : undefined}>{metrics?.unassigned ?? '—'}</strong><small>需要指定销售负责人</small></div><div><span>平均评分</span><strong>{metrics ? Math.round(metrics.averageScore) : '—'}</strong><small>当前线索综合评分</small></div></div>
    <section className="surface module-panel">
      <div className="module-toolbar"><div><h2>线索列表</h2><span>{leadsQuery.data ? `共 ${leadsQuery.data.totalElements} 条真实记录` : '正在读取数据库'}</span></div><div className="toolbar-controls"><label className="compact-search"><MagnifyingGlass size={15} aria-hidden /><input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0) }} placeholder="搜索企业、联系人、邮箱或电话" aria-label="搜索线索" /></label><SelectField className="is-compact" value={status} onChange={(value) => { setStatus(value); setPage(0) }} ariaLabel="筛选线索状态" options={statusOptions} /></div></div>
      {notice ? <div className="crm-inline-notice" role="status">{notice}<button type="button" onClick={() => setNotice('')}>关闭</button></div> : null}
      {(assignMutation.error || importMutation.error) ? <div className="crm-inline-notice is-error" role="alert">{assignMutation.error?.message ?? importMutation.error?.message}</div> : null}
      {leadsQuery.isPending ? <div className="customer-query-state"><SpinnerGap className="is-spinning" size={22} /><strong>正在加载线索</strong></div> : leadsQuery.isError ? <div className="customer-query-state is-error"><WarningCircle size={22} /><strong>线索加载失败</strong><span>{leadsQuery.error.message}</span><button type="button" onClick={() => void leadsQuery.refetch()}>重新加载</button></div> : <>
        <div className="workspace-table-wrap"><table className="workspace-table leads-table"><thead><tr><th>线索</th><th>状态</th><th>评分</th><th>来源</th><th>最近行为</th><th>负责人</th><th>进入时间</th><th aria-label="操作" /></tr></thead><tbody>{leads.map((lead) => <tr key={lead.id}>
          <td><button className="customer-cell customer-link" type="button" onClick={() => setDrawer({ mode: 'edit', id: lead.id })}><span className="company-avatar">{lead.company.slice(0, 1)}</span><span><strong>{lead.company}</strong><small>{lead.contactName} · {lead.contactTitle ?? lead.industry ?? '未填写职位'}</small></span></button></td>
          <td><span className={`crm-status is-${lead.status.toLowerCase()}`}>{statusLabels[lead.status]}</span></td><td><span className={`lead-score ${(lead.score ?? 0) >= 80 ? 'is-high' : ''}`}>{lead.score ?? '—'}</span></td><td>{sourceLabel(lead.source)}</td><td className="muted-cell">{relativeTime(lead.lastActivityAt)}</td><td>{lead.ownerName ?? <span className="unassigned-copy">未分配</span>}</td><td className="muted-cell">{new Date(lead.createdAt).toLocaleDateString('zh-CN')}</td>
          <td><div className="row-actions">{!lead.ownerMemberId && !['QUALIFIED', 'DISQUALIFIED'].includes(lead.status) ? <button type="button" disabled={isGuest || !memberId || assignMutation.isPending} title="分配给当前销售" aria-label={`分配 ${lead.company}`} onClick={() => assignMutation.mutate(lead)}><UserPlus size={15} /></button> : null}<button type="button" disabled={isGuest || ['QUALIFIED', 'DISQUALIFIED'].includes(lead.status)} title="编辑线索" aria-label={`编辑 ${lead.company}`} onClick={() => setDrawer({ mode: 'edit', id: lead.id })}><PencilSimple size={15} /></button>{lead.status !== 'QUALIFIED' && lead.status !== 'DISQUALIFIED' ? <button type="button" disabled={isGuest || !lead.ownerMemberId} title={lead.ownerMemberId ? '转为客户和商机' : '请先分配负责人'} aria-label={`将 ${lead.company} 转为商机`} onClick={() => setConversion(lead)}><ArrowSquareOut size={15} /></button> : null}</div></td>
        </tr>)}</tbody></table></div>
        {!leads.length ? <div className="filter-empty"><UsersThree size={22} /><strong>没有匹配的线索</strong><span><FunnelSimple size={13} />调整筛选条件或录入第一条线索。</span></div> : null}
        {leadsQuery.data && leadsQuery.data.totalPages > 0 ? <footer className="table-pagination"><span>第 {page + 1} / {leadsQuery.data.totalPages} 页</span><div><button type="button" disabled={leadsQuery.data.first} onClick={() => setPage((value) => value - 1)}><CaretLeft size={14} />上一页</button><button type="button" disabled={leadsQuery.data.last} onClick={() => setPage((value) => value + 1)}>下一页<CaretRight size={14} /></button></div></footer> : null}
      </>}
    </section>
    {drawer ? <LeadDrawer key={drawer.mode === 'create' ? 'create' : `${drawer.id}-${selectedQuery.data?.updatedAt ?? ''}`} lead={drawer.mode === 'edit' ? selectedQuery.data : null} owners={ownersQuery.data ?? []} pending={saveMutation.isPending} loading={drawer.mode === 'edit' && selectedQuery.isPending} error={saveMutation.error?.message ?? selectedQuery.error?.message} readOnly={isGuest} onClose={() => setDrawer(null)} onSubmit={save} /> : null}
    {conversion ? <ConvertDialog lead={conversion} pending={convertMutation.isPending} error={convertMutation.error?.message} onClose={() => setConversion(null)} onSubmit={(input) => convertMutation.mutate({ lead: conversion, input })} /> : null}
  </section>
}

function ConvertDialog({ lead, pending, error, onClose, onSubmit }: { lead: Lead; pending: boolean; error?: string; onClose: () => void; onSubmit: (input: LeadConversionInput) => void }) {
  const [name, setName] = useState(`${lead.company} 商机`); const [amount, setAmount] = useState(''); const [date, setDate] = useState('')
  return <div className="admin-modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="admin-modal lead-convert-modal" role="dialog" aria-modal="true" aria-labelledby="convert-title"><header><div><h2 id="convert-title">转为客户和商机</h2><p>{lead.company} 将保留为客户档案，并创建一条商机。</p></div><button className="icon-button" type="button" onClick={onClose} aria-label="关闭"><X size={18} /></button></header><form onSubmit={(event) => { event.preventDefault(); onSubmit({ opportunityName: name.trim(), amount: amount ? Number(amount) : null, expectedCloseDate: date || null }) }}><label><span>商机名称</span><input required value={name} onChange={(e) => setName(e.target.value)} maxLength={255} /></label><label><span>预计金额（元）</span><input type="number" min="0" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} /></label><label><span>预计成交日期</span><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>{error ? <p className="form-error" role="alert">{error}</p> : null}<footer><button className="button button-secondary" type="button" onClick={onClose}>取消</button><button className="button button-primary" type="submit" disabled={pending || !name.trim()}>{pending ? '转化中…' : '确认转化'}</button></footer></form></section></div>
}

function sourceLabel(source: string) { return ({ MANUAL: '手工录入', IMPORT: '批量导入', CHAT: '聊天识别', CRM: 'CRM 同步', API: 'API' } as Record<string, string>)[source] ?? source }
function relativeTime(value: string | null) { if (!value) return '暂无跟进'; const days = Math.floor((Date.now() - new Date(value).getTime()) / 86_400_000); return days <= 0 ? '今天' : `${days} 天前` }
function parseLeadCsv(text: string): LeadUpsertInput[] {
  const rows = parseCsv(text.replace(/^\uFEFF/, '')); if (rows.length < 2) return []
  const headers = rows[0].map((item) => item.trim().toLowerCase()); const value = (row: string[], names: string[]) => { const index = headers.findIndex((header) => names.includes(header)); return index < 0 ? '' : row[index]?.trim() ?? '' }
  return rows.slice(1).filter((row) => row.some(Boolean)).map((row, index) => { const company = value(row, ['企业名称', '公司名称', 'company']); const contactName = value(row, ['联系人', '联系人姓名', 'contact']); if (!company || !contactName) throw new Error(`CSV 第 ${index + 2} 行缺少企业名称或联系人`); const score = value(row, ['评分', 'score']); return { company, contactName, industry: value(row, ['行业', 'industry']) || null, contactEmail: value(row, ['邮箱', 'email']) || null, contactPhone: value(row, ['电话', '手机号', 'phone']) || null, contactTitle: value(row, ['职位', 'title']) || null, source: 'IMPORT', status: 'NEW', score: score ? Number(score) : null } })
}
function parseCsv(text: string) {
  const rows: string[][] = []; let row: string[] = []; let field = ''; let quoted = false
  for (let index = 0; index < text.length; index++) { const char = text[index]; const next = text[index + 1]; if (char === '"' && quoted && next === '"') { field += '"'; index++ } else if (char === '"') quoted = !quoted; else if (char === ',' && !quoted) { row.push(field); field = '' } else if ((char === '\n' || char === '\r') && !quoted) { if (char === '\r' && next === '\n') index++; row.push(field); rows.push(row); row = []; field = '' } else field += char }
  if (field || row.length) { row.push(field); rows.push(row) } return rows
}
