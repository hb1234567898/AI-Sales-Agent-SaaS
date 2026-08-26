import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SettingsPage } from './SettingsPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SettingsPage', () => {
  it('展示千问真实配置状态并测试连接', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.endsWith('/api/v1/ai/model/test') && init?.method === 'POST') {
        return new Response(JSON.stringify({ provider: 'QWEN', model: 'qwen-plus', status: 'CONNECTED', responsePreview: '连接成功', latencyMs: 328 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.endsWith('/api/v1/ai/model')) {
        return new Response(JSON.stringify({ provider: 'QWEN', model: 'qwen-plus', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyConfigured: true, ready: true, status: 'READY' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify({ service: 'sales-agent', status: 'UP', timestamp: '2026-08-25T08:00:00Z' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <SettingsPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('已就绪')).toBeInTheDocument()
    expect(screen.getByDisplayValue('qwen-plus')).toBeInTheDocument()
    expect(screen.getByDisplayValue('********')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '测试连接' }))

    expect(await screen.findByText(/连接成功 · 328 ms/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/ai/model/test', expect.objectContaining({ method: 'POST' }))
  })
})
