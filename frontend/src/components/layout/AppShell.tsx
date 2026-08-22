import {
  Bell,
  CaretDown,
  ChartLineUp,
  CheckSquareOffset,
  CirclesThreePlus,
  GearSix,
  House,
  List,
  MagnifyingGlass,
  Robot,
  Target,
  UsersThree,
  X,
  type IconProps,
} from '@phosphor-icons/react'
import { useState, type ComponentType } from 'react'
import { NavLink, Outlet } from 'react-router'

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

function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <aside className={`app-sidebar${open ? ' is-open' : ''}`} aria-label="应用导航">
      <div className="sidebar-brand">
        <span className="brand-symbol" aria-hidden>
          <CirclesThreePlus size={20} weight="fill" />
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
        <span className="workspace-avatar">默</span>
        <span>
          <small>当前工作区</small>
          <strong>默认工作区</strong>
        </span>
        <CaretDown size={14} aria-hidden />
      </button>

      <div className="sidebar-navigation">
        <NavigationGroup label="销售工作" items={workNavigation} onNavigate={onClose} />
        <NavigationGroup label="运营与配置" items={insightNavigation} onNavigate={onClose} />
      </div>

      <div className="sidebar-footer">
        <span className="user-avatar" aria-hidden>管</span>
        <span>
          <strong>系统管理员</strong>
          <small>管理员</small>
        </span>
      </div>
    </aside>
  )
}

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="app-shell">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
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
            <span className="environment-pill"><i />生产环境</span>
            <button className="icon-button" type="button" disabled aria-label="通知" title="通知中心即将开放">
              <Bell size={19} />
            </button>
            <span className="topbar-avatar" aria-label="系统管理员">管</span>
          </div>
        </header>

        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
