import { Plus, Trash, Calculator } from '@phosphor-icons/react'
import { useDeferredValue, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getPriceBooks, getProducts, previewQuote, type QuoteDetail, type QuoteInput } from '../../api/commercial-api'
import { getOpportunities, getOpportunity } from '../../api/opportunities-api'
import { SelectField, type SelectOption } from '../forms/SelectField'
import { Dialog, ErrorNotice, Lines, Search } from './CommercialUI'
import { today, useCommercialRole } from './commercial-utils'

export function QuoteEditor({ detail, opportunityId = '', busy, error, onClose, onSave }: { detail: QuoteDetail | null; opportunityId?: string; busy: boolean; error: unknown; onClose: () => void; onSave: (input: QuoteInput) => void }) {
  const { manager, memberId } = useCommercialRole()
  const [form, setForm] = useState<QuoteInput>(() => detail ? { opportunityId: detail.quote.opportunityId, title: detail.snapshot.title, priceBookId: detail.snapshot.priceBookId, validUntil: detail.snapshot.validUntil, terms: detail.snapshot.terms, version: detail.quote.version, lines: detail.lines.map(l => ({ productId: l.productId, quantity: l.quantity, discountPercent: l.discountPercent })) } : { opportunityId, title: '', priceBookId: null, validUntil: today(), terms: '', lines: [{ productId: '', quantity: 1, discountPercent: 0 }] })
  const [productSearch, setProductSearch] = useState(''), [opportunitySearch, setOpportunitySearch] = useState('')
  const [pickedProducts, setPickedProducts] = useState<SelectOption[]>([])
  const productQuery = useDeferredValue(productSearch), opportunityQuery = useDeferredValue(opportunitySearch)
  const products = useQuery({ queryKey: ['commercial', 'product-options', productQuery], queryFn: () => getProducts(productQuery, 0, true, 100) })
  const opportunities = useQuery({ queryKey: ['commercial', 'opportunity-options', opportunityQuery, manager, memberId], queryFn: () => getOpportunities({ query: opportunityQuery, size: 100, ...(manager ? {} : { owner: memberId }) }) })
  const selected = useQuery({ queryKey: ['commercial', 'opportunity-option', form.opportunityId], queryFn: () => getOpportunity(form.opportunityId), enabled: Boolean(form.opportunityId) && !detail })
  const books = useQuery({ queryKey: ['commercial', 'books'], queryFn: getPriceBooks })
  const preview = useMutation({ mutationFn: async (input: QuoteInput) => ({ signature: JSON.stringify(input), value: await previewQuote(input) }) })
  const currentPreview = preview.data?.signature === JSON.stringify(form) ? preview.data.value : null
  const productOptions = [...new Map([...pickedProducts.map(p => [p.value, p] as const), ...(detail?.lines ?? []).map(l => [l.productId, { value: l.productId, label: `${l.sku} · ${l.productName}` }] as const), ...(products.data?.content ?? []).map(p => [p.id, { value: p.id, label: `${p.sku} · ${p.name}` }] as const)]).values()]
  function pickProduct(index: number, id: string) {
    const option = productOptions.find(p => p.value === id)
    if (option) setPickedProducts(previous => [...previous.filter(p => p.value !== id), option])
    setForm({ ...form, lines: form.lines.map((line, i) => i === index ? { ...line, productId: id } : line) })
  }
  const opportunityOptions = [...new Map([...(opportunities.data?.content ?? []).filter(o => o.status === 'OPEN').map(o => [o.id, { value: o.id, label: `${o.company} · ${o.name}` }] as const), ...(selected.data ? [[selected.data.id, { value: selected.data.id, label: `${selected.data.company} · ${selected.data.name}` }] as const] : []), ...(detail ? [[detail.quote.opportunityId, { value: detail.quote.opportunityId, label: `${detail.quote.customerName} · ${detail.quote.opportunityName}` }] as const] : [])]).values()]
  const complete = form.opportunityId && form.title.trim() && form.validUntil && form.lines.length > 0 && form.lines.every(l => l.productId && Number(l.quantity) > 0 && Number(l.discountPercent) >= 0)
  return <Dialog wide title={detail ? `新建报价版本 · V${detail.quote.revision + 1}` : '新建报价单'} busy={busy || preview.isPending} onClose={onClose} footer={<><button className="button button-secondary" disabled={busy || preview.isPending} onClick={onClose}>取消</button><button className="button button-secondary" type="submit" form="commercial-quote-form" disabled={!complete || busy || preview.isPending}><Calculator size={16} />{preview.isPending ? '计算中…' : '计算报价'}</button><button className="button button-primary" disabled={!currentPreview || busy || preview.isPending} onClick={() => onSave(form)}>{busy ? '保存中…' : '保存为草稿'}</button></>}>
    <form id="commercial-quote-form" className="commercial-form" onSubmit={e => { e.preventDefault(); preview.mutate(form) }}>
      <div className="commercial-fields">{!detail && <Search value={opportunitySearch} onChange={setOpportunitySearch} placeholder="查找商机（最多显示100项）" />}
        <label className="commercial-field">关联商机<SelectField portal ariaLabel="关联商机" value={form.opportunityId} disabled={Boolean(detail)} options={opportunityOptions} onChange={value => setForm({ ...form, opportunityId: value })} /></label>
        <label className="commercial-field">报价名称<input required maxLength={200} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} /></label>
        <label className="commercial-field">价格来源<SelectField portal ariaLabel="价格来源" value={form.priceBookId ?? ''} options={[{ value: '', label: '产品标准价' }, ...(books.data ?? []).filter(b => b.active && b.validFrom <= today() && b.validUntil >= today()).map(b => ({ value: b.id, label: b.name }))]} onChange={value => setForm({ ...form, priceBookId: value || null })} /></label>
        <label className="commercial-field">报价有效期<input required type="date" min={today()} value={form.validUntil} onChange={e => setForm({ ...form, validUntil: e.target.value })} /></label>
      </div><ErrorNotice error={opportunities.error ?? selected.error ?? books.error} />
      <Search value={productSearch} onChange={setProductSearch} placeholder="查找可添加的产品" /><ErrorNotice error={products.error} />
      <div className="commercial-table-wrap"><table className="commercial-table commercial-editor-table"><thead><tr><th>产品 / 服务</th><th>数量</th><th>折扣（%）</th><th>操作</th></tr></thead><tbody>{form.lines.map((line, i) => <tr key={i}><td><SelectField portal ariaLabel={`第 ${i + 1} 行产品`} value={line.productId} options={productOptions.map(o => ({ ...o, disabled: form.lines.some((l, j) => j !== i && l.productId === o.value) }))} onChange={value => pickProduct(i, value)} /></td><td><input required aria-label={`第 ${i + 1} 行数量`} type="number" min="0.001" max="1000000" step="0.001" value={line.quantity} onChange={e => setForm({ ...form, lines: form.lines.map((l, j) => j === i ? { ...l, quantity: e.target.value } : l) })} /></td><td><input required aria-label={`第 ${i + 1} 行折扣`} type="number" min="0" max="99.99" step="0.01" value={line.discountPercent} onChange={e => setForm({ ...form, lines: form.lines.map((l, j) => j === i ? { ...l, discountPercent: e.target.value } : l) })} /></td><td><button type="button" className="commercial-icon" title="移除此行" aria-label={`移除第 ${i + 1} 行`} onClick={() => setForm({ ...form, lines: form.lines.filter((_, j) => j !== i) })}><Trash size={16} /></button></td></tr>)}</tbody></table></div>
      <button type="button" className="button button-secondary" disabled={form.lines.length >= 50} onClick={() => setForm({ ...form, lines: [...form.lines, { productId: '', quantity: 1, discountPercent: 0 }] })}><Plus size={16} />添加明细</button>
      <label className="commercial-field">报价条款<textarea maxLength={5000} value={form.terms} onChange={e => setForm({ ...form, terms: e.target.value })} /></label>
      {currentPreview && <section className="commercial-section"><h3>报价核算</h3><Lines {...currentPreview} /></section>}<ErrorNotice error={preview.error ?? error} />
    </form>
  </Dialog>
}
