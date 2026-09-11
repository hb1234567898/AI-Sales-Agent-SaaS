import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CustomersPage } from './CustomersPage'
import { fetchRequestUrl } from '../test/fetch-request'

const customer = {
  id: '30000000-0000-0000-0000-000000000001',
  name: '云岚科技',
  website: 'https://yunlan.example',
  industry: '企业服务',
  employeeRange: '100-499 人',
  stage: 'PROPOSAL',
  status: 'ACTIVE',
  source: 'MANUAL',
  ownerMemberId: '20000000-0000-0000-0000-000000000001',
  ownerName: '陈默',
  score: 92,
  estimatedValue: 486000,
  nextAction: '发送方案确认邮件',
  lastInteractionAt: '2026-08-25T06:00:00Z',
  nextFollowUpAt: '2026-08-26T06:00:00Z',
  primaryContact: { name: '林婉清', email: 'lin@example.com', phone: '13800000001' },
  createdAt: '2026-08-20T06:00:00Z',
  updatedAt: '2026-08-25T06:00:00Z',
  version: 0,
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('CustomersPage', () => {
  it('loads real customer APIs and opens the create drawer', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = fetchRequestUrl(input)
      const payload = url.includes('/metrics')
        ? { total: 1, highIntent: 1, activeOpportunities: 1, averageScore: 92 }
        : url.includes('/owners')
          ? [{ id: customer.ownerMemberId, name: customer.ownerName }]
          : { content: [customer], page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true }
      return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <CustomersPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('共 1 条真实记录')).toBeInTheDocument()
    expect(screen.getByText('云岚科技')).toBeInTheDocument()
    expect(screen.getByText('发送方案确认邮件')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([input]) => fetchRequestUrl(input).includes('/api/v1/customers?'))).toBe(true)

    await user.click(screen.getByRole('button', { name: '添加客户' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '添加客户' })).toBeInTheDocument()
  })
})
