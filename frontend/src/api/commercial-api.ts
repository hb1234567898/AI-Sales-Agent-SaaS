import { apiClient, getJson, requestJson } from './axios-client'

export type Money = number | string
export interface CommercialPage<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface Product { id: string; sku: string; name: string; unit: string; listPrice: Money; taxRate: Money; active: boolean; description: string; version: number; updatedAt: string }
export type ProductInput = Omit<Product, 'id' | 'updatedAt' | 'version'> & { version?: number }
export interface PriceBook { id: string; name: string; validFrom: string; validUntil: string; active: boolean; version: number; updatedAt: string }
export interface PriceEntry { productId: string; productName?: string; sku?: string; unitPrice: Money }
export interface PriceBookDetail { book: PriceBook; entries: PriceEntry[] }
export type PriceBookInput = Omit<PriceBook, 'id' | 'updatedAt' | 'version'> & { entries: PriceEntry[]; version?: number }
export interface QuoteLine { position: number; productId: string; sku: string; productName: string; unit: string; quantity: Money; unitPrice: Money; discountPercent: Money; taxRate: Money; subtotal: Money; tax: Money; total: Money }
export interface Quote { id: string; number: string; title: string; opportunityId: string; opportunityName: string; customerId: string; customerName: string; ownerMemberId: string; ownerName: string; contactEmail: string | null; status: string; revision: number; version: number; subtotal: Money; tax: Money; total: Money; validUntil: string; updatedAt: string; approvalId: string | null; approvalStatus: string | null; actionStatus: string | null; deliveryStatus: string | null }
export interface QuoteVersion { id: string; quoteId: string; revision: number; title: string; priceBookId: string | null; priceBookName: string; customerName: string; currency: string; validUntil: string; terms: string; subtotal: Money; tax: Money; total: Money; createdAt: string; createdByName: string; approvalId: string | null }
export interface CommercialEvent { id: string; actorName: string; summary: string; createdAt: string }
export interface QuoteDetail { quote: Quote; versions: QuoteVersion[]; snapshot: QuoteVersion; lines: QuoteLine[]; history: CommercialEvent[] }
export interface QuoteInput { opportunityId: string; title: string; priceBookId: string | null; validUntil: string; terms: string; lines: { productId: string; quantity: Money; discountPercent: Money }[]; version?: number }
export interface QuotePreview { lines: QuoteLine[]; subtotal: Money; tax: Money; total: Money }
export interface Installment { name: string; amount: Money; dueOn: string }
export interface PaymentPlan extends Installment { id: string; contractId: string; contractNumber: string; customerName: string; position: number; paid: Money; status: string }
export interface Contract { id: string; number: string; quoteId: string; quoteVersionId: string; quoteNumber: string; quoteRevision: number; opportunityId: string; customerId: string; customerName: string; ownerMemberId: string; ownerName: string; name: string; total: Money; paid: Money; currency: string; status: string; startsOn: string; endsOn: string; terms: string; signedOn: string | null; signedFileId: string | null; signedFilename: string | null; version: number; updatedAt: string }
export interface Receipt { id: string; planId: string; contractId: string; amount: Money; receivedOn: string; reference: string; note: string; requestKey: string; createdByName: string; createdAt: string }
export interface ContractDetail { contract: Contract; installments: PaymentPlan[]; receipts: Receipt[]; history: CommercialEvent[] }
export interface ContractInput { quoteId: string; name: string; startsOn: string; endsOn: string; terms: string; installments: Installment[]; version?: number }
export interface CommercialMetrics { draftQuotes: number; awaitingCustomer: number; signedAmount: Money; receivable: Money; overdueAmount: Money }
const base = '/api/v1/commercial'
function params(query: string, page: number, extra: Record<string, string> = {}) { return new URLSearchParams({ query, page: String(page), size: '20', ...extra }) }
export function getProducts(query = '', page = 0, active?: boolean, size = 20) { return getJson<CommercialPage<Product>>(`${base}/products?${new URLSearchParams({ query, page: String(page), size: String(size), ...(active == null ? {} : { active: String(active) }) })}`) }
export function saveProduct(input: ProductInput, id?: string) { return requestJson<Product>(`${base}/products${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', data: input }) }
export function getPriceBooks() { return getJson<PriceBook[]>(`${base}/price-books`) }
export function getPriceBook(id: string) { return getJson<PriceBookDetail>(`${base}/price-books/${id}`) }
export function savePriceBook(input: PriceBookInput, id?: string) { return requestJson<PriceBookDetail>(`${base}/price-books${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', data: input }) }
export function getCommercialMetrics() { return getJson<CommercialMetrics>(`${base}/metrics`) }
export function getQuotes(query = '', status = '', page = 0) { return getJson<CommercialPage<Quote>>(`${base}/quotes?${params(query, page, { status })}`) }
export function getQuote(id: string, revision?: number) { return getJson<QuoteDetail>(`${base}/quotes/${id}${revision ? `?revision=${revision}` : ''}`) }
export function previewQuote(input: QuoteInput) { return requestJson<QuotePreview>(`${base}/quotes/preview`, { method: 'POST', data: input }) }
export function saveQuote(input: QuoteInput, id?: string) { return requestJson<QuoteDetail>(`${base}/quotes${id ? `/${id}/versions` : ''}`, { method: 'POST', data: input }) }
export function decideQuote(id: string, action: string, version: number, reason: string) { return requestJson<QuoteDetail>(`${base}/quotes/${id}/decisions/${action}`, { method: 'POST', data: { version, reason } }) }
export function sendQuote(id: string, data: { version: number; to: string; subject: string; body: string }) { return requestJson<{ approvalId: string; runId: string }>(`${base}/quotes/${id}/send`, { method: 'POST', data }) }
export function getContracts(query = '', status = '', page = 0) { return getJson<CommercialPage<Contract>>(`${base}/contracts?${params(query, page, { status })}`) }
export function getContract(id: string) { return getJson<ContractDetail>(`${base}/contracts/${id}`) }
export function saveContract(input: ContractInput, id?: string) { return requestJson<ContractDetail>(`${base}/contracts${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', data: input }) }
export function signContract(id: string, data: { version: number; signedOn: string; signedFileId: string }) { return requestJson<ContractDetail>(`${base}/contracts/${id}/sign`, { method: 'POST', data }) }
export function cancelContract(id: string, version: number, reason: string) { return requestJson<ContractDetail>(`${base}/contracts/${id}/cancel`, { method: 'POST', data: { version, reason } }) }
export function receivePayment(id: string, plan: string, data: { version: number; amount: Money; receivedOn: string; reference: string; note: string; requestKey: string }) { return requestJson<ContractDetail>(`${base}/contracts/${id}/installments/${plan}/receipts`, { method: 'POST', data }) }
export function getReceivables(overdue: boolean, page = 0) { return getJson<CommercialPage<PaymentPlan>>(`${base}/receivables?${new URLSearchParams({ overdue: String(overdue), page: String(page), size: '20' })}`) }
export async function downloadCommercial(path: string, filename: string) {
  const response = await apiClient.get<Blob>(`${base}${path}`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data), anchor = document.createElement('a')
  anchor.href = url; anchor.download = filename; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
