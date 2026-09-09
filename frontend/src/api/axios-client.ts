import axios, {
  AxiosHeaders,
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { clearAuthTokens, getAuthTokens, replaceAuthTokens, type AuthTokens } from '../auth/auth-token-storage'

export type JsonRequestConfig = Omit<AxiosRequestConfig, 'baseURL' | 'url'>

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

interface ProblemResponse {
  detail?: string
  title?: string
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  salesAgentRetried?: boolean
}

const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS'])
const unauthenticatedPaths = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh'])
const authenticationWritePaths = new Set([...unauthenticatedPaths, '/api/v1/auth/logout'])
const apiBaseUrlStorageKey = 'sales-agent:api-base-url'
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const sameOriginBaseUrl = typeof window === 'undefined' ? 'http://localhost' : window.location.origin
const apiBaseUrl = resolveApiBaseUrl()
const fetchAdapter = axios.getAdapter('fetch')
let refreshPromise: Promise<RefreshResult> | null = null

type RefreshResult = 'refreshed' | 'invalid'

/**
 * 业务请求使用的 Axios 实例。所有 API 都经过统一超时、Token 注入和错误转换。
 * 导出实例是为了让文件上传、取消请求等特殊场景仍能复用相同基础配置。
 */
export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  adapter: fetchAdapter,
  timeout: 60_000,
  withCredentials: false,
  headers: { Accept: 'application/json' },
})

