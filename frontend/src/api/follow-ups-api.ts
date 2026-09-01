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

export function getFollowUps(filter: string) {
  return getJson<FollowUpPage>(`/api/v1/follow-ups?filter=${encodeURIComponent(filter)}&page=0&size=50`)
}

export function completeFollowUp(id: string) {
  return requestJson<FollowUp>(`/api/v1/follow-ups/${id}/complete`, { method: 'POST' })
}
