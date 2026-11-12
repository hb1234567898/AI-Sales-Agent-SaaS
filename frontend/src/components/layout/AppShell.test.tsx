import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'
import { availableGuides } from '../guides/feature-guides'

const auth = vi.hoisted(() => ({ role: 'GUEST' }))
vi.mock('../../auth/use-auth', () => ({ useAuth: () => ({ role: auth.role, userId: 'test-user', displayName: '游客', organizationName: '演示团队', email: 'guest@local' }) }))
vi.mock('../../desktop/DesktopUpdatePrompt', () => ({ DesktopUpdateIndicator: () => null }))
vi.mock('../../api/approvals-api', () => ({ getPendingApprovals: async () => ({ content: [], totalElements: 0 }) }))
vi.mock('../../api/follow-ups-api', () => ({ getFollowUpReminders: async () => ({ unreadCount: 0, items: [] }), readFollowUpReminder: async () => {}, readAllFollowUpReminders: async () => {} }))
function mount() {
  return render(<MemoryRouter initialEntries={['/app/today']}><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AppShell /></QueryClientProvider></MemoryRouter>)
}
afterEach(() => { cleanup(); sessionStorage.clear() })
beforeEach(() => { auth.role = 'GUEST' })
const originalShow = HTMLDialogElement.prototype.showModal
const originalClose = HTMLDialogElement.prototype.close
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
})
afterAll(() => { HTMLDialogElement.prototype.showModal = originalShow; HTMLDialogElement.prototype.close = originalClose })

describe('guest guide', () => {
  it('offers detailed guides per feature, remembers dismissal and can be reopened', async () => {
    const user = userEvent.setup(); const view = mount()
    const dialog = screen.getByRole('dialog', { name: '功能使用指南' })
    expect(within(dialog).getByText(`已完成 0 / ${availableGuides('GUEST').length}`)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /企业知识库与带引用问答/ }))
    expect(within(dialog).getByRole('heading', { name: '核对回答来源' })).toBeInTheDocument()
    expect(within(dialog).getByRole('heading', { name: '维护资料版本' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /成员账号与角色权限/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '关闭使用引导' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(sessionStorage.getItem('sales-agent:guest-guide-seen')).toBe('true')
    view.unmount(); mount()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '打开功能使用指南' }))
    expect(screen.getByRole('dialog', { name: '功能使用指南' })).toBeInTheDocument()
  })
  it('makes all management guides available to an owner', async () => {
    auth.role = 'OWNER'
    const user = userEvent.setup(); mount()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '打开功能使用指南' }))
    const dialog = screen.getByRole('dialog', { name: '功能使用指南' })
    expect(within(dialog).getByText(`已完成 0 / ${availableGuides('OWNER').length}`)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: /团队 Token 预算与成员分配/ })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: /操作日志查询与审计/ })).toBeInTheDocument()
  })
  it('does not expose administrator guides to a read-only member', async () => {
    auth.role = 'VIEWER'
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('button', { name: '打开功能使用指南' }))
    const dialog = screen.getByRole('dialog', { name: '功能使用指南' })
    expect(within(dialog).getByText(`已完成 0 / ${availableGuides('VIEWER').length}`)).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /团队 Token 预算与成员分配/ })).not.toBeInTheDocument()
  })
})


describe('feature navigation', () => {
  it.each(['GUEST', 'VIEWER', 'SALES', 'MANAGER', 'ADMIN', 'OWNER'])('includes a usable guide for every navigation entry visible to %s', (role) => {
    auth.role = role
    mount()
    const sidebar = screen.getByRole('complementary', { name: '应用导航' })
    const guides = availableGuides(role)
    for (const link of within(sidebar).getAllByRole('link')) {
      const matching = guides.filter(guide => guide.path === link.getAttribute('href'))
      expect(matching.length, `缺少“${link.textContent}”的功能引导`).toBeGreaterThan(0)
      expect(matching.every(guide => guide.steps.length > 0 && guide.steps.every(step => step.title && step.description && step.target))).toBe(true)
    }
  })
  it('searches new commercial features and supports the keyboard shortcut', async () => {
    sessionStorage.setItem('sales-agent:guest-guide-seen', 'true')
    const user = userEvent.setup(); mount()
    await user.keyboard('{Control>}k{/Control}')
    const input = screen.getByRole('searchbox', { name: '搜索功能' })
    expect(input).toHaveFocus()
    await user.type(input, '报价')
    const results = screen.getByRole('navigation', { name: '功能搜索结果' })
    expect(within(results).getByRole('link', { name: '报价单' })).toHaveAttribute('href', '/app/quotes')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('navigation', { name: '功能搜索结果' })).not.toBeInTheDocument()
  })
  it('does not expose administrator destinations to members in search', async () => {
    auth.role = 'VIEWER'
    const user = userEvent.setup(); mount()
    await user.type(screen.getByRole('searchbox', { name: '搜索功能' }), '成员管理')
    expect(screen.getByText('未找到匹配功能，请尝试其他关键词。')).toBeInTheDocument()
  })
})
