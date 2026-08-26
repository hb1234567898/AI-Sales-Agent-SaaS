import type { AuthSession } from '../api/auth-api'

const guestModeKey = 'sales-agent:guest-mode'

export const guestSession: AuthSession = {
  userId: 'guest',
  memberId: 'guest',
  organizationId: '00000000-0000-0000-0000-000000000001',
  email: 'guest@local',
  displayName: '游客',
  organizationName: '演示销售团队',
  role: 'GUEST',
  expiresAt: '',
}

export function enterGuestMode() {
  sessionStorage.setItem(guestModeKey, 'true')
}

export function leaveGuestMode() {
  sessionStorage.removeItem(guestModeKey)
}

export function isGuestMode() {
  return sessionStorage.getItem(guestModeKey) === 'true'
}
