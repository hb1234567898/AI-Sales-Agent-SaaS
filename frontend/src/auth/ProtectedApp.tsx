import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router'
import { getAuthSession } from '../api/auth-api'
import { ApiError } from '../api/http-client'
import { AppShell } from '../components/layout/AppShell'
import { RouteLoadingPage } from '../pages/RouteLoadingPage'
import { AuthProvider } from './AuthProvider'
import { guestSession, isGuestMode } from './guest-session'

export function ProtectedApp() {
  const location = useLocation()
  const [guestMode] = useState(isGuestMode)
  const [invalidated, setInvalidated] = useState(false)
  const sessionQuery = useQuery({
    queryKey: ['auth-session'],
    queryFn: getAuthSession,
    retry: false,
    staleTime: 5 * 60 * 1000,
    enabled: !guestMode,
  })

  useEffect(() => {
    const handleUnauthorized = () => setInvalidated(true)
    window.addEventListener('sales-agent:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('sales-agent:unauthorized', handleUnauthorized)
  }, [])

  if (guestMode) {
    return (
      <AuthProvider session={guestSession}>
        <AppShell />
      </AuthProvider>
    )
  }

  if (sessionQuery.isPending) {
    return <RouteLoadingPage />
  }

  if (invalidated || sessionQuery.error instanceof ApiError && sessionQuery.error.status === 401) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (sessionQuery.isError) {
    throw sessionQuery.error
  }

  return (
    <AuthProvider session={sessionQuery.data}>
      <AppShell />
    </AuthProvider>
  )
}
