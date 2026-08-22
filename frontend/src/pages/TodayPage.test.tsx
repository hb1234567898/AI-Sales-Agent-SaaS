import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TodayPage } from './TodayPage'

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
    expect(screen.getAllByText('云岚科技')).toHaveLength(2)
    expect(await screen.findByText('已连接')).toBeInTheDocument()
  })
})
