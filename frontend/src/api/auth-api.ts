import { getJson, requestJson } from './http-client'

export interface AuthSession {
  userId: string
  memberId: string
  organizationId: string
  email: string
  displayName: string
  organizationName: string
  role: string
  expiresAt: string
}

interface CsrfTokenResponse {
  headerName: string
  token: string
}

export interface LoginInput {
  email: string
  password: string
  rememberMe: boolean
}

let csrfToken: CsrfTokenResponse | null = null

export async function ensureCsrfToken() {
  csrfToken ??= await getJson<CsrfTokenResponse>('/api/v1/auth/csrf')
  return csrfToken
}

export async function getAuthSession() {
  await ensureCsrfToken()
  return getJson<AuthSession>('/api/v1/auth/session')
}

export async function login(input: LoginInput) {
  const csrf = await ensureCsrfToken()
  return requestJson<AuthSession>('/api/v1/auth/login', {
    method: 'POST',
    headers: { [csrf.headerName]: csrf.token },
    body: JSON.stringify(input),
  })
}

export async function logout() {
  const csrf = await ensureCsrfToken()
  await requestJson<void>('/api/v1/auth/logout', {
    method: 'POST',
    headers: { [csrf.headerName]: csrf.token },
  })
}
