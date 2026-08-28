import { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getAuthTokens, saveAuthTokens } from '../auth/auth-token-storage'
import { apiClient, authRefreshClient, requestJson } from './axios-client'

const originalApiAdapter = apiClient.defaults.adapter
const originalRefreshAdapter = authRefreshClient.defaults.adapter

afterEach(() => {
  apiClient.defaults.adapter = originalApiAdapter
  authRefreshClient.defaults.adapter = originalRefreshAdapter
  vi.restoreAllMocks()
  localStorage.clear()
  sessionStorage.clear()
})

describe('Axios HTTP client JWT refresh', () => {
  it('在 Access Token 失效后轮换双 Token 并重试原请求', async () => {
    saveExpiredTokens(false)
    let apiRequests = 0
    apiClient.defaults.adapter = async (config) => {
      apiRequests += 1
      const authorization = header(config, 'Authorization')
      expect(header(config, 'X-Sales-Agent-Access-Token')).toBe(
        authorization === 'Bearer expired-access.jwt' ? 'expired-access.jwt' : 'new-access.jwt',
      )
      return authorization === 'Bearer expired-access.jwt'
        ? reject(config, 401, { detail: '登录状态已失效' })
        : resolve(config, 200, { configured: true })
    }
    authRefreshClient.defaults.adapter = async (config) => {
      expect(config.withCredentials).toBe(false)
      expect(JSON.parse(String(config.data))).toEqual({ refreshToken: 'old-refresh.jwt' })
      return resolve(config, 200, refreshedTokens())
    }

    const result = await requestJson<{ configured: boolean }>('/api/v1/ai/model', {
      method: 'PUT',
      data: { provider: 'QWEN' },
    })

    expect(result).toEqual({ configured: true })
    expect(apiRequests).toBe(2)
    expect(getAuthTokens()).toEqual({
      accessToken: 'new-access.jwt',
      accessTokenExpiresAt: '2026-08-26T02:15:00Z',
      refreshToken: 'new-refresh.jwt',
      refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
    })
  })

  it('并发 401 只发起一次 Refresh Token 请求', async () => {
    saveExpiredTokens(false)
    let refreshRequests = 0
    apiClient.defaults.adapter = async (config) => (
      header(config, 'Authorization') === 'Bearer expired-access.jwt'
        ? reject(config, 401, { detail: '登录状态已失效' })
        : resolve(config, 200, { ok: true })
    )
    authRefreshClient.defaults.adapter = async (config) => {
      refreshRequests += 1
      await Promise.resolve()
      return resolve(config, 200, refreshedTokens())
    }

    const results = await Promise.all([
      requestJson<{ ok: boolean }>('/api/v1/customers'),
      requestJson<{ ok: boolean }>('/api/v1/customers/metrics'),
    ])

    expect(results).toEqual([{ ok: true }, { ok: true }])
    expect(refreshRequests).toBe(1)
  })

  it('Refresh Token 失效时清理本地登录状态', async () => {
    saveExpiredTokens(true)
    apiClient.defaults.adapter = (config) => reject(config, 401, { detail: '登录状态已失效' })
    authRefreshClient.defaults.adapter = (config) => reject(config, 401, { detail: '刷新令牌已失效' })

    await expect(requestJson('/api/v1/ai/model', { method: 'PUT' })).rejects.toMatchObject({ status: 401 })

    expect(getAuthTokens()).toBeNull()
  })

  it('刷新成功后业务接口仍返回 401 时保留登录状态', async () => {
    saveExpiredTokens(true)
    const unauthorizedListener = vi.fn()
    window.addEventListener('sales-agent:unauthorized', unauthorizedListener)
    apiClient.defaults.adapter = (config) => reject(config, 401, { detail: '无权执行该业务操作' })
    authRefreshClient.defaults.adapter = (config) => resolve(config, 200, refreshedTokens())

    await expect(requestJson('/api/v1/ai/model', { method: 'PUT' })).rejects.toMatchObject({ status: 401 })

    expect(getAuthTokens()?.accessToken).toBe('new-access.jwt')
    expect(unauthorizedListener).not.toHaveBeenCalled()
    window.removeEventListener('sales-agent:unauthorized', unauthorizedListener)
  })

  it('Session 探测请求在刷新后仍返回 401 时不主动清理 Token', async () => {
    saveExpiredTokens(false)
    apiClient.defaults.adapter = (config) => reject(config, 401, { detail: '会话不可用' })
    authRefreshClient.defaults.adapter = (config) => resolve(config, 200, refreshedTokens())

    await expect(requestJson('/api/v1/auth/session')).rejects.toMatchObject({ status: 401 })

    expect(getAuthTokens()?.accessToken).toBe('new-access.jwt')
  })

  it('刷新服务暂时异常时不删除本地 Token', async () => {
    saveExpiredTokens(false)
    apiClient.defaults.adapter = (config) => reject(config, 401, { detail: '登录状态已失效' })
    authRefreshClient.defaults.adapter = (config) => reject(config, 503, { detail: '刷新服务暂时不可用' })

    await expect(requestJson('/api/v1/ai/model', { method: 'PUT' })).rejects.toMatchObject({
      status: 503,
      message: '刷新服务暂时不可用',
    })

    expect(getAuthTokens()?.refreshToken).toBe('old-refresh.jwt')
  })

  it('游客模式在请求发送前阻止写操作', async () => {
    sessionStorage.setItem('sales-agent:guest-mode', 'true')
    const adapter = vi.fn((config: InternalAxiosRequestConfig) => resolve(config, 200, {}))
    apiClient.defaults.adapter = adapter

    await expect(requestJson('/api/v1/customers', { method: 'POST', data: {} })).rejects.toMatchObject({
      status: 403,
    })

    expect(adapter).not.toHaveBeenCalled()
  })
})

function saveExpiredTokens(remember: boolean) {
  saveAuthTokens({
    accessToken: 'expired-access.jwt',
    accessTokenExpiresAt: '2026-08-26T02:00:00Z',
    refreshToken: 'old-refresh.jwt',
    refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
  }, remember)
}

function refreshedTokens() {
  return {
    tokenType: 'Bearer',
    accessToken: 'new-access.jwt',
    accessTokenExpiresAt: '2026-08-26T02:15:00Z',
    refreshToken: 'new-refresh.jwt',
    refreshTokenExpiresAt: '2026-09-25T02:00:00Z',
    session: {},
  }
}

function header(config: InternalAxiosRequestConfig, name: string) {
  return AxiosHeaders.from(config.headers).get(name)
}

function resolve(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  return Promise.resolve({
    config,
    data,
    headers: new AxiosHeaders(),
    status,
    statusText: status === 200 ? 'OK' : 'Error',
  })
}

function reject(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<never> {
  const response: AxiosResponse = {
    config,
    data,
    headers: new AxiosHeaders(),
    status,
    statusText: 'Error',
  }
  return Promise.reject(new AxiosError(
    `Request failed with status code ${status}`,
    AxiosError.ERR_BAD_REQUEST,
    config,
    undefined,
    response,
  ))
}
