import { useContext } from 'react'
import { AuthContext } from './auth-context'

export function useAuth() {
  const session = useContext(AuthContext)
  if (!session) {
    throw new Error('useAuth 必须在 AuthProvider 内使用')
  }
  return session
}

export function useIsGuest() {
  return useContext(AuthContext)?.role === 'GUEST'
}
