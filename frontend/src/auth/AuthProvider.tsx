import type { ReactNode } from 'react'
import type { AuthSession } from '../api/auth-api'
import { AuthContext } from './auth-context'

export function AuthProvider({ session, children }: { session: AuthSession; children: ReactNode }) {
  return <AuthContext.Provider value={session}>{children}</AuthContext.Provider>
}
