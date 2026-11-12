import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import * as api from '../api/commercial-api'
import { getCustomerFiles, uploadCustomerFile } from '../api/files-api'
import { getOpportunities, getOpportunity } from '../api/opportunities-api'
import { demoBooks, demoContract, demoContracts, demoProducts, demoQuote, demoQuotes } from '../data/commercial-demo'
import { ProductCatalogPage } from './ProductCatalogPage'
import { QuotesPage } from './QuotesPage'
import { ContractsPage } from './ContractsPage'
import { QuoteEditor } from '../components/commercial/QuoteEditor'
import { ContractEditor } from '../components/commercial/ContractEditor'

const auth = vi.hoisted(() => ({ role: 'OWNER', memberId: 'guest' }))
vi.mock('../auth/use-auth', () => ({ useAuth: () => auth }))
vi.mock('../api/commercial-api', () => ({ getProducts: vi.fn(), saveProduct: vi.fn(), getPriceBooks: vi.fn(), getPriceBook: vi.fn(), savePriceBook: vi.fn(), getCommercialMetrics: vi.fn(), getQuotes: vi.fn(), getQuote: vi.fn(), previewQuote: vi.fn(), saveQuote: vi.fn(), decideQuote: vi.fn(), sendQuote: vi.fn(), getContracts: vi.fn(), getContract: vi.fn(), saveContract: vi.fn(), signContract: vi.fn(), cancelContract: vi.fn(), receivePayment: vi.fn(), getReceivables: vi.fn(), downloadCommercial: vi.fn() }))
vi.mock('../api/files-api', () => ({ getCustomerFiles: vi.fn(), uploadCustomerFile: vi.fn() }))
vi.mock('../api/opportunities-api', () => ({ getOpportunities: vi.fn(), getOpportunity: vi.fn() }))
function page<T,>(content: T[]): api.CommercialPage<T> { return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true } }
function mount(node: ReactNode, path = '/app/quotes') { return render(<MemoryRouter initialEntries={[path]}><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{node}</QueryClientProvider></MemoryRouter>) }
const originalShow = HTMLDialogElement.prototype.showModal, originalClose = HTMLDialogElement.prototype.close
beforeAll(() => { HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }; HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') } })
afterAll(() => { HTMLDialogElement.prototype.showModal = originalShow; HTMLDialogElement.prototype.close = originalClose })
afterEach(cleanup)
beforeEach(() => {
  vi.resetAllMocks(); auth.role = 'OWNER'
  vi.mocked(api.getProducts).mockResolvedValue(page(demoProducts)); vi.mocked(api.getPriceBooks).mockResolvedValue(demoBooks)
  vi.mocked(api.getPriceBook).mockResolvedValue({ book: demoBooks[0], entries: [{ productId: demoProducts[0].id, productName: demoProducts[0].name, unitPrice: 3200 }] })
  vi.mocked(api.getQuotes).mockResolvedValue(page(demoQuotes)); vi.mocked(api.getQuote).mockImplementation(async (id, revision) => demoQuote(id, revision)!)
  vi.mocked(api.getCommercialMetrics).mockResolvedValue({ draftQuotes: 1, awaitingCustomer: 1, signedAmount: 57664, receivable: 28832, overdueAmount: 28832 })
  vi.mocked(api.getContracts).mockResolvedValue(page(demoContracts)); vi.mocked(api.getContract).mockResolvedValue(demoContract(demoContracts[0].id)!)
  vi.mocked(api.getReceivables).mockResolvedValue(page(demoContract(demoContracts[0].id)!.installments.filter(p => p.status !== 'PAID')))
  vi.mocked(getCustomerFiles).mockResolvedValue([]); vi.mocked(getOpportunities).mockResolvedValue(page([]))
  vi.mocked(getOpportunity).mockResolvedValue({ id: 'demo-opportunity-1', customerId: 'demo-customer-1', company: '云岚科技', name: 'Platform', ownerMemberId: 'guest', ownerName: 'Sales', stage: 'PROPOSAL', status: 'OPEN', amount: 0, probability: 60, expectedCloseDate: null, forecastCategory: 'PIPELINE', nextStep: null, closeReason: null, updatedAt: '', stageChangedAt: '', version: 0 })
})
it('guests can browse price books but cannot create or edit products', async () => {
  auth.role = 'GUEST'; const user = userEvent.setup(); mount(<ProductCatalogPage />)
  expect(await screen.findByText(demoProducts[0].name)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '新建产品' })).toBeDisabled()
  await user.click(screen.getByRole('tab', { name: '价格表' })); await user.click(await screen.findByRole('button', { name: demoBooks[0].name }))
  expect(await screen.findByRole('dialog')).toBeInTheDocument(); expect(screen.queryByRole('button', { name: '保存价格表' })).not.toBeInTheDocument()
  expect(api.saveProduct).not.toHaveBeenCalled(); expect(api.savePriceBook).not.toHaveBeenCalled()
})
it('edits a product with its optimistic version and decimal price', async () => {
  vi.mocked(api.saveProduct).mockResolvedValue(demoProducts[0]); const user = userEvent.setup(); mount(<ProductCatalogPage />)
  await user.click(await screen.findByRole('button', { name: `编辑 ${demoProducts[0].name}` }))
  await user.clear(screen.getByLabelText('标准单价（元，未税）')); await user.type(screen.getByLabelText('标准单价（元，未税）'), '3999.99')
  await user.click(screen.getByRole('button', { name: '保存产品' }))
  await waitFor(() => expect(api.saveProduct).toHaveBeenCalledWith(expect.objectContaining({ listPrice: '3999.99', version: 0 }), demoProducts[0].id))
})
it('shows failures without falling back to simulated records for signed-in users', async () => {
  vi.mocked(api.getQuotes).mockRejectedValue(new Error('数据库暂不可用')); mount(<QuotesPage />)
  expect(await screen.findByRole('alert')).toHaveTextContent('数据库暂不可用'); expect(screen.queryByText(demoQuotes[0].title)).not.toBeInTheDocument()
})
it('switches to immutable historical quote versions and hides state-changing actions', async () => {
  const user = userEvent.setup(); mount(<QuotesPage />); await user.click(await screen.findByRole('button', { name: demoQuotes[0].title }))
  await user.click(await screen.findByRole('combobox', { name: '报价历史版本' })); await user.click(screen.getByRole('option', { name: 'V1 · 历史快照' }))
  await waitFor(() => expect(api.getQuote).toHaveBeenCalledWith(demoQuotes[0].id, 1))
  expect(screen.queryByRole('link', { name: '生成 / 查看合同' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '导出 CSV' }))
  await waitFor(() => expect(api.downloadCommercial).toHaveBeenCalledWith(`/quotes/${demoQuotes[0].id}/versions/1/export`, `${demoQuotes[0].number}-V1.csv`))
})
it('requires current server-side calculation before saving a quote and invalidates changed inputs', async () => {
  const detail = demoQuote(demoQuotes[2].id)!, onSave = vi.fn(), user = userEvent.setup()
  vi.mocked(api.previewQuote).mockResolvedValue({ lines: detail.lines, subtotal: detail.snapshot.subtotal, tax: detail.snapshot.tax, total: detail.snapshot.total })
  mount(<QuoteEditor detail={detail} busy={false} error={null} onClose={vi.fn()} onSave={onSave} />)
  expect(screen.getByRole('button', { name: '保存为草稿' })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: '计算报价' }))
  await waitFor(() => expect(screen.getByRole('button', { name: '保存为草稿' })).toBeEnabled())
  await user.clear(screen.getByLabelText('第 1 行数量')); await user.type(screen.getByLabelText('第 1 行数量'), '2')
  expect(screen.getByRole('button', { name: '保存为草稿' })).toBeDisabled(); expect(onSave).not.toHaveBeenCalled()
})
it('requests customer confirmation with an explicit reason and expected version', async () => {
  vi.mocked(api.decideQuote).mockResolvedValue(demoQuote(demoQuotes[1].id)!); const user = userEvent.setup(); mount(<QuotesPage />)
  await user.click(await screen.findByRole('button', { name: demoQuotes[1].title })); await user.click(await screen.findByRole('button', { name: '登记客户确认' }))
  await user.type(screen.getByLabelText('客户确认依据'), '客户邮件确认'); const dialog = screen.getByRole('dialog')
  await user.click(within(dialog).getByRole('button', { name: '登记客户确认' }))
  await waitFor(() => expect(api.decideQuote).toHaveBeenCalledWith(demoQuotes[1].id, 'accept', 1, '客户邮件确认'))
})
it('opens the specific quote revision referenced by a contract link', async () => {
  mount(<QuotesPage />, '/app/quotes?quote=demo-quote-1&revision=1')
  await waitFor(() => expect(api.getQuote).toHaveBeenCalledWith('demo-quote-1', 1))
  expect(await screen.findByRole('dialog', { name: `${demoQuotes[0].number} · V1` })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: '生成 / 查看合同' })).not.toBeInTheDocument()
})
it('contract installments must sum to the fixed accepted quote amount', async () => {
  const detail = demoContract(demoContracts[0].id)!, user = userEvent.setup()
  mount(<ContractEditor detail={detail} busy={false} error={null} onClose={vi.fn()} onSave={vi.fn()} />)
  expect(screen.getByRole('button', { name: '保存合同草稿' })).toBeEnabled()
  await user.clear(screen.getByLabelText('第 1 期金额')); await user.type(screen.getByLabelText('第 1 期金额'), '1')
  expect(screen.getByRole('button', { name: '保存合同草稿' })).toBeDisabled()
})
it('does not upload a selected signed file until the user confirms signing', async () => {
  const detail = demoContract(demoContracts[0].id)!, draft = { ...detail, contract: { ...detail.contract, status: 'DRAFT' } }
  vi.mocked(api.getContract).mockResolvedValue(draft)
  vi.mocked(uploadCustomerFile).mockResolvedValue({ id: 'signed-file', customerId: draft.contract.customerId, filename: 'signed.pdf', sizeBytes: 8, sha256: '', contentType: 'application/pdf', createdAt: '' })
  vi.mocked(api.signContract).mockResolvedValue(detail)
  const user = userEvent.setup(); mount(<ContractsPage />, '/app/contracts')
  await user.click(await screen.findByRole('button', { name: draft.contract.name })); await user.click(await screen.findByRole('button', { name: '登记签约' }))
  await user.upload(screen.getByLabelText('选择签署文件'), new File(['%PDF-test'], 'signed.pdf', { type: 'application/pdf' }))
  expect(uploadCustomerFile).not.toHaveBeenCalled(); await user.click(screen.getByRole('button', { name: '确认签约' }))
  await waitFor(() => expect(api.signContract).toHaveBeenCalledWith(draft.contract.id, expect.objectContaining({ signedFileId: 'signed-file', version: 2 })))
})
it('retains the same receipt idempotency key when retrying an uncertain response', async () => {
  vi.mocked(api.receivePayment).mockRejectedValueOnce(new Error('网络中断')).mockResolvedValueOnce(demoContract(demoContracts[0].id)!)
  const user = userEvent.setup(); mount(<ContractsPage />, '/app/contracts')
  await user.click(await screen.findByRole('button', { name: demoContracts[0].name })); await user.click((await screen.findAllByRole('button', { name: '登记回款' })).find(b => !b.hasAttribute('disabled'))!)
  await user.type(screen.getByLabelText('银行 / 支付流水号'), 'BANK-001'); await user.click(screen.getByRole('button', { name: '确认登记' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('网络中断'); await user.click(screen.getByRole('button', { name: '确认登记' }))
  await waitFor(() => expect(api.receivePayment).toHaveBeenCalledTimes(2))
  const [first, second] = vi.mocked(api.receivePayment).mock.calls
  expect(first[2].requestKey).toBe(second[2].requestKey); expect(first[2]).toMatchObject({ version: 2, reference: 'BANK-001', amount: '28832.00' })
})
it('receivables allow an overdue-only view and guest receipt actions remain disabled', async () => {
  auth.role = 'GUEST'; const user = userEvent.setup(); mount(<ContractsPage />, '/app/contracts')
  expect(await screen.findByText(demoContracts[0].name)).toBeInTheDocument(); await user.click(screen.getByRole('tab', { name: '应收回款' })); await user.click(screen.getByLabelText('只看逾期'))
  await waitFor(() => expect(api.getReceivables).toHaveBeenCalledWith(true, 0))
  await user.click(await screen.findByRole('button', { name: '查看合同' }))
  for (const button of await screen.findAllByRole('button', { name: '登记回款' })) expect(button).toBeDisabled()
})
