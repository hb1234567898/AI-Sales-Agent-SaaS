import { ArrowClockwise, CaretLeft, CaretRight, MagnifyingGlass, X } from '@phosphor-icons/react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { dateTime, message, money, statusLabel, useCommercialRole } from './commercial-utils'
import type { CommercialEvent, CommercialPage, Money, QuoteLine } from '../../api/commercial-api'
import './commercial.css'

export function Status({ value }: { value: string }) { return <span className={`commercial-status status-${value.toLowerCase()}`}>{statusLabel(value)}</span> }
export function PageHeading({ title, children }: { title: string; children?: ReactNode }) {
  const { guest } = useCommercialRole()
  return <header className="commercial-heading"><div><h1>{title}</h1>{guest && <span className="commercial-demo">模拟数据 · 只读</span>}</div><div className="commercial-actions">{children}</div></header>
}
export function Dialog({ title, children, onClose, footer, busy = false, wide = false }: { title: string; children: ReactNode; onClose: () => void; footer?: ReactNode; busy?: boolean; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null), titleId = useId()
  useEffect(() => { const node = ref.current; node?.showModal(); return () => node?.close() }, [])
  return <dialog ref={ref} className={`commercial-dialog${wide ? ' is-wide' : ''}`} aria-labelledby={titleId} onCancel={e => { e.preventDefault(); if (!busy) onClose() }}>
    <header><h2 id={titleId}>{title}</h2><button className="commercial-icon" type="button" title="关闭" aria-label="关闭" disabled={busy} onClick={onClose}><X size={20} /></button></header>
    <div className="commercial-dialog-body"><fieldset disabled={busy} className="commercial-dialog-content">{children}</fieldset></div>{footer && <footer>{footer}</footer>}
  </dialog>
}
export function ErrorNotice({ error }: { error: unknown }) { return error ? <div role="alert" className="commercial-error">{message(error)}</div> : null }
export function Search({ value, onChange, placeholder = '搜索' }: { value: string; onChange: (value: string) => void; placeholder?: string }) {
  return <label className="commercial-search"><MagnifyingGlass size={17} /><input type="search" aria-label={placeholder} placeholder={placeholder} value={value} onChange={e => onChange(e.target.value)} /></label>
}
export function LoadState({ pending, error, empty, retry }: { pending: boolean; error: unknown; empty: boolean; retry: () => void }) {
  if (error) return <div className="commercial-empty"><ErrorNotice error={error} /><button className="button button-secondary" type="button" onClick={retry}><ArrowClockwise size={16} />重试</button></div>
  if (pending || empty) return <div className="commercial-empty" role="status">{pending ? '正在加载…' : '暂无记录'}</div>
  return null
}
export function Pager({ data, onChange }: { data: CommercialPage<unknown> | undefined; onChange: (page: number) => void }) {
  if (!data) return null
  return <div className="commercial-pager"><span>共 {data.totalElements} 条 · 第 {data.totalPages ? data.page + 1 : 0} / {data.totalPages} 页</span><div><button className="commercial-icon" type="button" title="上一页" aria-label="上一页" disabled={data.first} onClick={() => onChange(data.page - 1)}><CaretLeft size={18} /></button><button className="commercial-icon" type="button" title="下一页" aria-label="下一页" disabled={data.last} onClick={() => onChange(data.page + 1)}><CaretRight size={18} /></button></div></div>
}
export function History({ events }: { events: CommercialEvent[] }) {
  return <section className="commercial-section"><h3>操作记录</h3><ol className="commercial-history">{events.map(e => <li key={e.id}><span>{e.summary}</span><small>{e.actorName} · {dateTime(e.createdAt)}</small></li>)}</ol></section>
}
export function Lines({ lines, subtotal, tax, total }: { lines: QuoteLine[]; subtotal: Money; tax: Money; total: Money }) {
  return <><div className="commercial-table-wrap"><table className="commercial-table"><thead><tr><th>产品 / 服务</th><th>数量</th><th>未税单价</th><th>折扣</th><th>税率</th><th className="numeric">含税金额</th></tr></thead><tbody>{lines.map(line => <tr key={line.position}><td><strong>{line.productName}</strong><small>{line.sku} · {line.unit}</small></td><td>{Number(line.quantity)}</td><td>{money(line.unitPrice)}</td><td>{Number(line.discountPercent)}%</td><td>{Number(line.taxRate)}%</td><td className="numeric">{money(line.total)}</td></tr>)}</tbody></table></div><dl className="commercial-totals"><div><dt>未税合计</dt><dd>{money(subtotal)}</dd></div><div><dt>税额</dt><dd>{money(tax)}</dd></div><div><dt>含税总额</dt><dd>{money(total)}</dd></div></dl></>
}
