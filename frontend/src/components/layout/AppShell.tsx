import {
  Bell,
  Buildings,
  CaretDown,
  ChartLineUp,
  CheckSquareOffset,
  GearSix,
  House,
  List,
  MagnifyingGlass,
  Robot,
  SignOut,
  Eye,
  FileText,
  Target,
  UsersThree,
  UserList,
  X,
  type IconProps,
} from '@phosphor-icons/react'
import { useState, type ComponentType } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { logout } from '../../api/auth-api'
import { useAuth } from '../../auth/use-auth'
import { leaveGuestMode } from '../../auth/guest-session'

interface NavItem {
  to: string
  label: string
  icon: ComponentType<IconProps>
}

const workNavigation: NavItem[] = [
  { to: '/app/today', label: '今日工作台', icon: House },
  { to: '/app/customers', label: '客户', icon: UsersThree },
  { to: '/app/follow-ups', label: '跟进任务', icon: Target },
  { to: '/app/approvals', label: '审批', icon: CheckSquareOffset },
]

const insightNavigation: NavItem[] = [
  { to: '/app/agent-runs', label: 'Agent 运行', icon: Robot },
  { to: '/app/analytics', label: '效果分析', icon: ChartLineUp },
  { to: '/app/settings', label: '设置', icon: GearSix },
]

const adminNavigation: NavItem[] = [
  { to: '/app/admin/members', label: '成员管理', icon: UserList },
  { to: '/app/admin/team', label: '团队管理', icon: Buildings },
  { to: '/app/audit-logs', label: '日志管理', icon: FileText },
]

function NavigationGroup({
  label,
  items,
  onNavigate,
}: {
  label: string
  items: NavItem[]
  onNavigate?: () => void
}) {
  return (
    <div className="navigation-group">
      <p className="navigation-label">{label}</p>
      <nav className="navigation-list" aria-label={label}>
        {items.map(({ to, label: itemLabel, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            className={({ isActive }) => `navigation-item${isActive ? ' is-active' : ''}`}
          >
            <Icon size={18} aria-hidden />
            <span>{itemLabel}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function Sidebar({
  open,
  onClose,
  organizationName,
  displayName,
  email,
  role,
  onLogout,
  logoutPending,
}: {
  open: boolean
  onClose: () => void
  organizationName: string
  displayName: string
  email: string
  role: string
  onLogout: () => void
  logoutPending: boolean
}) {
  const roleLabel = role === 'GUEST' ? '只读浏览' : role === 'OWNER' ? '所有者' : role === 'ADMIN' ? '管理员' : '销售成员'

  return (
    <aside className={`app-sidebar${open ? ' is-open' : ''}`} aria-label="应用导航">
      <div className="sidebar-brand">
        <span className="brand-symbol" aria-hidden>
          <img src="/favicon.svg" alt="" />
        </span>
        <div>
          <strong>Sales Agent</strong>
          <span>销售运营工作台</span>
        </div>
        <button className="icon-button sidebar-close" type="button" onClick={onClose} aria-label="关闭导航">
          <X size={18} />
        </button>
      </div>

      <button className="workspace-switcher" type="button" disabled title="多工作区即将开放">
        <span className="workspace-avatar">{organizationName.slice(0, 1)}</span>
        <span>
          <small>当前工作区</small>
          <strong>{organizationName}</strong>
        </span>
        <CaretDown size={14} aria-hidden />
      </button>

      <div className="sidebar-navigation">
        <NavigationGroup label="销售工作" items={workNavigation} onNavigate={onClose} />
        <NavigationGroup label="运营与配置" items={insightNavigation} onNavigate={onClose} />
        {role === 'OWNER' || role === 'ADMIN' ? (
          <NavigationGroup label="管理员" items={adminNavigation} onNavigate={onClose} />
        ) : null}
      </div>

      <div className="sidebar-footer">
        <span className="user-avatar" aria-hidden>{displayName.slice(0, 1)}</span>
        <span className="sidebar-user-copy" title={email}>
          <strong>{displayName}</strong>
          <small>{roleLabel}</small>
        </span>
        <button
          className="sidebar-logout"
          type="button"
          onClick={onLogout}
          disabled={logoutPending}
          aria-label={role === 'GUEST' ? '退出游客模式' : '退出登录'}
          title={role === 'GUEST' ? '退出游客模式' : '退出登录'}
        ><SignOut size={17} /></button>
      </div>
    </aside>
  )
}

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const session = useAuth()
  const guestMode = session.role === 'GUEST'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      queryClient.removeQueries({ queryKey: ['auth-session'] })
      navigate('/login', { replace: true })
    },
  })

  function exitSession() {
    if (guestMode) {
      leaveGuestMode()
      queryClient.removeQueries({ queryKey: ['auth-session'] })
      navigate('/login', { replace: true })
      return
    }
    logoutMutation.mutate()
  }

  return (
    <div className={`app-shell${guestMode ? ' is-guest' : ''}`}>
      <Sidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        organizationName={session.organizationName}
        displayName={session.displayName}
        email={session.email}
        role={session.role}
        onLogout={exitSession}
        logoutPending={logoutMutation.isPending}
      />
      {sidebarOpen ? (
        <button
          className="sidebar-backdrop"
          type="button"
          aria-label="关闭导航"
          onClick={() => setSidebarOpen(false)}
        />
      ) : null}

      <div className="shell-content">
        <header className="app-topbar">
          <button
            className="icon-button mobile-menu"
            type="button"
            onClick={() => setSidebarOpen(true)}
            aria-label="打开导航"
          >
            <List size={20} />
          </button>

          <label className="global-search">
            <MagnifyingGlass size={17} aria-hidden />
            <input type="search" placeholder="搜索客户、任务或运行记录" aria-label="全局搜索" />
            <kbd>Ctrl K</kbd>
          </label>

          <div className="topbar-actions">
            <span className={`environment-pill${guestMode ? ' is-guest' : ''}`}>
              {guestMode ? <Eye size={14} /> : <i />}{guestMode ? '游客只读' : '生产环境'}
            </span>
            <button className="icon-button" type="button" disabled aria-label="通知" title="通知中心即将开放">
              <Bell size={19} />
            </button>
            <span className="topbar-avatar" aria-label={session.displayName}>{session.displayName.slice(0, 1)}</span>
          </div>
        </header>

        {guestMode ? (
          <div className="guest-readonly-banner" role="status">
            <span><Eye size={15} />你正在以游客身份浏览，新增、编辑、审批和 Agent 执行均已禁用。</span>
            <button type="button" onClick={exitSession}>登录后使用完整功能</button>
          </div>
        ) : null}

        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
