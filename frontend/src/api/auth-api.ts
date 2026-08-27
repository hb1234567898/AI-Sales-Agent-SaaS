import { clearAuthTokens, saveAuthTokens, type AuthTokens } from '../auth/auth-token-storage'
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

export interface LoginInput {
  email: string
  password: string
  rememberMe: boolean
}

interface AuthTokenResponse extends AuthTokens {
  tokenType: 'Bearer'
  session: AuthSession
}

export async function getAuthSession() {
  return getJson<AuthSession>('/api/v1/auth/session')
}

export async function login(input: LoginInput) {
  const result = await requestJson<AuthTokenResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  saveAuthTokens(result, input.rememberMe)
  return result.session
}

export async function logout() {
  try {
    await requestJson<void>('/api/v1/auth/logout', { method: 'POST' })
  }
  finally {
    clearAuthTokens()
  }
}
