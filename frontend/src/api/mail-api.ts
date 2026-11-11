import { getJson, requestJson } from './axios-client'
import type { ChatAnalysis } from './interactions-api'

export interface MailAccount {
  host: string; port: number; username: string; ssl: boolean; folder: string; enabled: boolean
  passwordConfigured: boolean; version: number; lastSyncAt: string | null; lastError: string | null; syncing: boolean
}
export interface MailMessage {
  id: string; customerId: string | null; customerName: string | null; direction: 'INBOUND' | 'OUTBOUND'; status: string
  fromAddress: string; recipients: string[]; subject: string; bodyText: string; attachmentNames: string[]
  occurredAt: string; threadId: string; parentId: string | null; version: number
  replyApprovalId: string | null; approvalStatus: string | null; actionStatus: string | null
  deliveryUncertain?: boolean
}
export interface MailPage { content: MailMessage[]; page: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export function getMailAccount() { return getJson<MailAccount>('/api/v1/mail/account') }
export function saveMailAccount(input: Pick<MailAccount, 'host' | 'port' | 'username' | 'ssl' | 'folder' | 'enabled' | 'version'> & { password?: string }) { return requestJson<MailAccount>('/api/v1/mail/account', { method: 'PUT', data: input }) }
export function testMailAccount() { return requestJson<{ message: string }>('/api/v1/mail/account/test', { method: 'POST' }) }
export function syncMail() { return requestJson<{ imported: number; unmatched: number; busy: boolean }>('/api/v1/mail/sync', { method: 'POST' }) }
export function getMailMessages(filter: string, page: number) { return getJson<MailPage>(`/api/v1/mail/messages?${new URLSearchParams({ filter, page: String(page), size: '20' })}`) }
export function getMailMessage(id: string) { return getJson<MailMessage>(`/api/v1/mail/messages/${id}`) }
export function getMailThread(id: string) { return getJson<MailMessage[]>(`/api/v1/mail/messages/${id}/thread`) }
export function linkMail(id: string, customerId: string, version: number) { return requestJson<MailMessage>(`/api/v1/mail/messages/${id}/link`, { method: 'POST', data: { customerId, version } }) }
export function handleMail(id: string, version: number) { return requestJson<void>(`/api/v1/mail/messages/${id}/handle`, { method: 'POST', data: { version } }) }
export function analyzeMail(id: string) { return requestJson<ChatAnalysis>(`/api/v1/mail/messages/${id}/analysis`, { method: 'POST' }) }
export function replyMail(id: string, input: { subject: string; body: string; attachmentIds: string[]; version: number }) { return requestJson<{ approvalId: string; runId: string }>(`/api/v1/mail/messages/${id}/reply`, { method: 'POST', data: input }) }
