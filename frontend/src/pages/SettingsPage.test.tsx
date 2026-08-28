import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SettingsPage } from './SettingsPage'
import { fetchRequestJson, fetchRequestMethod, fetchRequestUrl } from '../test/fetch-request'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SettingsPage', () => {
  it('手动保存加密模型配置后可以测试连接', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = fetchRequestUrl(input)
      const method = fetchRequestMethod(input, init)
      if (url.endsWith('/api/v1/ai/model') && method === 'PUT') {
        return new Response(JSON.stringify({ provider: 'QWEN', model: 'qwen3.7-plus', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyConfigured: true, ready: true, status: 'READY' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.endsWith('/api/v1/ai/model/test') && method === 'POST') {
        return new Response(JSON.stringify({ provider: 'QWEN', model: 'qwen3.7-plus', status: 'CONNECTED', responsePreview: '连接成功', latencyMs: 328 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.endsWith('/api/v1/ai/model')) {
        return new Response(JSON.stringify({ provider: 'QWEN', model: 'qwen-plus', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKeyConfigured: false, ready: false, status: 'MISSING_API_KEY' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
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

    expect(await screen.findByText('待配置')).toBeInTheDocument()
    expect(screen.getByDisplayValue('qwen-plus')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('模型名称'))
    await user.type(screen.getByLabelText('模型名称'), 'qwen3.7-plus')
    await user.type(screen.getByLabelText('API Key'), 'sk-manual-test')
    await user.click(screen.getByRole('button', { name: '保存配置' }))

    expect(await screen.findByText('配置已加密保存。')).toBeInTheDocument()
    expect(screen.getByLabelText('API Key')).toHaveValue('')
    const saveCall = fetchMock.mock.calls.find(([input, init]) => (
      fetchRequestUrl(input).endsWith('/api/v1/ai/model')
      && fetchRequestMethod(input, init) === 'PUT'
    ))
    expect(saveCall).toBeDefined()
    expect(await fetchRequestJson(saveCall![0], saveCall![1])).toEqual({
      provider: 'QWEN',
      model: 'qwen3.7-plus',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      apiKey: 'sk-manual-test',
    })

    await user.click(screen.getByRole('button', { name: '测试连接' }))

    expect(await screen.findByText(/连接成功 · 328 ms/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([input, init]) => (
      fetchRequestUrl(input).endsWith('/api/v1/ai/model/test')
      && fetchRequestMethod(input, init) === 'POST'
    ))).toBe(true)
  })
})
