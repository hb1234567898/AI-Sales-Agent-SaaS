import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { TodayFollowUps } from './TodayFollowUps'
import { completeFollowUp, getFollowUpMetrics, getFollowUps, type FollowUp } from '../../api/follow-ups-api'
import { useAuth } from '../../auth/use-auth'

vi.mock('../../api/follow-ups-api', () => ({ completeFollowUp: vi.fn(), getFollowUps: vi.fn(), getFollowUpMetrics: vi.fn() }))
vi.mock('../../auth/use-auth', () => ({ useAuth: vi.fn() }))

const task: FollowUp = {
  id: 'task-1', customerId: 'customer-1', customerName: '云岚科技', ownerMemberId: 'member-1', ownerName: '杨伟进',
  status: 'OPEN', dueAt: '2099-11-11T06:50:00Z', priority: 90, intentLevel: 'HIGH', riskLevel: 'LOW',
  reason: '客户希望确认方案范围', recommendedActionType: 'CALL', recommendedAction: { title: '确认方案评审时间' },
  origin: 'AI', version: 3, createdAt: '2026-11-11T01:00:00Z', completedAt: null,
}

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const rendered = render(<MemoryRouter><QueryClientProvider client={client}><TodayFollowUps /></QueryClientProvider></MemoryRouter>)
  return { ...rendered, client }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(useAuth).mockReturnValue({ role: 'SALES', memberId: 'member-1' } as ReturnType<typeof useAuth>)
  vi.mocked(getFollowUps).mockResolvedValue({ content: [task, { ...task, id: 'task-2', dueAt: '2000-01-01T01:00:00Z', origin: 'RULE', recommendedAction: { title: '了解技术方案评估进度' } }], page: 0, size: 5, totalElements: 2, totalPages: 1, first: true, last: true })
  vi.mocked(getFollowUpMetrics).mockResolvedValue({ pending: 2, today: 2, overdue: 1, completed: 0 })
  vi.mocked(completeFollowUp).mockResolvedValue({ ...task, status: 'COMPLETED' })
})

afterEach(cleanup)

it('shows separate metrics, readable origins, due status and task links', async () => {
  mount()
  const list = await screen.findByRole('list', { name: '我的跟进任务' })
  expect(within(list).getAllByRole('listitem')).toHaveLength(2)
  expect(screen.getByLabelText('待办统计')).toHaveTextContent('今日到期 2已逾期 1')
  expect(within(list).getByText('AI 建议')).toBeInTheDocument()
  expect(within(list).getByText('自动跟进')).toBeInTheDocument()
  expect(within(list).getByText('已逾期')).toBeInTheDocument()
  expect(within(list).getByText('待跟进')).toBeInTheDocument()
  expect(within(list).getByRole('link', { name: /确认方案评审时间/ })).toHaveAttribute('href', '/app/follow-ups?task=task-1')
  expect(list.querySelector('time')).toHaveAttribute('dateTime', task.dueAt)
})

it('keeps completing a task versioned and refreshes all affected counters', async () => {
  const { client } = mount()
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  await userEvent.click(await screen.findByRole('button', { name: '完成 确认方案评审时间' }))
  await waitFor(() => expect(completeFollowUp).toHaveBeenCalledWith('task-1', 3))
  for (const key of ['follow-ups', 'follow-up-metrics', 'follow-up-reminders']) {
    expect(invalidate).toHaveBeenCalledWith({ queryKey: [key] })
  }
})

it.each(['GUEST', 'VIEWER'])('keeps %s tasks read-only', async role => {
  vi.mocked(useAuth).mockReturnValue({ role, memberId: 'member-1' } as ReturnType<typeof useAuth>)
  mount()
  const button = await screen.findByRole('button', { name: '完成 确认方案评审时间' })
  expect(button).toBeDisabled()
  await userEvent.click(button)
  expect(completeFollowUp).not.toHaveBeenCalled()
})

it('labels imported tasks correctly and keeps a missing title usable', async () => {
  vi.mocked(getFollowUps).mockResolvedValue({ content: [{ ...task, origin: 'IMPORT', recommendedAction: {} }], page: 0, size: 5, totalElements: 1, totalPages: 1, first: true, last: true })
  mount()
  expect(await screen.findByText('导入记录')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /跟进客户/ })).toHaveAttribute('href', '/app/follow-ups?task=task-1')
})

it('provides a retry action when the list request fails', async () => {
  vi.mocked(getFollowUps).mockRejectedValueOnce(new Error('待办暂时无法读取'))
  mount()
  expect(await screen.findByRole('alert')).toHaveTextContent('待办暂时无法读取')
  await userEvent.click(screen.getByRole('button', { name: '重试' }))
  expect(await screen.findByRole('list', { name: '我的跟进任务' })).toBeInTheDocument()
})

it('exposes the empty state without completion buttons', async () => {
  vi.mocked(getFollowUps).mockResolvedValue({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, first: true, last: true })
  mount()
  expect(await screen.findByText('暂无待办')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '安排下一次跟进' })).toHaveAttribute('href', '/app/follow-ups')
  expect(screen.queryByRole('button', { name: /^完成 / })).not.toBeInTheDocument()
})
