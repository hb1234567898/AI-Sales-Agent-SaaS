import { clearAuthTokens, saveAuthTokens, type AuthTokens } from '../auth/auth-token-storage'
import { encryptPasswordIfAvailable } from '../auth/password-transport'
import { getJson, requestJson } from './axios-client'

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
  const passwordPayload = await encryptPasswordIfAvailable(input.password)
  const result = await requestJson<AuthTokenResponse>('/api/v1/auth/login', {
    method: 'POST',
    data: {
      email: input.email,
      rememberMe: input.rememberMe,
      ...passwordPayload,
    },
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
