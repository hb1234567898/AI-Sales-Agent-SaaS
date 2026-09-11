export interface AuthTokens {
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
}

const localStorageKey = 'sales-agent:auth-tokens'
const sessionStorageKey = 'sales-agent:auth-tokens'

export function getAuthTokens(): AuthTokens | null {
  return parseTokens(localStorage.getItem(localStorageKey)) ?? parseTokens(sessionStorage.getItem(sessionStorageKey))
}

export function saveAuthTokens(tokens: AuthTokens, persistent: boolean) {
  clearAuthTokens()
  const storage = persistent ? localStorage : sessionStorage
  storage.setItem(persistent ? localStorageKey : sessionStorageKey, JSON.stringify({
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: tokens.accessTokenExpiresAt,
    refreshToken: tokens.refreshToken,
    refreshTokenExpiresAt: tokens.refreshTokenExpiresAt,
  }))
}

export function replaceAuthTokens(tokens: AuthTokens) {
  const persistent = localStorage.getItem(localStorageKey) !== null
  saveAuthTokens(tokens, persistent)
}

export function clearAuthTokens() {
  localStorage.removeItem(localStorageKey)
  sessionStorage.removeItem(sessionStorageKey)
}

function parseTokens(value: string | null): AuthTokens | null {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as Partial<AuthTokens>
    return typeof parsed.accessToken === 'string'
      && typeof parsed.accessTokenExpiresAt === 'string'
      && typeof parsed.refreshToken === 'string'
      && typeof parsed.refreshTokenExpiresAt === 'string'
      ? parsed as AuthTokens
      : null
  }
  catch {
    return null
  }
}
