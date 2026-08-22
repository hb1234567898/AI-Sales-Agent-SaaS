import { ArrowUp, ChartLineUp, ClockCounterClockwise, DownloadSimple } from '@phosphor-icons/react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

const weeklyActivity = [
  { label: '周一', value: 42, contacts: 18 },
  { label: '周二', value: 68, contacts: 27 },
  { label: '周三', value: 54, contacts: 22 },
  { label: '周四', value: 82, contacts: 34 },
  { label: '周五', value: 73, contacts: 29 },
  { label: '周六', value: 28, contacts: 11 },
  { label: '周日', value: 35, contacts: 14 },
]

const funnel = [
  { label: '识别机会', value: 148, rate: '100%' },
  { label: '生成建议', value: 86, rate: '58.1%' },
  { label: '人工批准', value: 62, rate: '72.1%' },
  { label: '成功触达', value: 54, rate: '87.1%' },
  { label: '获得回复', value: 17, rate: '31.5%' },
]

export function AnalyticsPage() {
  return (
    <section className="module-page">
      <DemoPageHeader
        title="效果分析"
        description="量化节省时间、触达效率、转化提升和 Agent 成本。"
        actions={<button className="button button-secondary" type="button" disabled><DownloadSimple size={16} />导出报告</button>}
      />

      <div className="module-stat-grid analytics-stats" aria-label="效果指标">
        <div><span>本月节省时间</span><strong>46.8 小时</strong><small><ArrowUp size={11} />较上月 18.4%</small></div>
        <div><span>客户触达量</span><strong>286</strong><small><ArrowUp size={11} />较上月 12.7%</small></div>
        <div><span>有效回复率</span><strong>31.5%</strong><small>销售团队均值 24.2%</small></div>
        <div><span>Agent 投入产出比</span><strong>8.7x</strong><small>按节省工时估算</small></div>
      </div>

      <div className="analytics-grid">
        <section className="surface chart-panel">
          <div className="panel-header"><div><h2>本周触达趋势</h2><p>按成功发送的客户动作统计</p></div><span className="chart-total">155 次</span></div>
          <div className="bar-chart" role="img" aria-label="本周每日客户触达数量柱状图">
            <div className="chart-y-labels"><span>40</span><span>30</span><span>20</span><span>10</span><span>0</span></div>
            <div className="chart-bars">
              {weeklyActivity.map((day) => (
                <div className="chart-bar-item" key={day.label}>
                  <span className="bar-value">{day.contacts}</span>
                  <span className="chart-bar" style={{ height: `${day.value}%` }} />
                  <small>{day.label}</small>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="surface funnel-panel">
          <div className="panel-header"><div><h2>动作转化漏斗</h2><p>过去 30 天</p></div><ChartLineUp size={18} /></div>
          <div className="funnel-list">
            {funnel.map((stage, index) => (
              <div key={stage.label}>
                <span className="funnel-index">{index + 1}</span>
                <span><strong>{stage.label}</strong><small>{stage.rate} 阶段转化</small></span>
                <b>{stage.value}</b>
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="surface module-panel analytics-table-panel">
        <div className="module-toolbar"><div><h2>业务效果明细</h2><span>过去 30 天演示数据</span></div></div>
        <div className="workspace-table-wrap">
          <table className="workspace-table">
            <thead><tr><th>业务指标</th><th>使用 Agent</th><th>人工基线</th><th>变化</th><th>计算口径</th></tr></thead>
            <tbody>
              <tr><td>单客户研究时间</td><td>2.8 分钟</td><td>11.6 分钟</td><td><span className="positive-change">减少 75.9%</span></td><td>从读取客户到生成建议</td></tr>
              <tr><td>首次跟进响应</td><td>1.4 小时</td><td>5.8 小时</td><td><span className="positive-change">缩短 4.4 小时</span></td><td>客户产生信号到销售触达</td></tr>
              <tr><td>建议采纳率</td><td>72.1%</td><td>不适用</td><td><span className="neutral-change">本月新增</span></td><td>人工批准建议占全部建议比例</td></tr>
              <tr><td>每次有效触达成本</td><td>¥ 0.18</td><td>¥ 3.42</td><td><span className="positive-change">减少 94.7%</span></td><td>模型成本与人力时间估算</td></tr>
            </tbody>
          </table>
        </div>
        <p className="analytics-footnote"><ClockCounterClockwise size={13} />数据每小时刷新一次，当前展示为演示数据。</p>
      </section>
    </section>
  )
}
