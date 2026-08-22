import { Navigate, createBrowserRouter } from 'react-router'
import { AppShell } from '../components/layout/AppShell'
import { RouteErrorPage } from '../pages/RouteErrorPage'
import { RouteLoadingPage } from '../pages/RouteLoadingPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/app/today" replace />,
    errorElement: <RouteErrorPage />,
  },
  {
    path: '/app',
    element: <AppShell />,
    errorElement: <RouteErrorPage />,
    hydrateFallbackElement: <RouteLoadingPage />,
    children: [
      { index: true, element: <Navigate to="today" replace /> },
      {
        path: 'today',
        lazy: async () => ({ Component: (await import('../pages/TodayPage')).TodayPage }),
      },
      {
        path: 'customers',
        lazy: async () => ({
          Component: (await import('../pages/CustomersPage')).CustomersPage,
        }),
      },
      {
        path: 'follow-ups',
        lazy: async () => ({
          Component: (await import('../pages/FollowUpsPage')).FollowUpsPage,
        }),
      },
      {
        path: 'approvals',
        lazy: async () => ({
          Component: (await import('../pages/ApprovalsPage')).ApprovalsPage,
        }),
      },
      {
        path: 'agent-runs',
        lazy: async () => ({
          Component: (await import('../pages/AgentRunsPage')).AgentRunsPage,
        }),
      },
      {
        path: 'analytics',
        lazy: async () => ({
          Component: (await import('../pages/AnalyticsPage')).AnalyticsPage,
        }),
      },
      {
        path: 'settings',
        lazy: async () => ({
          Component: (await import('../pages/SettingsPage')).SettingsPage,
        }),
      },
    ],
  },
])
