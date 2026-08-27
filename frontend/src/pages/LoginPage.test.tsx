import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
  sessionStorage.clear()
})

describe('LoginPage', () => {
  it('可以不登录并以只读游客身份进入工作台', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/app/today" element={<h1>游客工作台</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole('button', { name: '以游客身份浏览' }))

    expect(await screen.findByRole('heading', { name: '游客工作台' })).toBeInTheDocument()
    expect(sessionStorage.getItem('sales-agent:guest-mode')).toBe('true')
  })

  it('登录成功后进入原本请求的工作台页面', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (String(input).endsWith('/auth/login') && init?.method === 'POST') {
        return new Response(JSON.stringify({
          tokenType: 'Bearer',
          accessToken: 'access.jwt',
          accessTokenExpiresAt: '2026-08-26T02:15:00Z',
          refreshToken: 'refresh.jwt',
          refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
          session: {
            userId: '10000000-0000-0000-0000-000000000001',
            memberId: '20000000-0000-0000-0000-000000000001',
            organizationId: '00000000-0000-0000-0000-000000000001',
            email: 'chen.mo@demo.local',
            displayName: '陈默',
            organizationName: '演示销售团队',
            role: 'SALES',
            expiresAt: '2026-09-25T02:00:00Z',
          },
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(null, { status: 404 })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/app/customers' } }]}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/app/customers" element={<h1>客户页面</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    await user.type(screen.getByLabelText('邮箱'), 'chen.mo@demo.local')
    await user.type(screen.getByLabelText('密码'), 'Demo@123456')
    await user.click(screen.getByRole('button', { name: '登录' }))

    expect(await screen.findByRole('heading', { name: '客户页面' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({
      method: 'POST',
      headers: expect.any(Headers),
      credentials: 'omit',
    }))
    expect(JSON.parse(localStorage.getItem('sales-agent:auth-tokens') ?? '{}')).toEqual({
      accessToken: 'access.jwt',
      accessTokenExpiresAt: '2026-08-26T02:15:00Z',
      refreshToken: 'refresh.jwt',
      refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
    })
  })
})
