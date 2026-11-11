import { getJson, requestJson } from './axios-client'

export interface FollowUp {
  id: string
  customerId: string
  customerName: string
  ownerMemberId: string | null
  ownerName: string | null
  status: string
  dueAt: string
  priority: number
  intentLevel: string | null
  riskLevel: string | null
  reason: string
  recommendedActionType: string
  recommendedAction: Record<string, unknown>
  origin: 'AI' | 'MANUAL' | 'IMPORT' | 'RULE'
  version: number
  createdAt: string
  completedAt: string | null
}

export interface FollowUpPage {
  content: FollowUp[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type FollowUpScope = 'MINE' | 'TEAM'
export interface FollowUpMetrics { pending: number; today: number; overdue: number; completed: number }
export interface FollowUpInput {
  customerId: string; ownerMemberId: string; title: string; dueAt: string; priority: number
  reason: string; actionType: string; version?: number
}
export interface FollowUpOptions { customers: { id: string; name: string }[]; owners: { id: string; name: string }[] }
export interface AutomationRun {
  id: string; status: string; createdCount: number; agentRunId: string | null; errorMessage: string | null; createdAt: string
}
export interface FollowUpRule {
  enabled: boolean; inactiveDays: number; runHour: number; maxCustomers: number; autoAnalyze: boolean
  reminderMinutes: number; version: number; lastRun: AutomationRun | null
}
export interface FollowUpReminder { id: string; followUpId: string; customerName: string; title: string; kind: 'DUE_SOON' | 'OVERDUE'; dueAt: string; createdAt: string }
export interface FollowUpReminders { unreadCount: number; items: FollowUpReminder[] }

export function getFollowUps(filter: string, page = 0, scope: FollowUpScope = 'MINE', size = 20) {
  return getJson<FollowUpPage>(`/api/v1/follow-ups?${new URLSearchParams({ filter, page: String(page), size: String(size), scope })}`)
}
export function getFollowUp(id: string) { return getJson<FollowUp>(`/api/v1/follow-ups/${id}`) }
export function getFollowUpMetrics(scope: FollowUpScope = 'MINE') { return getJson<FollowUpMetrics>(`/api/v1/follow-ups/metrics?scope=${scope}`) }
export function getFollowUpOptions(query: string) { return getJson<FollowUpOptions>(`/api/v1/follow-ups/options?query=${encodeURIComponent(query)}`) }
export function createFollowUp(input: FollowUpInput) { return requestJson<FollowUp>('/api/v1/follow-ups', { method: 'POST', data: input }) }
export function updateFollowUp(id: string, input: FollowUpInput) { return requestJson<FollowUp>(`/api/v1/follow-ups/${id}`, { method: 'PUT', data: input }) }
export function completeFollowUp(id: string, version?: number) { return requestJson<FollowUp>(`/api/v1/follow-ups/${id}/complete`, { method: 'POST', ...(version == null ? {} : { data: { version } }) }) }
export function cancelFollowUp(id: string, version: number) { return requestJson<FollowUp>(`/api/v1/follow-ups/${id}/cancel`, { method: 'POST', data: { version } }) }
export function snoozeFollowUp(id: string, dueAt: string, reason: string, version: number) { return requestJson<FollowUp>(`/api/v1/follow-ups/${id}/snooze`, { method: 'POST', data: { dueAt, reason, version } }) }
export function getFollowUpRule() { return getJson<FollowUpRule>('/api/v1/follow-ups/automation') }
export function saveFollowUpRule(input: Omit<FollowUpRule, 'lastRun'>) { return requestJson<FollowUpRule>('/api/v1/follow-ups/automation', { method: 'PUT', data: input }) }
export function runFollowUpRule() { return requestJson<AutomationRun>('/api/v1/follow-ups/automation/run', { method: 'POST' }) }
export function getFollowUpReminders() { return getJson<FollowUpReminders>('/api/v1/follow-ups/reminders') }
export function readFollowUpReminder(id: string) { return requestJson<void>(`/api/v1/follow-ups/reminders/${id}/read`, { method: 'POST' }) }
export function readAllFollowUpReminders() { return requestJson<void>('/api/v1/follow-ups/reminders/read-all', { method: 'POST' }) }
