import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CustomerInteractionsPanel } from './CustomerInteractionsPanel'
import { fetchRequestJson, fetchRequestMethod, fetchRequestUrl } from '../../test/fetch-request'

const customerId = '30000000-0000-0000-0000-000000000001'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('CustomerInteractionsPanel', () => {
  it('粘贴并导入微信聊天记录', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (fetchRequestUrl(input).endsWith('/interactions/analyses')) {
        return new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      if (fetchRequestMethod(input, init) === 'POST') {
        const request = await fetchRequestJson<{ content: string }>(input, init)
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

    const importCall = fetchMock.mock.calls.find(([input, init]) => (
      fetchRequestUrl(input).endsWith(`/api/v1/customers/${customerId}/interactions/chat-import`)
      && fetchRequestMethod(input, init) === 'POST'
    ))
    expect(importCall).toBeDefined()
    expect(await fetchRequestJson<{ content: string }>(importCall![0], importCall![1])).toMatchObject({
      content: expect.stringContaining('部署周期'),
    })
  })

  it('分析聊天并经销售确认后更新客户建议', async () => {
    const interactionId = '70000000-0000-0000-0000-000000000001'
    let analyses: Array<Record<string, unknown>> = []
    const draft = {
      id: '80000000-0000-0000-0000-000000000001',
      interactionId,
      version: 1,
      status: 'DRAFT',
      summary: '客户明确关注部署周期，购买意向较高。',
      intentScore: 82,
      intentLevel: 'HIGH',
      sentiment: 'POSITIVE',
      needs: ['明确部署周期'],
      painPoints: [],
      objections: [],
      risks: ['销售承诺尚未兑现'],
      recommendedActions: ['发送部署周期说明'],
      suggestedNextAction: '发送部署周期说明并确认收到',
      budgetSignal: null,
      timelineSignal: '当天下午',
      decisionMakerSignal: null,
      evidence: ['方案能否补充部署周期'],
      provider: 'QWEN',
      model: 'qwen-test',
      promptVersion: 'chat-analysis-v1',
      analyzedAt: '2026-08-26T02:00:00Z',
      appliedAt: null,
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = fetchRequestUrl(input)
      const method = fetchRequestMethod(input, init)
      if (url.endsWith('/interactions/analyses')) {
        return new Response(JSON.stringify(analyses), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.endsWith(`/${interactionId}/analysis`) && method === 'POST') {
        analyses = [draft]
        return new Response(JSON.stringify(draft), { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.endsWith(`/analysis/${draft.id}/apply`) && method === 'POST') {
        const applied = { ...draft, status: 'APPLIED', appliedAt: '2026-08-26T02:01:00Z' }
        analyses = [applied]
        return new Response(JSON.stringify(applied), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify({
        content: [{
          id: interactionId,
          customerId,
          type: 'CHAT_IMPORT',
          direction: 'NONE',
          occurredAt: '2026-08-26T01:30:00Z',
          subject: '微信聊天记录',
          bodyText: '客户：方案能否补充部署周期？',
          bodyPreview: '客户：方案能否补充部署周期？',
          participants: ['林婉清'],
          source: 'WECHAT',
          createdAt: '2026-08-26T01:30:00Z',
        }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <CustomerInteractionsPanel customerId={customerId} />
      </QueryClientProvider>,
    )

    await user.click(await screen.findByRole('button', { name: 'AI 分析' }))
    expect(await screen.findByText('客户明确关注部署周期，购买意向较高。')).toBeInTheDocument()
    expect(screen.getByText('82')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '确认并更新客户' }))
    expect(await screen.findByText('已更新客户评分与下一步动作')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([input, init]) => (
      fetchRequestUrl(input).endsWith(`/api/v1/customers/${customerId}/interactions/${interactionId}/analysis`)
      && fetchRequestMethod(input, init) === 'POST'
    ))).toBe(true)
  })
})
