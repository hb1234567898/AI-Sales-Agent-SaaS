import { clearAuthTokens, getAuthTokens, replaceAuthTokens, type AuthTokens } from '../auth/auth-token-storage'

export class ApiError extends Error {
  readonly status: number

  constructor(
    message: string,
    status: number,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

interface ProblemResponse {
  detail?: string
  title?: string
}

const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS'])
const unauthenticatedPaths = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh'])
let refreshPromise: Promise<boolean> | null = null

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  return executeRequest<T>(path, init, true)
}

async function executeRequest<T>(path: string, init: RequestInit, allowRefresh: boolean): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const method = (init.method ?? 'GET').toUpperCase()
  const isAuthenticationRequest = unauthenticatedPaths.has(path) || path === '/api/v1/auth/logout'
  if (!safeMethods.has(method) && !isAuthenticationRequest && sessionStorage.getItem('sales-agent:guest-mode') === 'true') {
    throw new ApiError('游客模式只能浏览，登录后才能执行修改操作', 403)
  }
  const tokens = getAuthTokens()
  if (!unauthenticatedPaths.has(path) && tokens?.accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${tokens.accessToken}`)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'omit',
  })

  if (!response.ok) {
    if (response.status === 401 && allowRefresh && !unauthenticatedPaths.has(path) && tokens?.refreshToken) {
      const refreshed = await refreshAccessToken()
      if (refreshed) return executeRequest<T>(path, init, false)
    }
    const problem = await response.json().catch(() => null) as ProblemResponse | null
    if (response.status === 401 && path !== '/api/v1/auth/login' && path !== '/api/v1/auth/session') {
      clearAuthTokens()
      window.dispatchEvent(new CustomEvent('sales-agent:unauthorized'))
    }
    throw new ApiError(problem?.detail ?? problem?.title ?? `请求失败，状态码 ${response.status}`, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

async function refreshAccessToken() {
  refreshPromise ??= performRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function performRefresh() {
  const tokens = getAuthTokens()
  if (!tokens?.refreshToken) return false
  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    credentials: 'omit',
  })
  if (!response.ok) {
    clearAuthTokens()
    return false
  }
  const refreshed = await response.json() as AuthTokens
  replaceAuthTokens(refreshed)
  return true
}

export function getJson<T>(path: string): Promise<T> {
  return requestJson<T>(path)
}
