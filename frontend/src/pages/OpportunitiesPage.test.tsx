import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { OpportunitiesPage } from './OpportunitiesPage'
import { changeOpportunityStage, createOpportunity, getOpportunities, getOpportunity, getOpportunityHistory, getOpportunityMetrics, updateOpportunity, type Opportunity, type OpportunityPage } from '../api/opportunities-api'
import { getCustomers, getCustomerOwners } from '../api/customers-api'

const auth = vi.hoisted(() => ({ guest: false }))
vi.mock('../auth/use-auth', () => ({ useIsGuest: () => auth.guest }))
vi.mock('../api/opportunities-api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/opportunities-api')>(),
  getOpportunities: vi.fn(), getOpportunity: vi.fn(), getOpportunityMetrics: vi.fn(), getOpportunityHistory: vi.fn(),
  createOpportunity: vi.fn(), updateOpportunity: vi.fn(), changeOpportunityStage: vi.fn(),
}))
vi.mock('../api/customers-api', () => ({ getCustomers: vi.fn(), getCustomerOwners: vi.fn() }))

const opportunity: Opportunity = {
  id: 'opportunity-1', customerId: 'customer-1', company: '真实客户', name: '销售平台采购', ownerMemberId: 'member-1',
  ownerName: '销售甲', stage: 'QUALIFIED', status: 'OPEN', amount: 100000, probability: 20,
  expectedCloseDate: '2026-12-01', forecastCategory: 'PIPELINE', nextStep: '安排演示', closeReason: null,
  stageChangedAt: new Date().toISOString(), updatedAt: new Date().toISOString(), version: 3,
}
function page(content: Opportunity[]): OpportunityPage {
  return { content, page: 0, size: 50, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true }
}
function renderPage() {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><OpportunitiesPage /></QueryClientProvider>)
}

describe('OpportunitiesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth.guest = false
    vi.mocked(getOpportunities).mockResolvedValue(page([opportunity]))
    vi.mocked(getOpportunity).mockResolvedValue(opportunity)
    vi.mocked(getOpportunityHistory).mockResolvedValue([])
    vi.mocked(getOpportunityMetrics).mockResolvedValue({ activeCount: 1, pipelineAmount: 100000, weightedAmount: 20000, stalledCount: 0 })
    vi.mocked(getCustomerOwners).mockResolvedValue([{ id: 'member-1', name: '销售甲' }])
    vi.mocked(getCustomers).mockResolvedValue({ content: [{ id: 'customer-1', name: '真实客户' }], totalElements: 1 } as Awaited<ReturnType<typeof getCustomers>>)
  })
  afterEach(cleanup)

  it('shows real converted opportunities and keeps demo data out', async () => {
    renderPage()
    expect(await screen.findByText('销售平台采购')).toBeInTheDocument()
    expect(screen.queryByText('集团销售数字化一期')).not.toBeInTheDocument()
    expect(screen.queryByText('演示数据')).not.toBeInTheDocument()
  })

  it('falls back to read-only demos only when the team has no opportunities', async () => {
    vi.mocked(getOpportunities).mockResolvedValue(page([]))
    const user = userEvent.setup(); renderPage()
    expect(await screen.findByText('集团销售数字化一期')).toBeInTheDocument()
    expect(screen.getByText('演示数据')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /恒川智造/ }))
    expect(screen.getByRole('button', { name: '变更阶段' })).toBeDisabled()
    expect(getOpportunity).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '新建商机' })).toBeEnabled()
  })

  it('does not substitute demos when a search returns no results', async () => {
    vi.mocked(getOpportunities).mockImplementation(async (filters) => page(filters?.query ? [] : [opportunity]))
    const user = userEvent.setup(); renderPage()
    await screen.findByText('销售平台采购')
    await user.type(screen.getByRole('textbox', { name: '搜索商机' }), '不存在')
    expect(await screen.findByText('没有符合条件的商机')).toBeInTheDocument()
    expect(screen.queryByText('演示数据')).not.toBeInTheDocument()
  })

  it('reports API errors without masking them as simulated data', async () => {
    vi.mocked(getOpportunities).mockRejectedValue(new Error('商机服务不可用'))
    renderPage()
    expect(await screen.findByText('商机服务不可用')).toBeInTheDocument()
    expect(screen.queryByText('集团销售数字化一期')).not.toBeInTheDocument()
  })

  it('creates an opportunity using real customer and owner references', async () => {
    vi.mocked(createOpportunity).mockResolvedValue(opportunity)
    const user = userEvent.setup(); renderPage()
    await user.click(screen.getByRole('button', { name: '新建商机' }))
    const dialog = screen.getByRole('dialog', { name: '新建商机' })
    await within(dialog).findByRole('option', { name: '真实客户' })
    await user.selectOptions(within(dialog).getByLabelText('关联客户'), 'customer-1')
    await user.selectOptions(within(dialog).getByLabelText('负责人'), 'member-1')
    await user.type(within(dialog).getByLabelText('商机名称'), '销售平台采购')
    await user.click(within(dialog).getByRole('button', { name: '保存商机' }))
    await waitFor(() => expect(createOpportunity).toHaveBeenCalledWith(expect.objectContaining({ customerId: 'customer-1', ownerMemberId: 'member-1', name: '销售平台采购' })))
    expect(await screen.findByRole('dialog', { name: '销售平台采购' })).toBeInTheDocument()
  })

  it('persists editing with a version and displays concurrency errors', async () => {
    vi.mocked(updateOpportunity).mockRejectedValue(new Error('资料已被其他成员更新，请刷新后重试'))
    const user = userEvent.setup(); renderPage()
    await user.click(await screen.findByRole('button', { name: /销售平台采购/ }))
    await user.click(screen.getByRole('button', { name: '编辑' }))
    const editor = screen.getByRole('dialog', { name: '编辑商机' })
    await user.clear(within(editor).getByLabelText('金额（元）'))
    await user.type(within(editor).getByLabelText('金额（元）'), '120000')
    await user.click(within(editor).getByRole('button', { name: '保存商机' }))
    await waitFor(() => expect(updateOpportunity).toHaveBeenCalledWith('opportunity-1', expect.objectContaining({ amount: 120000, version: 3 })))
    expect(await screen.findByText('资料已被其他成员更新，请刷新后重试')).toBeInTheDocument()
  })

  it('requires a lost reason and submits the versioned stage change', async () => {
    vi.mocked(changeOpportunityStage).mockResolvedValue({ ...opportunity, status: 'LOST', stage: 'LOST', probability: 0, version: 4 })
    const user = userEvent.setup(); renderPage()
    await user.click(await screen.findByRole('button', { name: /销售平台采购/ }))
    await user.click(screen.getByRole('button', { name: '变更阶段' }))
    const editor = screen.getByRole('dialog', { name: '变更销售阶段' })
    await user.selectOptions(within(editor).getByLabelText('目标阶段'), 'LOST')
    expect(within(editor).getByRole('button', { name: '确认变更' })).toBeDisabled()
    await user.type(within(editor).getByLabelText('输单原因'), '预算取消')
    await user.click(within(editor).getByRole('button', { name: '确认变更' }))
    await waitFor(() => expect(changeOpportunityStage).toHaveBeenCalledWith('opportunity-1', { stage: 'LOST', version: 3, reason: '预算取消' }))
  })

  it('lets guests browse demos without requesting private opportunities', async () => {
    auth.guest = true
    renderPage()
    expect(await screen.findByText('集团销售数字化一期')).toBeInTheDocument()
    expect(getOpportunities).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '新建商机' })).toBeDisabled()
  })
})
