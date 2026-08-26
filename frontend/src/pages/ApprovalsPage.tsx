import { Check, Clock, ShieldCheck, X } from '@phosphor-icons/react'
import { useState } from 'react'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { mockApprovals } from '../data/mock-sales-data'
import { useIsGuest } from '../auth/use-auth'

export function ApprovalsPage() {
  const isGuest = useIsGuest()
  const [decisions, setDecisions] = useState<Record<string, 'approved' | 'rejected'>>({})
  const pending = mockApprovals.filter((approval) => !decisions[approval.id])

  return (
    <section className="module-page">
      <DemoPageHeader title="审批" description="审核客户消息、高风险建议和敏感数据操作。" />

      <div className="approval-layout">
        <section className="surface module-panel">
          <div className="module-toolbar">
            <div><h2>待审批队列</h2><span>{pending.length} 项等待人工确认</span></div>
            <span className="count-label">{pending.length}</span>
          </div>
          <div className="approval-list">
            {pending.map((approval) => (
              <article className="approval-row" key={approval.id}>
                <div className="approval-topline">
                  <span className="approval-type"><ShieldCheck size={16} />{approval.type}</span>
                  <span className={`risk-label risk-${approval.risk === '中' ? 'medium' : 'low'}`}>{approval.risk}风险</span>
                  <span className="approval-time"><Clock size={14} />{approval.createdAt}</span>
                </div>
                <h3>{approval.action}</h3>
                <p>{approval.reason}</p>
                <dl className="approval-meta">
                  <div><dt>客户</dt><dd>{approval.company}</dd></div>
                  <div><dt>发起方</dt><dd>{approval.requester}</dd></div>
                  <div><dt>AI 置信度</dt><dd>{approval.confidence}</dd></div>
                  <div><dt>编号</dt><dd>{approval.id}</dd></div>
                </dl>
                <div className="approval-actions">
                  <button className="button button-secondary" type="button" disabled={isGuest} title={isGuest ? '游客模式不能处理审批' : undefined} onClick={() => setDecisions((current) => ({ ...current, [approval.id]: 'rejected' }))}><X size={15} />拒绝</button>
                  <button className="button button-primary" type="button" disabled={isGuest} title={isGuest ? '游客模式不能处理审批' : undefined} onClick={() => setDecisions((current) => ({ ...current, [approval.id]: 'approved' }))}><Check size={15} />批准</button>
                </div>
              </article>
            ))}
            {pending.length === 0 ? <div className="approval-empty"><Check size={24} /><strong>全部处理完成</strong><span>刷新页面可以恢复演示数据。</span></div> : null}
          </div>
        </section>

        <aside className="approval-sidebar">
          <section className="surface policy-panel">
            <div className="panel-header"><div><h2>今日审批概况</h2><p>演示工作区</p></div></div>
            <dl className="policy-stats">
              <div><dt>已批准</dt><dd>{14 + Object.values(decisions).filter((value) => value === 'approved').length}</dd></div>
              <div><dt>已拒绝</dt><dd>{2 + Object.values(decisions).filter((value) => value === 'rejected').length}</dd></div>
              <div><dt>平均处理时间</dt><dd>8 分钟</dd></div>
            </dl>
          </section>
          <section className="surface policy-panel">
            <div className="panel-header"><div><h2>审批策略</h2><p>当前生效规则</p></div></div>
            <div className="policy-list">
              <div><span>客户消息</span><strong>全部审批</strong></div>
              <div><span>CRM 字段更新</span><strong>敏感字段审批</strong></div>
              <div><span>删除与导出</span><strong>全部审批</strong></div>
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}
