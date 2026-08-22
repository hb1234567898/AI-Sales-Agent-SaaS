import { MagnifyingGlass, Plus, UploadSimple, UsersThree } from '@phosphor-icons/react'
import { useMemo, useState } from 'react'
import { SelectField } from '../components/forms/SelectField'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { mockCustomers } from '../data/mock-sales-data'

export function CustomersPage() {
  const [query, setQuery] = useState('')
  const [stage, setStage] = useState('全部阶段')
  const customers = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    return mockCustomers.filter((customer) => {
      const matchesKeyword = !keyword || [customer.company, customer.contact, customer.owner, customer.industry]
        .some((value) => value.toLowerCase().includes(keyword))
      return matchesKeyword && (stage === '全部阶段' || customer.stage === stage)
    })
  }, [query, stage])

  return (
    <section className="module-page">
      <DemoPageHeader
        title="客户"
        description="统一查看企业、联系人、商机阶段与下一步动作。"
        actions={(
          <>
            <button className="button button-secondary" type="button" disabled><UploadSimple size={16} />导入客户</button>
            <button className="button button-primary" type="button" disabled><Plus size={16} />添加客户</button>
          </>
        )}
      />

      <div className="module-stat-grid" aria-label="客户指标">
        <div><span>客户总数</span><strong>148</strong><small>本月新增 12</small></div>
        <div><span>高意向客户</span><strong>24</strong><small>较上周增加 5</small></div>
        <div><span>活跃商机</span><strong>37</strong><small>预计金额 ¥ 486 万</small></div>
        <div><span>平均客户评分</span><strong>78.4</strong><small>过去 30 天</small></div>
      </div>

      <section className="surface module-panel">
        <div className="module-toolbar">
          <div>
            <h2>客户列表</h2>
            <span>{customers.length} 条演示记录</span>
          </div>
          <div className="toolbar-controls">
            <label className="compact-search">
              <MagnifyingGlass size={15} aria-hidden />
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索企业、联系人或负责人" aria-label="搜索客户" />
            </label>
            <SelectField
              className="is-compact"
              value={stage}
              onChange={setStage}
              ariaLabel="筛选商机阶段"
              options={['全部阶段', '方案确认', '技术评审', '产品试用', '价值评估', '需求确认', '初步接触', '培育中'].map((label) => ({ value: label, label }))}
            />
          </div>
        </div>

        <div className="workspace-table-wrap">
          <table className="workspace-table customers-table">
            <thead><tr><th>客户</th><th>商机阶段</th><th>意向评分</th><th>预计金额</th><th>最近互动</th><th>负责人</th><th>下一步动作</th></tr></thead>
            <tbody>
              {customers.map((customer) => (
                <tr key={customer.id}>
                  <td><div className="customer-cell"><span className="company-avatar">{customer.company.slice(0, 1)}</span><span><strong>{customer.company}</strong><small>{customer.contact} · {customer.industry}</small></span></div></td>
                  <td><span className="stage-label">{customer.stage}</span></td>
                  <td><strong className="mono-value">{customer.score}</strong></td>
                  <td>{customer.value}</td>
                  <td className="muted-cell">{customer.lastTouch}</td>
                  <td>{customer.owner}</td>
                  <td><span className="action-copy">{customer.nextAction}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {customers.length === 0 ? <div className="filter-empty"><UsersThree size={22} /><strong>没有匹配的客户</strong><span>请调整搜索词或商机阶段。</span></div> : null}
      </section>
    </section>
  )
}
