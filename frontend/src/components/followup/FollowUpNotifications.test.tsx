import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { FollowUpNotifications } from './FollowUpNotifications'
import { getFollowUpReminders, readFollowUpReminder, readAllFollowUpReminders } from '../../api/follow-ups-api'
vi.mock('../../api/follow-ups-api', () => ({ getFollowUpReminders: vi.fn(), readFollowUpReminder: vi.fn(), readAllFollowUpReminders: vi.fn() }))
function mount(guest = false) {
  return render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><FollowUpNotifications pendingApprovals={1} guest={guest} /></QueryClientProvider></MemoryRouter>)
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getFollowUpReminders).mockResolvedValue({ unreadCount: 1, items: [{ id: 'reminder-1', followUpId: 'task-1', customerName: '和成科技', title: '确认预算', kind: 'OVERDUE', dueAt: '2026-11-11T02:00:00Z', createdAt: '2026-11-11T02:00:00Z' }] })
  vi.mocked(readFollowUpReminder).mockResolvedValue(undefined); vi.mocked(readAllFollowUpReminders).mockResolvedValue(undefined)
})
afterEach(cleanup)
it('combines counts and links directly to the reminded task', async () => {
  const user = userEvent.setup(); mount()
  await user.click(await screen.findByRole('button', { name: '2 条待处理通知' }))
  expect(screen.getByText('跟进已逾期')).toBeInTheDocument()
  const link = screen.getByRole('link', { name: /确认预算/ })
  expect(link).toHaveAttribute('href', '/app/follow-ups?task=task-1')
  await user.click(link)
  await waitFor(() => expect(readFollowUpReminder).toHaveBeenCalledWith('reminder-1', expect.anything()))
})
it('marks all reminders read and supports Escape', async () => {
  const user = userEvent.setup(); mount()
  const trigger = await screen.findByRole('button', { name: '2 条待处理通知' })
  await user.click(trigger); await user.click(screen.getByRole('button', { name: '全部已读' }))
  await waitFor(() => expect(readAllFollowUpReminders).toHaveBeenCalled())
  await user.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(trigger).toHaveFocus()
})
it('never writes guest reminder state to the server', async () => {
  const user = userEvent.setup(); mount(true)
  await user.click(await screen.findByRole('button', { name: '2 条待处理通知' }))
  expect(screen.getByRole('button', { name: '全部已读' })).toBeDisabled()
  await user.click(screen.getByRole('link', { name: /确认预算/ }))
  expect(readFollowUpReminder).not.toHaveBeenCalled()
})
