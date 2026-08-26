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

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null) as ProblemResponse | null
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
