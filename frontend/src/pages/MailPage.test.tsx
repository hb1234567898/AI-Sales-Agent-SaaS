import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import { MailPage } from './MailPage'
import * as api from '../api/mail-api'
import { getFollowUpOptions } from '../api/follow-ups-api'
import { getCustomerFiles } from '../api/files-api'
import { getApproval, approveApproval, rejectApproval, type Approval } from '../api/approvals-api'

const auth = vi.hoisted(() => ({ role: 'OWNER', memberId: 'member-1' }))
vi.mock('../auth/use-auth', () => ({ useAuth: () => auth }))
vi.mock('../api/mail-api', () => ({ getMailAccount: vi.fn(), getMailMessages: vi.fn(), getMailMessage: vi.fn(), getMailThread: vi.fn(), handleMail: vi.fn(), linkMail: vi.fn(), analyzeMail: vi.fn(), replyMail: vi.fn(), syncMail: vi.fn() }))
vi.mock('../api/follow-ups-api', () => ({ getFollowUpOptions: vi.fn() }))
vi.mock('../api/files-api', () => ({ getCustomerFiles: vi.fn() }))
vi.mock('../api/approvals-api', () => ({ getApproval: vi.fn(), approveApproval: vi.fn(), rejectApproval: vi.fn() }))
const message: api.MailMessage = { id: 'mail-1', customerId: 'customer-1', customerName: '和成科技', direction: 'INBOUND', status: 'RECEIVED', fromAddress: 'buyer@example.test', recipients: ['sales@example.test'], subject: '确认方案时间', bodyText: '<script>not executable</script>\n请确认部署周期。', attachmentNames: ['需求.pdf'], occurredAt: '2026-11-11T02:00:00Z', threadId: 'thread-1', parentId: null, version: 3, replyApprovalId: null, approvalStatus: null, actionStatus: null }
function mount(entry = '/app/mail?message=mail-1') { return render(<MemoryRouter initialEntries={[entry]}><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><MailPage /></QueryClientProvider></MemoryRouter>) }
const show = HTMLDialogElement.prototype.showModal, close = HTMLDialogElement.prototype.close
beforeAll(() => { HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }; HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') } })
afterAll(() => { HTMLDialogElement.prototype.showModal = show; HTMLDialogElement.prototype.close = close })
afterEach(cleanup)
beforeEach(() => {
  vi.resetAllMocks(); auth.role = 'OWNER'
  vi.mocked(api.getMailAccount).mockResolvedValue({ host: 'imap.example.test', port: 993, username: 'sales@example.test', ssl: true, folder: 'INBOX', enabled: true, passwordConfigured: true, version: 0, lastSyncAt: null, lastError: null, syncing: false })
  vi.mocked(api.getMailMessages).mockResolvedValue({ content: [message], page: 0, totalElements: 21, totalPages: 2, first: true, last: false })
  vi.mocked(api.getMailMessage).mockResolvedValue(message); vi.mocked(api.getMailThread).mockResolvedValue([message]); vi.mocked(getCustomerFiles).mockResolvedValue([])
})
it('renders mail as plain text, supports pagination and does not load a model automatically', async () => {
  const user = userEvent.setup(); mount()
  expect(await screen.findByRole('heading', { name: message.subject })).toBeInTheDocument()
  expect(document.querySelector('.mail-body script')).toBeNull()
  expect(api.analyzeMail).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: '下一页邮件' }))
  await waitFor(() => expect(api.getMailMessages).toHaveBeenCalledWith('PENDING', 1))
})
it('submits an immutable recipient-bound reply approval, never sends directly', async () => {
  vi.mocked(api.replyMail).mockResolvedValue({ approvalId: 'approval-1', runId: 'run-1' })
  const user = userEvent.setup(); mount()
  await user.click(await screen.findByRole('button', { name: '撰写回复' }))
  await user.type(screen.getByLabelText('回复正文'), '您好，预计两周完成部署。')
  await user.click(screen.getByRole('button', { name: '提交回复审批' }))
  await waitFor(() => expect(api.replyMail).toHaveBeenCalledWith('mail-1', { subject: 'Re: 确认方案时间', body: '您好，预计两周完成部署。', attachmentIds: [], version: 3 }))
  expect(approveApproval).not.toHaveBeenCalled()
})
it('prevents composing a second reply while an approval is pending', async () => {
  vi.mocked(api.getMailMessage).mockResolvedValue({ ...message, replyApprovalId: 'approval-1', approvalStatus: 'PENDING' })
  mount()
  expect(await screen.findByRole('button', { name: '撰写回复' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '标记已处理' })).toBeDisabled()
})
it('reviews the stored approval snapshot and only sends after explicit approval', async () => {
  vi.mocked(api.getMailMessage).mockResolvedValue({ ...message, replyApprovalId: 'approval-1', approvalStatus: 'PENDING' })
  const approval = { id: 'approval-1', version: 5, status: 'PENDING', actionStatus: 'AWAITING_APPROVAL', preview: { to: message.fromAddress, subject: 'Re: 确认方案时间', body: '最终回复正文', attachments: [] } } as unknown as Approval
  vi.mocked(getApproval).mockResolvedValue(approval); vi.mocked(approveApproval).mockResolvedValue({ ...approval, status: 'APPROVED', actionStatus: 'SUCCEEDED' })
  const user = userEvent.setup(); mount()
  await user.click(await screen.findByRole('button', { name: '审核回复' }))
  const dialog = screen.getByRole('dialog', { name: '审核邮件回复' })
  expect(await within(dialog).findByText('最终回复正文')).toBeInTheDocument()
  expect(approveApproval).not.toHaveBeenCalled()
  await user.click(within(dialog).getByRole('button', { name: '批准并发送' }))
  await waitFor(() => expect(approveApproval).toHaveBeenCalledWith(approval))
  expect(rejectApproval).not.toHaveBeenCalled()
})
it('keeps failures visible instead of replacing them with simulated success', async () => {
  vi.mocked(api.handleMail).mockRejectedValue(new Error('邮件状态已变化'))
  const user = userEvent.setup(); mount()
  await user.click(await screen.findByRole('button', { name: '标记已处理' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('邮件状态已变化')
  expect(api.handleMail).toHaveBeenCalledWith('mail-1', 3)
})
it('links unmatched mail only after a customer is explicitly selected', async () => {
  vi.mocked(api.getMailMessage).mockResolvedValue({ ...message, customerId: null, customerName: null, status: 'UNMATCHED' })
  vi.mocked(getFollowUpOptions).mockResolvedValue({ customers: [{ id: 'customer-1', name: '和成科技' }], owners: [] })
  vi.mocked(api.linkMail).mockResolvedValue(message)
  const user = userEvent.setup(); mount()
  await user.click(await screen.findByRole('combobox', { name: '关联邮件客户' }))
  await user.click(screen.getByRole('option', { name: '和成科技' })); await user.click(screen.getByRole('button', { name: '确认关联' }))
  await waitFor(() => expect(api.linkMail).toHaveBeenCalledWith('mail-1', 'customer-1', 3))
})
it('keeps all guest mail actions read-only', async () => {
  auth.role = 'GUEST'; mount()
  expect(await screen.findByRole('button', { name: '撰写回复' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '分析客户意图' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '同步邮件' })).toBeDisabled()
})
it('blocks resending after uncertain SMTP delivery and asks for mailbox verification', async () => {
  vi.mocked(api.getMailMessage).mockResolvedValue({ ...message, deliveryUncertain: true, replyApprovalId: 'approval-1', approvalStatus: 'APPROVED', actionStatus: 'FAILED' })
  mount()
  expect(await screen.findByRole('button', { name: '撰写回复' })).toBeDisabled()
  expect(screen.getByText(/此前回复的发送结果尚未确认/)).toBeInTheDocument()
})
