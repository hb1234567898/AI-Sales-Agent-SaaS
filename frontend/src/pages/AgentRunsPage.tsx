import { ArrowClockwise, CheckCircle, Clock, Robot, WarningCircle } from '@phosphor-icons/react'
import { useState } from 'react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { mockRuns } from '../data/mock-sales-data'

export function AgentRunsPage() {
  const [selectedId, setSelectedId] = useState(mockRuns[0].id)
  const selectedRun = mockRuns.find((run) => run.id === selectedId) ?? mockRuns[0]

  return (
    <section className="module-page">
      <DemoPageHeader
        title="Agent 运行"
        description="追踪每次运行的输入、步骤、模型消耗与业务结果。"
        actions={<button className="button button-primary" type="button" disabled><ArrowClockwise size={16} />新建运行</button>}
      />

      <div className="module-stat-grid" aria-label="Agent 运行指标">
        <div><span>今日运行</span><strong>18</strong><small>成功 16 次</small></div>
        <div><span>处理客户</span><strong>242</strong><small>去重后 186 个</small></div>
        <div><span>模型消耗</span><strong>9.4 万</strong><small>Token 总量</small></div>
        <div><span>今日成本</span><strong>¥ 2.38</strong><small>单次平均 ¥ 0.13</small></div>
      </div>

      <div className="runs-layout">
        <section className="surface run-history-panel">
          <div className="module-toolbar"><div><h2>运行历史</h2><span>最近 5 条演示记录</span></div></div>
          <div className="run-history-list">
            {mockRuns.map((run) => (
              <button className={selectedId === run.id ? 'run-history-item is-selected' : 'run-history-item'} type="button" key={run.id} onClick={() => setSelectedId(run.id)}>
                <span className="run-icon"><Robot size={16} /></span>
                <span><strong>{run.name}</strong><small>{run.id} · {run.scope}</small></span>
                <span className={`run-status ${run.status === '待审核' ? 'is-review' : run.status === '部分失败' ? 'is-error' : ''}`}>{run.status}</span>
                <time>{run.startedAt}</time>
              </button>
            ))}
          </div>
        </section>

        <section className="surface run-detail-panel">
          <div className="panel-header">
            <div><h2>{selectedRun.name}</h2><p>{selectedRun.id} · {selectedRun.trigger}</p></div>
            <span className={`run-status detail-status ${selectedRun.status === '待审核' ? 'is-review' : selectedRun.status === '部分失败' ? 'is-error' : ''}`}>{selectedRun.status}</span>
          </div>
          <div className="run-result">
            <span className={selectedRun.status === '部分失败' ? 'result-icon is-error' : 'result-icon'}>{selectedRun.status === '部分失败' ? <WarningCircle size={22} /> : <CheckCircle size={22} />}</span>
            <div><strong>运行结果</strong><p>{selectedRun.result}</p></div>
          </div>
          <div className="execution-flow" aria-label="执行步骤">
            {['读取客户信号', '分析意向与风险', '生成下一步动作', '写入审核队列'].map((step, index) => (
              <div key={step}><span><CheckCircle size={15} /></span><strong>{step}</strong><small>{8 + index * 5} 秒</small></div>
            ))}
          </div>
          <dl className="run-facts">
            <div><dt>处理范围</dt><dd>{selectedRun.scope}</dd></div>
            <div><dt>使用模型</dt><dd>{selectedRun.model}</dd></div>
            <div><dt>Token</dt><dd>{selectedRun.tokens}</dd></div>
            <div><dt>模型成本</dt><dd>{selectedRun.cost}</dd></div>
            <div><dt>运行耗时</dt><dd><Clock size={13} />{selectedRun.duration}</dd></div>
            <div><dt>触发方式</dt><dd>{selectedRun.trigger}</dd></div>
          </dl>
        </section>
      </div>
    </section>
  )
}
