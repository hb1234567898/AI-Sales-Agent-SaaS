import { afterEach, describe, expect, it, vi } from 'vitest'
import { getAuthTokens, saveAuthTokens } from '../auth/auth-token-storage'
import { requestJson } from './http-client'

afterEach(() => {
  vi.restoreAllMocks()
  localStorage.clear()
  sessionStorage.clear()
})

describe('http client JWT refresh', () => {
  it('在 Access Token 失效后轮换双 Token 并重试原请求', async () => {
    saveAuthTokens({
      accessToken: 'expired-access.jwt',
      accessTokenExpiresAt: '2026-08-26T02:00:00Z',
      refreshToken: 'old-refresh.jwt',
      refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
    }, false)
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const authorization = new Headers(init?.headers).get('Authorization')
      if (String(input) === '/api/v1/ai/model' && authorization === 'Bearer expired-access.jwt') {
        return problemResponse(401)
      }
      if (String(input) === '/api/v1/auth/refresh') {
        expect(init?.credentials).toBe('omit')
        expect(JSON.parse(String(init?.body))).toEqual({ refreshToken: 'old-refresh.jwt' })
        return jsonResponse({
          tokenType: 'Bearer',
          accessToken: 'new-access.jwt',
          accessTokenExpiresAt: '2026-08-26T02:15:00Z',
          refreshToken: 'new-refresh.jwt',
          refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
          session: {},
        })
      }
      if (String(input) === '/api/v1/ai/model' && authorization === 'Bearer new-access.jwt') {
        expect(init?.credentials).toBe('omit')
        return jsonResponse({ configured: true })
      }
      return problemResponse(500)
    })

    const result = await requestJson<{ configured: boolean }>('/api/v1/ai/model', {
      method: 'PUT',
      body: JSON.stringify({ provider: 'QWEN' }),
    })

    expect(result).toEqual({ configured: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(getAuthTokens()).toEqual({
      accessToken: 'new-access.jwt',
      accessTokenExpiresAt: '2026-08-26T02:15:00Z',
      refreshToken: 'new-refresh.jwt',
      refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
    })
  })

  it('Refresh Token 失效时清理本地登录状态', async () => {
    saveAuthTokens({
      accessToken: 'expired-access.jwt',
      accessTokenExpiresAt: '2026-08-26T02:00:00Z',
      refreshToken: 'expired-refresh.jwt',
      refreshTokenExpiresAt: '2026-08-26T02:00:00Z',
    }, true)
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => (
      String(input) === '/api/v1/auth/refresh' ? problemResponse(401) : problemResponse(401)
    ))

    await expect(requestJson('/api/v1/ai/model', { method: 'PUT' })).rejects.toMatchObject({ status: 401 })

    expect(getAuthTokens()).toBeNull()
  })
})

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problemResponse(status: number) {
  return new Response(JSON.stringify({ detail: '登录状态已失效' }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}
