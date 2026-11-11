import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { FollowUpsPage } from './FollowUpsPage'
import * as api from '../api/follow-ups-api'

const auth = vi.hoisted(() => ({ role: 'SALES', memberId: 'member-1' }))
vi.mock('../auth/use-auth', () => ({ useAuth: () => auth }))
vi.mock('../api/follow-ups-api', () => ({
  getFollowUps: vi.fn(), getFollowUp: vi.fn(), getFollowUpMetrics: vi.fn(), getFollowUpOptions: vi.fn(), getFollowUpRule: vi.fn(),
  createFollowUp: vi.fn(), updateFollowUp: vi.fn(), completeFollowUp: vi.fn(), cancelFollowUp: vi.fn(), snoozeFollowUp: vi.fn(),
  saveFollowUpRule: vi.fn(), runFollowUpRule: vi.fn(),
}))
const task: api.FollowUp = {
  id: 'task-1', customerId: 'customer-1', customerName: '和成科技', ownerMemberId: 'member-1', ownerName: '销售甲',
  status: 'OPEN', dueAt: '2099-11-11T02:00:00Z', priority: 60, intentLevel: null, riskLevel: null,
  reason: '确认需求', recommendedActionType: 'CALL', recommendedAction: { title: '联系采购负责人' }, origin: 'MANUAL',
  version: 3, createdAt: '2026-11-11T02:00:00Z', completedAt: null,
}
const rule: api.FollowUpRule = { enabled: false, inactiveDays: 7, runHour: 9, maxCustomers: 5, autoAnalyze: false, reminderMinutes: 30, version: -1, lastRun: null }
function mount(entry = '/app/follow-ups') {
  return render(<MemoryRouter initialEntries={[entry]}><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><FollowUpsPage /></QueryClientProvider></MemoryRouter>)
}
const show = HTMLDialogElement.prototype.showModal, close = HTMLDialogElement.prototype.close
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
})
afterAll(() => { HTMLDialogElement.prototype.showModal = show; HTMLDialogElement.prototype.close = close })
afterEach(cleanup)
beforeEach(() => {
  vi.resetAllMocks(); auth.role = 'SALES'
  vi.mocked(api.getFollowUps).mockResolvedValue({ content: [task], page: 0, size: 20, totalElements: 25, totalPages: 2, first: true, last: false })
  vi.mocked(api.getFollowUp).mockResolvedValue(task)
  vi.mocked(api.getFollowUpMetrics).mockResolvedValue({ pending: 25, today: 12, overdue: 3, completed: 30 })
  vi.mocked(api.getFollowUpOptions).mockResolvedValue({ customers: [{ id: 'customer-1', name: '和成科技' }], owners: [{ id: 'member-1', name: '销售甲' }] })
  vi.mocked(api.getFollowUpRule).mockResolvedValue(rule)
})

