import { ArrowDown, ArrowUp, CalendarBlank, ChartLineUp, Target } from '@phosphor-icons/react'
import { useMemo, useState } from 'react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { forecastCategoryLabels, mockOpportunities, salesTargets, type ForecastCategory } from '../data/mock-crm-data'

const periods = [{ value: 'MONTH', label: '本月' }, { value: 'QUARTER', label: '本季度' }, { value: 'YEAR', label: '本年度' }]
const owners = Object.keys(salesTargets.owners) as Array<keyof typeof salesTargets.owners>
const money = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 })

export function ForecastPage() {
  const [period, setPeriod] = useState('QUARTER')
  const periodFactor = period === 'MONTH' ? 0.42 : period === 'YEAR' ? 2.8 : 1
  const active = mockOpportunities.filter((item) => item.stage !== 'WON')
  const wonAmount = mockOpportunities.filter((item) => item.stage === 'WON').reduce((sum, item) => sum + item.amount, 0) * periodFactor
  const commitAmount = active.filter((item) => item.forecastCategory === 'COMMIT').reduce((sum, item) => sum + item.amount, 0) * periodFactor
  const bestCaseAmount = active.filter((item) => item.forecastCategory !== 'PIPELINE').reduce((sum, item) => sum + item.amount, 0) * periodFactor
  const weightedAmount = active.reduce((sum, item) => sum + item.amount * item.probability / 100, 0) * periodFactor
  const target = salesTargets.team * periodFactor
  const attainment = Math.round((wonAmount + commitAmount) / target * 100)

  const ownerRows = useMemo(() => owners.map((owner) => {
    const items = mockOpportunities.filter((item) => item.owner === owner)
    const closed = items.filter((item) => item.stage === 'WON').reduce((sum, item) => sum + item.amount, 0) * periodFactor
    const commit = items.filter((item) => item.stage !== 'WON' && item.forecastCategory === 'COMMIT').reduce((sum, item) => sum + item.amount, 0) * periodFactor
    const best = items.filter((item) => item.stage !== 'WON' && item.forecastCategory !== 'PIPELINE').reduce((sum, item) => sum + item.amount, 0) * periodFactor
    const ownerTarget = salesTargets.owners[owner] * periodFactor
    return { owner, target: ownerTarget, closed, commit, best, coverage: (items.filter((item) => item.stage !== 'WON').reduce((sum, item) => sum + item.amount, 0) * periodFactor) / ownerTarget }
  }), [periodFactor])

  return (
    <section className="module-page crm-page forecast-page">
      <DemoPageHeader title="销售预测" description="汇总目标、承诺金额和销售管道，提前识别收入缺口。" actions={<div className="segmented-filter period-filter" aria-label="预测周期"><CalendarBlank size={14} />{periods.map((item) => <button className={period === item.value ? 'is-active' : ''} type="button" key={item.value} onClick={() => setPeriod(item.value)}>{item.label}</button>)}</div>} />

      <div className="forecast-summary surface">
        <section><span>目标完成度</span><strong>{attainment}%</strong><div className="forecast-progress" aria-label={`目标完成度 ${attainment}%`}><i style={{ width: `${Math.min(attainment, 100)}%` }} /></div><small>{money.format(wonAmount + commitAmount)} / {money.format(target)}</small></section>
        <dl><div><dt>已赢单</dt><dd>{money.format(wonAmount)}</dd><small><ArrowUp size={11} />计入实际收入</small></div><div><dt>承诺预测</dt><dd>{money.format(commitAmount)}</dd><small>销售明确承诺</small></div><div><dt>最佳情况</dt><dd>{money.format(bestCaseAmount)}</dd><small>包含承诺商机</small></div><div><dt>加权管道</dt><dd>{money.format(weightedAmount)}</dd><small>按成交概率计算</small></div></dl>
      </div>

      <div className="forecast-layout">
        <section className="surface module-panel forecast-table-panel">
          <div className="panel-header"><div><h2>团队预测</h2><p>按销售负责人汇总目标与预测金额</p></div><span className="count-label">{ownerRows.length} 人</span></div>
          <div className="workspace-table-wrap"><table className="workspace-table forecast-table"><thead><tr><th>销售</th><th>目标</th><th>已赢单</th><th>承诺</th><th>最佳情况</th><th>管道覆盖</th><th>风险</th></tr></thead><tbody>{ownerRows.map((row) => {
            const gap = row.target - row.closed - row.commit
            return <tr key={row.owner}><td><span className="forecast-owner"><span>{row.owner.slice(0, 1)}</span><strong>{row.owner}</strong></span></td><td>{money.format(row.target)}</td><td>{money.format(row.closed)}</td><td>{money.format(row.commit)}</td><td>{money.format(row.best)}</td><td><span className="coverage-value">{row.coverage.toFixed(1)}x</span></td><td>{gap > 0 ? <span className="forecast-risk"><ArrowDown size={11} />缺口 {money.format(gap)}</span> : <span className="forecast-safe"><ArrowUp size={11} />目标可覆盖</span>}</td></tr>
          })}</tbody></table></div>
        </section>

        <aside className="surface forecast-insights"><header><ChartLineUp size={18} /><div><h2>预测洞察</h2><p>根据当前演示管道计算</p></div></header><div className="forecast-gap"><span>距目标仍差</span><strong>{money.format(Math.max(target - wonAmount - commitAmount, 0))}</strong><small>需要从最佳情况或管道商机中补足</small></div><section><h3>优先推进</h3>{active.sort((a, b) => b.amount * b.probability - a.amount * a.probability).slice(0, 3).map((item) => <article key={item.id}><span><Target size={13} />{item.company}</span><strong>{money.format(item.amount)}</strong><small>{forecastCategoryLabels[item.forecastCategory as ForecastCategory]} · {item.probability}%</small></article>)}</section></aside>
      </div>
    </section>
  )
}