// 刷新 Token 使用无业务拦截器的独立实例，避免刷新接口 401 时递归调用自身。
export const authRefreshClient = axios.create({
  baseURL: apiBaseUrl,
  adapter: fetchAdapter,
  timeout: 15_000,
  withCredentials: false,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use(async (config) => {
  const path = requestPath(config.url)
  const method = (config.method ?? 'GET').toUpperCase()
  if (
    !safeMethods.has(method)
    && !authenticationWritePaths.has(path)
    && sessionStorage.getItem('sales-agent:guest-mode') === 'true'
  ) {
    throw new ApiError('游客模式只能浏览，登录后才能执行修改操作', 403)
  }

  let tokens = getAuthTokens()
  // 主动刷新临近过期的 access token，避免流式等耗时请求在传输中途撞上 401，
  // 而 stream 模式下的 401→refresh→retry 链路不稳定，容易直接失败。
  if (!unauthenticatedPaths.has(path) && tokens?.accessToken && isAccessTokenExpiring(tokens)) {
    try {
      if ((await refreshAccessToken()) === 'refreshed') {
        tokens = getAuthTokens()
      }
    }
    catch {
      // 刷新失败则沿用原 token，交给 401 响应拦截器兜底。
    }
  }
  if (!unauthenticatedPaths.has(path) && tokens?.accessToken) {
    const headers = AxiosHeaders.from(config.headers)
    if (!headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${tokens.accessToken}`)
      headers.set('X-Sales-Agent-Access-Token', tokens.accessToken)
    }
    config.headers = headers
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(toApiError(error))

    const config = error.config as RetriableRequestConfig | undefined
    const path = requestPath(config?.url)
    const tokens = getAuthTokens()
    if (
      error.response?.status === 401
      && config
      && !config.salesAgentRetried
      && !unauthenticatedPaths.has(path)
      && tokens?.refreshToken
    ) {
      config.salesAgentRetried = true
      try {
        const result = await refreshAccessToken()
        if (result === 'refreshed') {
          const refreshedTokens = getAuthTokens()
          const headers = AxiosHeaders.from(config.headers)
          if (refreshedTokens?.accessToken) {
            headers.set('Authorization', `Bearer ${refreshedTokens.accessToken}`)
            headers.set('X-Sales-Agent-Access-Token', refreshedTokens.accessToken)
          }
          config.headers = headers
          return apiClient.request(config)
        }
        window.dispatchEvent(new CustomEvent('sales-agent:unauthorized'))
      }
      catch (refreshError) {
        return Promise.reject(toApiError(refreshError))
      }
    }
    return Promise.reject(toApiError(error))
  },
)

export async function requestJson<T>(path: string, config: JsonRequestConfig = {}): Promise<T> {
  try {
    const requestConfig: AxiosRequestConfig = { ...config, url: path }
    const response = await apiClient.request<T>(requestConfig)
    return response.status === 204 ? undefined as T : response.data
  }
  catch (error) {
    throw toApiError(error)
  }
}

export function getJson<T>(path: string, config: JsonRequestConfig = {}): Promise<T> {
  return requestJson<T>(path, { ...config, method: 'GET' })
}

export function getApiBaseUrlSetting() {
  return localStorage.getItem(apiBaseUrlStorageKey) ?? configuredApiBaseUrl ?? ''
}

export function saveApiBaseUrlSetting(value: string) {
  const normalized = normalizeApiBaseUrl(value)
  if (normalized) {
    localStorage.setItem(apiBaseUrlStorageKey, normalized)
  } else {
    localStorage.removeItem(apiBaseUrlStorageKey)
  }
  apiClient.defaults.baseURL = normalized || configuredApiBaseUrl || sameOriginBaseUrl
  authRefreshClient.defaults.baseURL = normalized || configuredApiBaseUrl || sameOriginBaseUrl
  return normalized
}

async function refreshAccessToken() {
  refreshPromise ??= performRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function performRefresh(): Promise<RefreshResult> {
  const tokens = getAuthTokens()
  if (!tokens?.refreshToken) return 'invalid'

  try {
    const response = await authRefreshClient.post<AuthTokens>('/api/v1/auth/refresh', {
      refreshToken: tokens.refreshToken,
    })
    replaceAuthTokens(response.data)
    return 'refreshed'
  }
  catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearAuthTokens()
      return 'invalid'
    }
    throw toApiError(error, '刷新登录状态失败')
  }
}

function requestPath(url?: string) {
  if (!url) return ''
  try {
    return new URL(url, window.location.origin).pathname
  }
  catch {
    return url.split('?')[0] ?? url
  }
}

function resolveApiBaseUrl() {
  return localStorage.getItem(apiBaseUrlStorageKey) ?? configuredApiBaseUrl ?? sameOriginBaseUrl
}

function normalizeApiBaseUrl(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return ''
  const withProtocol = /^https?:\/\//i.test(trimmed) ? trimmed : `${defaultProtocol(trimmed)}://${trimmed}`
  return withProtocol.replace(/\/+$/, '')
}

function defaultProtocol(host: string) {
  return /^(localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1\])(?::\d+)?$/i.test(host) ? 'http' : 'https'
}

function isAccessTokenExpiring(tokens: AuthTokens, skewSeconds = 60): boolean {
  const expiresAt = new Date(tokens.accessTokenExpiresAt).getTime()
  if (Number.isNaN(expiresAt)) return false
  return expiresAt - Date.now() <= skewSeconds * 1000
}

function toApiError(error: unknown, fallback = '请求失败') {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError<ProblemResponse>(error)) {
    const status = error.response?.status ?? 0
    if (status === 401) {
      const raw = error.response?.headers as Record<string, string> | AxiosHeaders | undefined
      const getHeader = (name: string): string | undefined => {
        const value = raw && typeof (raw as AxiosHeaders).get === 'function'
          ? (raw as AxiosHeaders).get(name)
          : (raw as Record<string, string> | undefined)?.[name]
        return value == null ? undefined : String(value)
      }
      const tokenStatus = getHeader('x-sales-agent-auth-token')
      const authHeaderPresent = getHeader('x-sales-agent-auth-authorization')
      if (tokenStatus || authHeaderPresent) {
        const reason = tokenStatus === 'missing'
          ? '请求未携带认证令牌'
          : tokenStatus === 'invalid'
            ? '认证令牌无效或已过期'
            : tokenStatus === 'accepted'
              ? '令牌有效但权限不足'
              : '登录状态已失效'
        const headerNote = authHeaderPresent === 'true' ? 'Authorization 头已发送' : 'Authorization 头未发送'
        return new ApiError(`认证失败：${reason}（${headerNote}）`, 401)
      }
    }
    const problem = error.response?.data
    const message = problem?.detail
      ?? problem?.title
      ?? (error.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试'
        : status ? `请求失败，状态码 ${status}` : `${fallback}，请检查网络连接`)
    return new ApiError(message, status)
  }
  return new ApiError(error instanceof Error ? error.message : fallback, 0)
}

export type { AxiosError, AxiosRequestConfig }
