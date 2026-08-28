import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getAuthSession, type AuthSession } from '../api/auth-api'
import { ApiError } from '../api/axios-client'
import { clearAuthTokens, saveAuthTokens } from './auth-token-storage'
import { ProtectedApp } from './ProtectedApp'

vi.mock('../api/auth-api', () => ({
  getAuthSession: vi.fn(),
}))

vi.mock('../components/layout/AppShell', () => ({
  AppShell: () => <Outlet />,
}))

const session: AuthSession = {
  userId: 'user-1',
  memberId: 'member-1',
  organizationId: 'org-1',
  email: 'chenmo@example.com',
  displayName: '陈默',
  organizationName: '演示销售团队',
  role: 'MEMBER',
  expiresAt: '2099-08-27T10:00:00Z',
}

function renderProtectedApp() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/app/customers']}>
        <Routes>
          <Route path="/app" element={<ProtectedApp />}>
            <Route path="customers" element={<h1>客户页面</h1>} />
          </Route>
          <Route path="/login" element={<h1>登录页面</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ProtectedApp', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.mocked(getAuthSession).mockReset()
  })

  afterEach(() => {
    cleanup()
  })

  it('业务接口误返回 401 时先验证会话并保留当前页面', async () => {
    saveAuthTokens({
      accessToken: 'access.jwt',
      accessTokenExpiresAt: '2099-08-27T10:00:00Z',
      refreshToken: 'refresh.jwt',
      refreshTokenExpiresAt: '2099-08-27T10:00:00Z',
    }, false)
    vi.mocked(getAuthSession).mockResolvedValue(session)
    renderProtectedApp()

    expect(await screen.findByRole('heading', { name: '客户页面' })).toBeInTheDocument()
    window.dispatchEvent(new CustomEvent('sales-agent:unauthorized'))

    await waitFor(() => expect(getAuthSession).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('heading', { name: '客户页面' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '登录页面' })).not.toBeInTheDocument()
  })

  it('会话接口确认失效且本地 Token 已清理后才跳转登录页', async () => {
    saveAuthTokens({
      accessToken: 'access.jwt',
      accessTokenExpiresAt: '2099-08-27T10:00:00Z',
      refreshToken: 'refresh.jwt',
      refreshTokenExpiresAt: '2099-08-27T10:00:00Z',
    }, false)
    let requestCount = 0
    vi.mocked(getAuthSession).mockImplementation(() => {
      requestCount += 1
      return requestCount === 1
        ? Promise.resolve(session)
        : Promise.reject(new ApiError('登录状态已失效，请重新登录', 401))
    })
    renderProtectedApp()

    expect(await screen.findByRole('heading', { name: '客户页面' })).toBeInTheDocument()
    clearAuthTokens()
    window.dispatchEvent(new CustomEvent('sales-agent:unauthorized'))

    expect(await screen.findByRole('heading', { name: '登录页面' })).toBeInTheDocument()
  })

  it('会话接口短暂返回 401 但本地 Token 仍存在时不跳转登录页', async () => {
    saveAuthTokens({
      accessToken: 'access.jwt',
      accessTokenExpiresAt: '2099-08-27T10:00:00Z',
      refreshToken: 'refresh.jwt',
      refreshTokenExpiresAt: '2099-08-27T10:00:00Z',
    }, false)
    let requestCount = 0
    vi.mocked(getAuthSession).mockImplementation(() => {
      requestCount += 1
      return requestCount === 1
        ? Promise.resolve(session)
        : Promise.reject(new ApiError('登录状态已失效，请重新登录', 401))
    })
    renderProtectedApp()

    expect(await screen.findByRole('heading', { name: '客户页面' })).toBeInTheDocument()
    window.dispatchEvent(new CustomEvent('sales-agent:unauthorized'))

    await waitFor(() => expect(getAuthSession).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('heading', { name: '客户页面' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '登录页面' })).not.toBeInTheDocument()
  })
})
