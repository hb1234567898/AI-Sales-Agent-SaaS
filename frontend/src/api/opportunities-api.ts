import { getJson, requestJson } from './axios-client'

export type OpportunityStage = 'QUALIFIED' | 'DISCOVERY' | 'DEMO' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST'
export type ForecastCategory = 'PIPELINE' | 'BEST_CASE' | 'COMMIT'
export const opportunityStages: Record<OpportunityStage, string> = {
  QUALIFIED: '已确认商机', DISCOVERY: '需求调研', DEMO: '产品验证', PROPOSAL: '方案报价', NEGOTIATION: '商务谈判', WON: '赢单', LOST: '输单',
}
export const forecastCategories: Record<ForecastCategory, string> = { PIPELINE: '管道', BEST_CASE: '最佳情况', COMMIT: '承诺' }

export interface Opportunity {
  id: string
  customerId: string
  company: string
  name: string
  ownerMemberId: string | null
  ownerName: string | null
  stage: OpportunityStage
  status: 'OPEN' | 'WON' | 'LOST' | 'ARCHIVED'
  amount: number | null
  probability: number | null
  expectedCloseDate: string | null
  forecastCategory: ForecastCategory
  nextStep: string | null
  closeReason: string | null
  stageChangedAt: string
  updatedAt: string
  version: number
}
export interface OpportunityInput {
  customerId: string
  name: string
  ownerMemberId: string
  amount: number
  probability: number
  expectedCloseDate: string | null
  forecastCategory: ForecastCategory
  nextStep: string | null
  version?: number
}
export interface OpportunityPage {
  content: Opportunity[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean
}
export interface OpportunityMetrics { activeCount: number; pipelineAmount: number; weightedAmount: number; stalledCount: number }
export interface OpportunityEvent { id: string; actorName: string; fromStage: OpportunityStage | null; toStage: OpportunityStage; summary: string; createdAt: string }
export function getOpportunities(filters: { query?: string; stage?: string; owner?: string; page?: number; size?: number } = {}) {
  const params = new URLSearchParams({ query: filters.query ?? '', page: String(filters.page ?? 0), size: String(filters.size ?? 50) })
  if (filters.stage) params.set('stage', filters.stage)
  if (filters.owner) params.set('owner', filters.owner)
  return getJson<OpportunityPage>(`/api/v1/opportunities?${params}`)
}
export function getOpportunity(id: string) { return getJson<Opportunity>(`/api/v1/opportunities/${id}`) }
export function getOpportunityMetrics() { return getJson<OpportunityMetrics>('/api/v1/opportunities/metrics') }
export function getOpportunityHistory(id: string) { return getJson<OpportunityEvent[]>(`/api/v1/opportunities/${id}/history`) }
export function createOpportunity(input: OpportunityInput) { return requestJson<Opportunity>('/api/v1/opportunities', { method: 'POST', data: input }) }
export function updateOpportunity(id: string, input: OpportunityInput) { return requestJson<Opportunity>(`/api/v1/opportunities/${id}`, { method: 'PUT', data: input }) }
export function changeOpportunityStage(id: string, input: { stage: OpportunityStage; version: number; reason: string | null }) {
  return requestJson<Opportunity>(`/api/v1/opportunities/${id}/stage`, { method: 'PUT', data: input })
}
