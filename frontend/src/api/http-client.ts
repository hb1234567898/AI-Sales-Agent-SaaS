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

function readCookie(name: string) {
  const prefix = `${name}=`
  const value = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix))
  return value ? decodeURIComponent(value.slice(prefix.length)) : null
}

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const method = (init.method ?? 'GET').toUpperCase()
  const isAuthenticationRequest = path === '/api/v1/auth/login' || path === '/api/v1/auth/logout'
  if (!safeMethods.has(method) && !isAuthenticationRequest && sessionStorage.getItem('sales-agent:guest-mode') === 'true') {
    throw new ApiError('游客模式只能浏览，登录后才能执行修改操作', 403)
  }
  const csrfToken = readCookie('XSRF-TOKEN')
  if (!safeMethods.has(method) && csrfToken && !headers.has('X-XSRF-TOKEN')) {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null) as ProblemResponse | null
    if (response.status === 401 && path !== '/api/v1/auth/login' && path !== '/api/v1/auth/session') {
      window.dispatchEvent(new CustomEvent('sales-agent:unauthorized'))
    }
    throw new ApiError(problem?.detail ?? problem?.title ?? `请求失败，状态码 ${response.status}`, response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function getJson<T>(path: string): Promise<T> {
  return requestJson<T>(path)
}
