import { Navigate, Outlet } from 'react-router'
import { useAuth } from './use-auth'

export function AdminRoute() {
  const session = useAuth()
  return session.role === 'OWNER' || session.role === 'ADMIN'
    ? <Outlet />
    : <Navigate to="/app/today" replace />
}
