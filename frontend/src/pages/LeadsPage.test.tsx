import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { assignLead, convertLead, createLead, getLead, getLeadMetrics, getLeadOwners, getLeads, importLeads, updateLead } from '../api/leads-api'
import { LeadsPage } from './LeadsPage'

vi.mock('../auth/use-auth', () => ({ useIsGuest: () => false, useAuth: () => ({ memberId: 'member-1' }) }))
vi.mock('../api/leads-api', () => ({
  getLeads: vi.fn(), getLead: vi.fn(), getLeadMetrics: vi.fn(), getLeadOwners: vi.fn(), createLead: vi.fn(), updateLead: vi.fn(),
  assignLead: vi.fn(), convertLead: vi.fn(), importLeads: vi.fn(),
}))

const lead = {
  id: 'lead-1', company: '云岚科技', industry: '企业服务', contactName: '苏恬', contactEmail: 'su@example.com',
  contactPhone: '13800000000', contactTitle: '采购经理', source: 'MANUAL' as const, status: 'NEW' as const,
  ownerMemberId: null, ownerName: null, score: 82, nextAction: '首次联系', nextFollowUpAt: null,
  lastActivityAt: null, createdAt: '2026-09-22T02:00:00Z', updatedAt: '2026-09-22T02:00:00Z',
}

function renderPage() {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><LeadsPage /></QueryClientProvider>)
}

describe('LeadsPage', () => {
  beforeEach(() => {
    vi.mocked(getLeads).mockResolvedValue({ content: [lead], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true })
    vi.mocked(getLeadMetrics).mockResolvedValue({ pending: 1, qualified: 0, unassigned: 1, averageScore: 82 })
    vi.mocked(getLeadOwners).mockResolvedValue([{ id: 'member-1', name: '陈默' }])
    vi.mocked(getLead).mockResolvedValue(lead)
    vi.mocked(assignLead).mockReset(); vi.mocked(convertLead).mockReset(); vi.mocked(createLead).mockReset(); vi.mocked(updateLead).mockReset(); vi.mocked(importLeads).mockReset()
  })
  afterEach(cleanup)

  it('读取真实线索指标并可分配给当前销售', async () => {
    vi.mocked(assignLead).mockResolvedValue({ ...lead, ownerMemberId: 'member-1', ownerName: '陈默', status: 'NURTURING' })
    const user = userEvent.setup(); renderPage()
    expect(await screen.findByText('云岚科技')).toBeInTheDocument()
    expect(screen.getAllByText('82')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: '分配 云岚科技' }))
    await waitFor(() => expect(assignLead).toHaveBeenCalledWith('lead-1', 'member-1'))
  })

  it('打开新建线索抽屉并提交真实数据', async () => {
    vi.mocked(createLead).mockResolvedValue(lead)
    const user = userEvent.setup(); renderPage()
    await user.click(await screen.findByRole('button', { name: '新建线索' }))
    await user.type(screen.getByLabelText(/企业名称/), '云岚科技')
    await user.type(screen.getByLabelText(/姓名/), '苏恬')
    await user.click(screen.getByRole('button', { name: '创建线索' }))
    await waitFor(() => expect(createLead).toHaveBeenCalledWith(expect.objectContaining({ company: '云岚科技', contactName: '苏恬', status: 'NEW' })))
  })
})
