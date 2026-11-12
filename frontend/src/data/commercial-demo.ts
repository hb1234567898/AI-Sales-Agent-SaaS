import type { Contract, ContractDetail, PaymentPlan, PriceBook, Product, Quote, QuoteDetail, QuoteLine, QuoteVersion } from '../api/commercial-api'

const now = new Date().toISOString()
export const demoProducts: Product[] = [
  { id: 'demo-product-1', sku: 'CRM-PRO', name: '销售协同专业版', unit: '人/年', listPrice: 3600, taxRate: 6, active: true, description: '客户管理、商机与销售协同', version: 0, updatedAt: now },
  { id: 'demo-product-2', sku: 'SETUP', name: '实施与数据迁移服务', unit: '项', listPrice: 18000, taxRate: 6, active: true, description: '部署、数据迁移和团队培训', version: 0, updatedAt: now },
  { id: 'demo-product-3', sku: 'AI-SALES', name: '销售智能体增值服务', unit: '年', listPrice: 24000, taxRate: 6, active: true, description: '销售分析与企业知识库', version: 0, updatedAt: now },
]
const day = (offset: number) => { const d = new Date(); d.setDate(d.getDate() + offset); return d.toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }) }
export const demoBooks: PriceBook[] = [{ id: 'demo-book-1', name: '企业年度合作价', validFrom: day(-60), validUntil: day(120), active: true, version: 0, updatedAt: now }]
const lines: QuoteLine[] = [
  { position: 1, productId: demoProducts[0].id, sku: 'CRM-PRO', productName: demoProducts[0].name, unit: '人/年', quantity: 12, unitPrice: 3200, discountPercent: 0, taxRate: 6, subtotal: 38400, tax: 2304, total: 40704 },
  { position: 2, productId: demoProducts[1].id, sku: 'SETUP', productName: demoProducts[1].name, unit: '项', quantity: 1, unitPrice: 16000, discountPercent: 0, taxRate: 6, subtotal: 16000, tax: 960, total: 16960 },
]
export const demoQuotes: Quote[] = [
  { id: 'demo-quote-1', number: 'QT-202611-001', title: '云岚科技年度销售协同方案', opportunityId: 'demo-opportunity-1', opportunityName: '云岚科技销售协同平台', customerId: 'demo-customer-1', customerName: '云岚科技', ownerMemberId: 'guest', ownerName: '陈默', contactEmail: 'lin@example.com', status: 'ACCEPTED', revision: 2, version: 3, subtotal: 54400, tax: 3264, total: 57664, validUntil: day(30), updatedAt: now, approvalId: null, approvalStatus: null, actionStatus: null, deliveryStatus: null },
  { id: 'demo-quote-2', number: 'QT-202611-002', title: '恒川智造试点部署报价', opportunityId: 'demo-opportunity-2', opportunityName: '恒川智造数字化试点', customerId: 'demo-customer-2', customerName: '恒川智造', ownerMemberId: 'guest', ownerName: '李昕', contactEmail: 'zhou@example.com', status: 'ISSUED', revision: 1, version: 1, subtotal: 54400, tax: 3264, total: 57664, validUntil: day(15), updatedAt: now, approvalId: null, approvalStatus: null, actionStatus: null, deliveryStatus: null },
  { id: 'demo-quote-3', number: 'QT-202611-003', title: '云岚科技智能体增购报价', opportunityId: 'demo-opportunity-1', opportunityName: '云岚科技销售协同平台', customerId: 'demo-customer-1', customerName: '云岚科技', ownerMemberId: 'guest', ownerName: '陈默', contactEmail: 'lin@example.com', status: 'DRAFT', revision: 1, version: 0, subtotal: 24000, tax: 1440, total: 25440, validUntil: day(30), updatedAt: now, approvalId: null, approvalStatus: null, actionStatus: null, deliveryStatus: null },
]
export function demoQuote(id: string, revision?: number): QuoteDetail | null {
  const quote = demoQuotes.find(q => q.id === id)
  if (!quote) return null
  const versions: QuoteVersion[] = Array.from({ length: quote.revision }, (_, i) => ({ id: `${id}-v${i + 1}`, quoteId: id, revision: i + 1, title: quote.title, priceBookId: demoBooks[0].id, priceBookName: demoBooks[0].name, customerName: quote.customerName, currency: 'CNY', validUntil: quote.validUntil, terms: '报价含税，服务期一年；具体交付范围以签署合同为准。', subtotal: 54400, tax: 3264, total: 57664, createdAt: now, createdByName: quote.ownerName, approvalId: null })).reverse()
  const snapshot = versions.find(v => v.revision === (revision ?? quote.revision)) ?? versions[0]
  const snapshotLines = id === 'demo-quote-3' ? [{ ...lines[0], productId: 'demo-product-3', sku: 'AI-SALES', productName: demoProducts[2].name, quantity: 1, unit: '年', unitPrice: 24000, subtotal: 24000, tax: 1440, total: 25440 }] : lines
  if (id === 'demo-quote-3') Object.assign(snapshot, { subtotal: 24000, tax: 1440, total: 25440 })
  return { quote, versions, snapshot, lines: snapshotLines, history: [{ id: `${id}-event`, actorName: quote.ownerName, summary: quote.status === 'ACCEPTED' ? '登记客户确认：客户已确认产品范围与年度报价' : '保存报价版本', createdAt: now }] }
}
export const demoContracts: Contract[] = [{ id: 'demo-contract-1', number: 'CT-202611-001', quoteId: 'demo-quote-1', quoteVersionId: 'demo-quote-1-v2', quoteNumber: demoQuotes[0].number, quoteRevision: 2, opportunityId: 'demo-opportunity-1', customerId: 'demo-customer-1', customerName: '云岚科技', ownerMemberId: 'guest', ownerName: '陈默', name: '云岚科技年度销售协同服务合同', total: 57664, paid: 28832, currency: 'CNY', status: 'SIGNED', startsOn: day(-30), endsOn: day(335), terms: '签约支付 50%，交付验收支付尾款。', signedOn: day(-30), signedFileId: null, signedFilename: '云岚科技服务合同-签署版.pdf', version: 2, updatedAt: now }]
export const demoPlans: PaymentPlan[] = [
  { id: 'demo-plan-1', contractId: demoContracts[0].id, contractNumber: demoContracts[0].number, customerName: '云岚科技', position: 1, name: '签约首付款', amount: 28832, paid: 28832, dueOn: day(-30), status: 'PAID' },
  { id: 'demo-plan-2', contractId: demoContracts[0].id, contractNumber: demoContracts[0].number, customerName: '云岚科技', position: 2, name: '交付验收尾款', amount: 28832, paid: 0, dueOn: day(-3), status: 'OVERDUE' },
]
export function demoContract(id: string): ContractDetail | null { const contract = demoContracts.find(c => c.id === id); return contract ? { contract, installments: demoPlans, receipts: [{ id: 'demo-receipt-1', contractId: id, planId: 'demo-plan-1', amount: 28832, receivedOn: day(-30), reference: 'DEMO-TRANSFER-001', note: '首付款已登记（模拟）', requestKey: 'demo-key', createdByName: '陈默', createdAt: now }], history: [{ id: 'demo-contract-event', actorName: '陈默', summary: '登记签约并将关联商机标记为赢单', createdAt: now }] } : null }
export function commercialGuestData(route: string, params: URLSearchParams): unknown {
  const page = <T,>(items: T[]) => { const index = Number(params.get('page')) || 0, size = Number(params.get('size')) || 20; return { content: items.slice(index * size, (index + 1) * size), page: index, size, totalElements: items.length, totalPages: Math.ceil(items.length / size), first: index === 0, last: (index + 1) * size >= items.length } }
  const keyword = (params.get('query') ?? '').toLowerCase(), status = params.get('status') ?? ''
  if (route.endsWith('/metrics')) return { draftQuotes: 1, awaitingCustomer: 1, signedAmount: 57664, receivable: 28832, overdueAmount: 28832 }
  if (route.endsWith('/products')) return page(demoProducts.filter(p => `${p.sku}${p.name}`.toLowerCase().includes(keyword) && (!params.has('active') || p.active === (params.get('active') === 'true'))))
  if (route.endsWith('/price-books')) return demoBooks
  if (route.includes('/price-books/')) return { book: demoBooks[0], entries: [{ productId: 'demo-product-1', productName: demoProducts[0].name, sku: 'CRM-PRO', unitPrice: 3200 }, { productId: 'demo-product-2', productName: demoProducts[1].name, sku: 'SETUP', unitPrice: 16000 }] }
  if (route.endsWith('/quotes')) return page(demoQuotes.filter(q => (!status || q.status === status) && `${q.number}${q.title}${q.customerName}`.toLowerCase().includes(keyword)))
  if (route.includes('/quotes/')) return demoQuote(route.split('/').at(-1)!, Number(params.get('revision')) || undefined)
  if (route.endsWith('/contracts')) return page(demoContracts.filter(c => (!status || c.status === status) && (`${c.number}${c.name}${c.customerName}`.toLowerCase().includes(keyword) || c.quoteId === keyword)))
  if (route.includes('/contracts/')) return demoContract(route.split('/').at(-1)!)
  if (route.endsWith('/receivables')) return page(demoPlans.filter(p => p.status !== 'PAID' && (params.get('overdue') !== 'true' || p.status === 'OVERDUE')))
  return null
}
