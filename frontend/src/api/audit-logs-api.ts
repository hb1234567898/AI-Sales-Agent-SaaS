import { getJson } from './http-client'

export type AuditResult = 'SUCCEEDED' | 'FAILED' | 'DENIED'

export interface AuditEvent {
  id: number
  actorIdentifier: string | null
  action: string
  targetType: string
  targetId: string
  result: AuditResult
  ipAddress: string | null
  userAgent: string | null
  requestId: string | null
  metadata: Record<string, unknown>
  occurredAt: string
}

export interface AuditEventPage {
  content: AuditEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AuditEventQuery {
  keyword: string
  action: string
  targetType: string
  result: string
  page: number
  size: number
}

export function getAuditEvents(query: AuditEventQuery) {
  const params = new URLSearchParams({
    keyword: query.keyword,
    action: query.action,
    targetType: query.targetType,
    result: query.result,
    page: String(query.page),
    size: String(query.size),
  })
  return getJson<AuditEventPage>(`/api/v1/audit-events?${params.toString()}`)
}