describe('proactive follow-ups', () => {
  it('uses server totals and paginates instead of counting the current page', async () => {
    const user = userEvent.setup(); mount()
    await screen.findByText('联系采购负责人')
    expect(within(screen.getByLabelText('跟进任务指标')).getByText('25')).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '任务范围' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '下一页' }))
    await waitFor(() => expect(api.getFollowUps).toHaveBeenCalledWith('ALL', 1, 'MINE'))
    await user.click(screen.getByRole('button', { name: '已结束' }))
    await waitFor(() => expect(api.getFollowUps).toHaveBeenCalledWith('CLOSED', 0, 'MINE'))
  })
  it('creates a task with selected customer, current member and an ISO timestamp', async () => {
    vi.mocked(api.createFollowUp).mockResolvedValue(task)
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('button', { name: '新建任务' }))
    const dialog = screen.getByRole('dialog', { name: '新建跟进任务' })
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: '关联客户' })).toBeEnabled())
    await user.click(within(dialog).getByRole('combobox', { name: '关联客户' }))
    await user.click(within(dialog).getByRole('option', { name: '和成科技' }))
    await user.type(within(dialog).getByLabelText('任务名称'), '确认预算')
    await user.click(within(dialog).getByRole('button', { name: '保存任务' }))
    await waitFor(() => expect(api.createFollowUp).toHaveBeenCalledWith(expect.objectContaining({ customerId: 'customer-1', ownerMemberId: 'member-1', title: '确认预算', actionType: 'CALL', dueAt: expect.stringMatching(/Z$/) })))
    expect(await screen.findByText('跟进任务已创建')).toBeInTheDocument()
  })
  it('sends optimistic versions and keeps editing open when a conflict occurs', async () => {
    vi.mocked(api.updateFollowUp).mockRejectedValue(new Error('任务已被更新，请刷新后重试'))
    const user = userEvent.setup(); mount()
    await user.click(await screen.findByRole('button', { name: '编辑 联系采购负责人' }))
    const dialog = screen.getByRole('dialog', { name: '编辑跟进任务' })
    await waitFor(() => expect(within(dialog).getByRole('button', { name: '保存任务' })).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: '保存任务' }))
    await waitFor(() => expect(api.updateFollowUp).toHaveBeenCalledWith('task-1', expect.objectContaining({ version: 3 })))
    expect(await screen.findByRole('alert')).toHaveTextContent('任务已被更新')
    expect(dialog).toBeInTheDocument()
  })
  it('completes and snoozes tasks using the current version', async () => {
    vi.mocked(api.completeFollowUp).mockResolvedValue(task); vi.mocked(api.snoozeFollowUp).mockResolvedValue(task)
    const user = userEvent.setup(); mount()
    await user.click(await screen.findByRole('button', { name: '完成 联系采购负责人' }))
    await waitFor(() => expect(api.completeFollowUp).toHaveBeenCalledWith('task-1', 3))
    await user.click(screen.getByRole('button', { name: '延期 联系采购负责人' }))
    await user.click(screen.getByRole('button', { name: '确认延期' }))
    await waitFor(() => expect(api.snoozeFollowUp).toHaveBeenCalledWith('task-1', expect.stringMatching(/Z$/), '', 3))
  })
  it('loads a notification-linked task even outside the current page', async () => {
    mount('/app/follow-ups?task=task-1')
    expect(await screen.findByRole('dialog', { name: '编辑跟进任务' })).toBeInTheDocument()
    expect(api.getFollowUp).toHaveBeenCalledWith('task-1')
  })
  it('requires a saved enabled rule before manually checking and shows failures', async () => {
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('button', { name: '自动跟进' }))
    const dialog = screen.getByRole('dialog', { name: '自动跟进规则' })
    expect(await within(dialog).findByRole('button', { name: /立即检查/ })).toBeDisabled()
    await user.click(within(dialog).getByRole('checkbox', { name: /开启主动跟进/ }))
    expect(within(dialog).getByRole('button', { name: /立即检查/ })).toBeDisabled()
    vi.mocked(api.saveFollowUpRule).mockRejectedValue(new Error('规则已更新'))
    await user.click(within(dialog).getByRole('button', { name: '保存规则' }))
    await waitFor(() => expect(api.saveFollowUpRule).toHaveBeenCalledWith(expect.objectContaining({ enabled: true, autoAnalyze: false, version: -1 }), expect.anything()))
    expect(await screen.findByRole('alert')).toHaveTextContent('规则已更新')
    expect(api.runFollowUpRule).not.toHaveBeenCalled()
  })
  it('keeps guest tasks and automation read-only', async () => {
    auth.role = 'GUEST'; const user = userEvent.setup(); mount()
    expect(screen.getByRole('button', { name: '新建任务' })).toBeDisabled()
    expect(await screen.findByRole('button', { name: '完成 联系采购负责人' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '自动跟进' }))
    expect(await screen.findByRole('button', { name: '保存规则' })).toBeDisabled()
  })
})
