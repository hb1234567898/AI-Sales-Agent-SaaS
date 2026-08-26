import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CustomerInteractionsPanel } from './CustomerInteractionsPanel'

const customerId = '30000000-0000-0000-0000-000000000001'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('CustomerInteractionsPanel', () => {
  it('粘贴并导入微信聊天记录', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'POST') {
        const request = JSON.parse(String(init.body)) as { content: string }
        return new Response(JSON.stringify({
          id: '70000000-0000-0000-0000-000000000001',
          customerId,
          type: 'CHAT_IMPORT',
          direction: 'NONE',
          occurredAt: '2026-08-25T08:00:00Z',
          subject: '微信聊天记录',
          bodyText: request.content,
          bodyPreview: request.content,
          participants: ['林婉清'],
          source: 'WECHAT',
          createdAt: '2026-08-25T08:00:00Z',
        }), { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, first: true, last: true }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <CustomerInteractionsPanel customerId={customerId} />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('还没有互动记录')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '导入聊天' }))
    await user.type(screen.getByLabelText(/客户联系人/), '林婉清')
    await user.type(screen.getByLabelText(/聊天原文/), '客户：能否补充部署周期？')
    await user.click(screen.getByRole('button', { name: '确认导入' }))

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/customers/${customerId}/interactions/chat-import`,
      expect.objectContaining({ method: 'POST', body: expect.stringContaining('部署周期') }),
    )
  })
})
