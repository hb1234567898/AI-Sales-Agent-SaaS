import { ArrowSquareOut, FunnelSimple, MagnifyingGlass, Plus, UserPlus, UsersThree } from '@phosphor-icons/react'
import { useDeferredValue, useMemo, useState } from 'react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { SelectField } from '../components/forms/SelectField'
import { useIsGuest } from '../auth/use-auth'
import { leadStatusLabels, mockLeads, type SalesLead } from '../data/mock-crm-data'

const statusOptions = [
  { value: '', label: '全部状态' },
  ...Object.entries(leadStatusLabels).map(([value, label]) => ({ value, label })),
]

export function LeadsPage() {
  const isGuest = useIsGuest()
  const [leads, setLeads] = useState(mockLeads)
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query.trim().toLowerCase())
  const [status, setStatus] = useState('')
  const [notice, setNotice] = useState('')

  const filteredLeads = useMemo(() => leads.filter((lead) => {
    const matchesQuery = !deferredQuery || [lead.company, lead.contact, lead.title, lead.source].some((value) => value.toLowerCase().includes(deferredQuery))
    return matchesQuery && (!status || lead.status === status)
  }), [deferredQuery, leads, status])

  const newCount = leads.filter((lead) => lead.status === 'NEW').length
  const qualifiedCount = leads.filter((lead) => lead.status === 'QUALIFIED').length
  const unassignedCount = leads.filter((lead) => !lead.owner).length
  const averageScore = Math.round(leads.reduce((sum, lead) => sum + lead.score, 0) / leads.length)

  function assignLead(lead: SalesLead) {
    setLeads((current) => current.map((item) => item.id === lead.id ? { ...item, owner: '陈默', status: item.status === 'NEW' ? 'NURTURING' : item.status } : item))
    setNotice(`已将 ${lead.company} 分配给陈默。`)
  }

  function convertLead(lead: SalesLead) {
    setLeads((current) => current.map((item) => item.id === lead.id ? { ...item, status: 'QUALIFIED', owner: item.owner ?? '陈默' } : item))
    setNotice(`${lead.company} 已转为商机，商机资料将在后端接入后自动创建。`)
  }

  return (
    <section className="module-page crm-page">
      <DemoPageHeader
        title="线索池"
        description="统一接收、清洗、分配和转化全渠道销售线索。"
        actions={<button className="button button-primary" type="button" disabled={isGuest} title={isGuest ? '游客模式不能新建线索' : undefined} onClick={() => setNotice('新建线索表单将在后端数据模型接入时开放。')}><Plus size={16} />新建线索</button>}
      />

      <div className="module-stat-grid" aria-label="线索指标">
        <div><span>待处理线索</span><strong>{newCount}</strong><small>需要首次联系或分配</small></div>
        <div><span>已转化线索</span><strong>{qualifiedCount}</strong><small>本批演示数据</small></div>
        <div><span>未分配线索</span><strong className={unassignedCount ? 'danger-value' : undefined}>{unassignedCount}</strong><small>应进入负责人分配规则</small></div>
        <div><span>平均评分</span><strong>{averageScore}</strong><small>属性与行为综合评分</small></div>
      </div>

      <section className="surface module-panel">
        <div className="module-toolbar">
          <div><h2>线索列表</h2><span>展示从获客到销售合格线索的处理队列</span></div>
          <div className="toolbar-controls">
            <label className="compact-search"><MagnifyingGlass size={15} aria-hidden /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索企业、联系人或来源" aria-label="搜索线索" /></label>
            <SelectField className="is-compact" value={status} onChange={setStatus} ariaLabel="筛选线索状态" options={statusOptions} />
          </div>
        </div>
        {notice ? <div className="crm-inline-notice" role="status">{notice}<button type="button" onClick={() => setNotice('')}>关闭</button></div> : null}
        <div className="workspace-table-wrap">
          <table className="workspace-table leads-table">
            <thead><tr><th>线索</th><th>状态</th><th>评分</th><th>来源</th><th>最近行为</th><th>负责人</th><th>进入时间</th><th aria-label="操作" /></tr></thead>
            <tbody>{filteredLeads.map((lead) => (
              <tr key={lead.id}>
                <td><span className="customer-cell"><span className="company-avatar">{lead.company.slice(0, 1)}</span><span><strong>{lead.company}</strong><small>{lead.contact} · {lead.title}</small></span></span></td>
                <td><span className={`crm-status is-${lead.status.toLowerCase()}`}>{leadStatusLabels[lead.status]}</span></td>
                <td><span className={`lead-score ${lead.score >= 80 ? 'is-high' : ''}`}>{lead.score}</span></td>
                <td>{lead.source}</td>
                <td className="muted-cell">{lead.lastActivity}</td>
                <td>{lead.owner ?? <span className="unassigned-copy">未分配</span>}</td>
                <td className="muted-cell">{lead.createdAt}</td>
                <td><div className="row-actions">{!lead.owner ? <button type="button" disabled={isGuest} title={isGuest ? '游客模式不能分配线索' : '分配给当前销售'} aria-label={`分配 ${lead.company}`} onClick={() => assignLead(lead)}><UserPlus size={15} /></button> : null}{lead.status !== 'QUALIFIED' && lead.status !== 'DISQUALIFIED' ? <button type="button" disabled={isGuest} title={isGuest ? '游客模式不能转化线索' : '转为商机'} aria-label={`将 ${lead.company} 转为商机`} onClick={() => convertLead(lead)}><ArrowSquareOut size={15} /></button> : null}</div></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
        {filteredLeads.length === 0 ? <div className="filter-empty"><UsersThree size={22} /><strong>没有匹配的线索</strong><span><FunnelSimple size={13} />请调整搜索词或状态筛选。</span></div> : null}
      </section>
    </section>
  )
}
