import { createContext } from 'react'
import type { AuthSession } from '../api/auth-api'

export const AuthContext = createContext<AuthSession | null>(null)
