import { getJson, requestJson } from './axios-client'

export interface Approval {
  id: string
  actionRequestId: string
  runId: string
  customerId: string
  customerName: string
  actionType: string
  riskLevel: string
  status: string
  reason: string
  preview: Record<string, unknown>
  requester: string
  version: number
  requestedAt: string
  expiresAt: string | null
}

export interface ApprovalPage {
  content: Approval[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export function getPendingApprovals() {
  return getJson<ApprovalPage>('/api/v1/approvals?status=PENDING&page=0&size=50')
}

export function approveApproval(approval: Approval) {
  return requestJson<Approval>(`/api/v1/approvals/${approval.id}/approve`, {
    method: 'POST',
    data: { expectedVersion: approval.version },
  })
}

export function rejectApproval(approval: Approval) {
  return requestJson<Approval>(`/api/v1/approvals/${approval.id}/reject`, {
    method: 'POST',
    data: { expectedVersion: approval.version },
  })
}
