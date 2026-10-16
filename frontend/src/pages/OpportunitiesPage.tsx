import { ArrowRight, Kanban, ListBullets, MagnifyingGlass, Plus, WarningCircle } from '@phosphor-icons/react'
import { useMemo, useState } from 'react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { SelectField } from '../components/forms/SelectField'
import { useIsGuest } from '../auth/use-auth'
import { forecastCategoryLabels, mockOpportunities, opportunityStageLabels, type OpportunityStage, type SalesOpportunity } from '../data/mock-crm-data'

const stages = Object.keys(opportunityStageLabels) as OpportunityStage[]
const activeStages = stages.filter((stage) => stage !== 'WON')
const ownerOptions = [{ value: '', label: '全部负责人' }, { value: '陈默', label: '陈默' }, { value: '李昕', label: '李昕' }, { value: '王宁', label: '王宁' }]
const money = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 })

export function OpportunitiesPage() {
  const isGuest = useIsGuest()
  const [opportunities, setOpportunities] = useState(mockOpportunities)
  const [view, setView] = useState<'BOARD' | 'LIST'>('BOARD')
  const [owner, setOwner] = useState('')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<SalesOpportunity | null>(null)
  const [notice, setNotice] = useState('')

  const visible = useMemo(() => opportunities.filter((item) => {
    const term = query.trim().toLowerCase()
    return (!owner || item.owner === owner) && (!term || `${item.company}${item.name}${item.owner}`.toLowerCase().includes(term))
  }), [opportunities, owner, query])

  const active = opportunities.filter((item) => item.stage !== 'WON')
  const pipelineAmount = active.reduce((sum, item) => sum + item.amount, 0)
  const weightedAmount = active.reduce((sum, item) => sum + item.amount * item.probability / 100, 0)
  const stalledCount = active.filter((item) => item.daysInStage >= 14).length

  function moveForward(item: SalesOpportunity) {
    const currentIndex = stages.indexOf(item.stage)
    const nextStage = stages[Math.min(currentIndex + 1, stages.length - 1)]
    const updated = { ...item, stage: nextStage, daysInStage: 0, probability: nextStage === 'WON' ? 100 : Math.min(item.probability + 15, 90) }
    setOpportunities((current) => current.map((value) => value.id === item.id ? updated : value))
    setSelected(updated)
  }

  return (
    <section className="module-page crm-page">
      <DemoPageHeader title="商机管道" description="按销售阶段管理机会、下一步动作和预计成交金额。" actions={<button className="button button-primary" type="button" disabled={isGuest} title={isGuest ? '游客模式不能新建商机' : undefined} onClick={() => setNotice('商机创建表单将在后端数据模型接入时开放。')}><Plus size={16} />新建商机</button>} />
      <div className="module-stat-grid" aria-label="商机指标">
        <div><span>活跃商机</span><strong>{active.length}</strong><small>不含已赢单项目</small></div>
        <div><span>管道金额</span><strong>{formatCompactMoney(pipelineAmount)}</strong><small>全部活跃商机金额</small></div>
        <div><span>加权金额</span><strong>{formatCompactMoney(weightedAmount)}</strong><small>金额乘以成交概率</small></div>
        <div><span>阶段停滞</span><strong className={stalledCount ? 'danger-value' : undefined}>{stalledCount}</strong><small>当前阶段超过 14 天</small></div>
      </div>

      <section className="surface module-panel opportunity-panel">
        <div className="module-toolbar">
          <div><h2>销售管道</h2><span>推进阶段时同步更新成交概率</span></div>
          <div className="toolbar-controls">
            <label className="compact-search"><MagnifyingGlass size={15} aria-hidden /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索商机或客户" aria-label="搜索商机" /></label>
            <SelectField className="is-compact" value={owner} onChange={setOwner} ariaLabel="筛选负责人" options={ownerOptions} />
            <div className="icon-segment" aria-label="商机视图"><button className={view === 'BOARD' ? 'is-active' : ''} type="button" onClick={() => setView('BOARD')} aria-label="看板视图"><Kanban size={16} /></button><button className={view === 'LIST' ? 'is-active' : ''} type="button" onClick={() => setView('LIST')} aria-label="列表视图"><ListBullets size={16} /></button></div>
          </div>
        </div>
        {notice ? <div className="crm-inline-notice" role="status">{notice}<button type="button" onClick={() => setNotice('')}>关闭</button></div> : null}

        {view === 'BOARD' ? (
          <div className="pipeline-board">{activeStages.map((stage) => {
            const items = visible.filter((item) => item.stage === stage)
            return <section className="pipeline-column" key={stage} aria-label={opportunityStageLabels[stage]}>
              <header><span>{opportunityStageLabels[stage]}</span><strong>{items.length}</strong><small>{formatCompactMoney(items.reduce((sum, item) => sum + item.amount, 0))}</small></header>
              <div>{items.map((item) => <button className="opportunity-card" type="button" key={item.id} onClick={() => setSelected(item)}>
                <span className="opportunity-card-company">{item.company}<small>{item.id}</small></span>
                <strong>{item.name}</strong>
                <span className="opportunity-card-value">{money.format(item.amount)}<small>{item.probability}%</small></span>
                <span className="opportunity-card-meta"><small>{item.owner}</small><small>{item.closeDate} 预计成交</small></span>
                {item.daysInStage >= 14 ? <span className="stalled-warning"><WarningCircle size={12} />已停留 {item.daysInStage} 天</span> : null}
              </button>)}</div>
            </section>
          })}</div>
        ) : (
          <div className="workspace-table-wrap"><table className="workspace-table opportunities-table"><thead><tr><th>商机</th><th>阶段</th><th>金额</th><th>概率</th><th>预测分类</th><th>预计成交</th><th>负责人</th><th>下一步</th></tr></thead><tbody>{visible.map((item) => <tr key={item.id} onClick={() => setSelected(item)}><td><strong>{item.name}</strong><small className="table-subcopy">{item.company}</small></td><td><span className="stage-label">{opportunityStageLabels[item.stage]}</span></td><td>{money.format(item.amount)}</td><td><span className="mono-value">{item.probability}%</span></td><td>{forecastCategoryLabels[item.forecastCategory]}</td><td>{item.closeDate}</td><td>{item.owner}</td><td>{item.nextStep}</td></tr>)}</tbody></table></div>
        )}
      </section>

      {selected ? <div className="crm-detail-backdrop" role="presentation" onMouseDown={() => setSelected(null)}><aside className="crm-detail-panel" role="dialog" aria-modal="true" aria-labelledby="opportunity-detail-title" onMouseDown={(event) => event.stopPropagation()}><header><div><span>{selected.company}</span><h2 id="opportunity-detail-title">{selected.name}</h2></div><button type="button" onClick={() => setSelected(null)} aria-label="关闭商机详情">关闭</button></header><dl><div><dt>商机金额</dt><dd>{money.format(selected.amount)}</dd></div><div><dt>成交概率</dt><dd>{selected.probability}%</dd></div><div><dt>销售阶段</dt><dd>{opportunityStageLabels[selected.stage]}</dd></div><div><dt>预测分类</dt><dd>{forecastCategoryLabels[selected.forecastCategory]}</dd></div><div><dt>负责人</dt><dd>{selected.owner}</dd></div><div><dt>预计成交</dt><dd>{selected.closeDate}</dd></div></dl><section><span>下一步动作</span><p>{selected.nextStep}</p></section><footer><button className="button button-secondary" type="button" onClick={() => setSelected(null)}>稍后处理</button><button className="button button-primary" type="button" disabled={isGuest || selected.stage === 'WON'} title={isGuest ? '游客模式不能推进商机阶段' : undefined} onClick={() => moveForward(selected)}>推进到下一阶段<ArrowRight size={15} /></button></footer></aside></div> : null}
    </section>
  )
}

function formatCompactMoney(value: number) {
  return `¥${(value / 10000).toFixed(1)}万`
}
