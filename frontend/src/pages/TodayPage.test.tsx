import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TodayPage } from './TodayPage'

vi.mock('../api/agent-runs-api', () => ({ getAgentRuns: async () => ({ content: [], totalElements: 0 }) }))
vi.mock('../api/approvals-api', () => ({ getPendingApprovals: async () => ({ content: [], totalElements: 7 }) }))
vi.mock('../api/ai-settings-api', () => ({ getAiModelStatus: async () => ({ ready: true }) }))
vi.mock('../auth/use-auth', () => ({ useAuth: () => ({ role: 'GUEST', memberId: 'guest' }) }))
vi.mock('../api/follow-ups-api', () => ({
  getFollowUps: async () => ({ content: [], totalElements: 0 }),
  getFollowUpMetrics: async () => ({ pending: 0, today: 0, overdue: 0, completed: 0 }),
  completeFollowUp: vi.fn(),
}))

afterEach(async () => {
  cleanup()
  await new Promise<void>((resolve) => queueMicrotask(resolve))
  vi.restoreAllMocks()
})

describe('TodayPage', () => {
  it('展示演示数据并检查后端连接', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          service: 'sales-agent',
          status: 'UP',
          timestamp: '2026-08-22T00:00:00Z',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <TodayPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { name: '今日工作台' })).toBeInTheDocument()
    expect(screen.getByText('演示数据')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '我的跟进待办' })).toBeInTheDocument()
    expect(await screen.findByText('暂无待办')).toBeInTheDocument()
    expect(await screen.findByText('已连接')).toBeInTheDocument()
    expect(await screen.findByText('已配置')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /待人工审批 7/ })).toHaveAttribute('href', '/app/approvals')
    expect(screen.getByText('暂无运行记录，可前往 MCP 助手开始分析。')).toBeInTheDocument()
    expect(screen.queryByText('RUN-0241')).not.toBeInTheDocument()
  })
})
