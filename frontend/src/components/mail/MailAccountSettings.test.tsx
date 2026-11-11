import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { MailAccountSettings } from './MailAccountSettings'
import { getMailAccount, saveMailAccount, testMailAccount } from '../../api/mail-api'
const auth = vi.hoisted(() => ({ role: 'OWNER' }))
vi.mock('../../auth/use-auth', () => ({ useAuth: () => auth }))
vi.mock('../../api/mail-api', () => ({ getMailAccount: vi.fn(), saveMailAccount: vi.fn(), testMailAccount: vi.fn() }))
const account = { host: 'imap.example.test', port: 993, username: 'sales@example.test', ssl: true, folder: 'INBOX', enabled: false, passwordConfigured: true, version: 2, lastSyncAt: null, lastError: null, syncing: false }
function mount() { return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MailAccountSettings /></QueryClientProvider>) }
beforeEach(() => { vi.resetAllMocks(); auth.role = 'OWNER'; vi.mocked(getMailAccount).mockResolvedValue(account) })
afterEach(cleanup)
it('never reveals saved passwords and requires saving edits before testing', async () => {
  const user = userEvent.setup(); mount()
  const password = await screen.findByLabelText('IMAP 授权码')
  expect(password).toHaveValue('')
  expect(screen.getByRole('button', { name: '测试收信' })).toBeEnabled()
  await user.clear(screen.getByLabelText('IMAP 服务器')); await user.type(screen.getByLabelText('IMAP 服务器'), 'imap.new.test')
  expect(screen.getByRole('button', { name: '测试收信' })).toBeDisabled()
  await user.type(password, 'app-password')
  vi.mocked(saveMailAccount).mockResolvedValue({ ...account, host: 'imap.new.test', version: 3 })
  await user.click(screen.getByRole('button', { name: '保存收信配置' }))
  await waitFor(() => expect(saveMailAccount).toHaveBeenCalledWith(expect.objectContaining({ host: 'imap.new.test', password: 'app-password', version: 2 }), expect.anything()))
  expect(await screen.findByLabelText('IMAP 授权码')).toHaveValue('')
  expect(testMailAccount).not.toHaveBeenCalled()
})
it('prevents ordinary members from changing the shared mailbox', async () => {
  auth.role = 'SALES'; mount()
  expect(await screen.findByLabelText('IMAP 服务器')).toBeDisabled()
  expect(screen.getByRole('button', { name: '保存收信配置' })).toBeDisabled()
})
