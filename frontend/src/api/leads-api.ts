import { getJson, requestJson } from './axios-client'
import type { CustomerSource, OwnerOption } from './customers-api'

export type LeadStatus = 'NEW' | 'CONTACTED' | 'NURTURING' | 'QUALIFIED' | 'DISQUALIFIED'

export interface Lead {
  id: string
  company: string
  industry: string | null
  contactName: string
  contactEmail: string | null
  contactPhone: string | null
  contactTitle: string | null
  source: CustomerSource
  status: LeadStatus
  ownerMemberId: string | null
  ownerName: string | null
  score: number | null
  nextAction: string | null
  nextFollowUpAt: string | null
  lastActivityAt: string | null
  createdAt: string
  updatedAt: string
}

export interface LeadUpsertInput {
  company: string
  industry?: string | null
  contactName: string
  contactEmail?: string | null
  contactPhone?: string | null
  contactTitle?: string | null
  source: CustomerSource
  status: LeadStatus
  ownerMemberId?: string | null
  score?: number | null
  nextAction?: string | null
  nextFollowUpAt?: string | null
}

export interface LeadPage {
  content: Lead[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface LeadMetrics { pending: number; qualified: number; unassigned: number; averageScore: number }
export interface LeadImportResult { total: number; created: number; skipped: number; errors: string[] }
export interface LeadConversionInput { opportunityName: string; amount?: number | null; expectedCloseDate?: string | null }

export function getLeads(filters: { query: string; status?: LeadStatus; page: number; size: number }) {
  const params = new URLSearchParams({ query: filters.query, page: String(filters.page), size: String(filters.size) })
  if (filters.status) params.set('status', filters.status)
  return getJson<LeadPage>(`/api/v1/leads?${params}`)
}

export function getLead(id: string) { return getJson<Lead>(`/api/v1/leads/${id}`) }
export function getLeadMetrics() { return getJson<LeadMetrics>('/api/v1/leads/metrics') }
export function getLeadOwners() { return getJson<OwnerOption[]>('/api/v1/leads/owners') }
export function createLead(input: LeadUpsertInput) { return requestJson<Lead>('/api/v1/leads', { method: 'POST', data: input }) }
export function updateLead(id: string, input: LeadUpsertInput) { return requestJson<Lead>(`/api/v1/leads/${id}`, { method: 'PUT', data: input }) }
export function assignLead(id: string, ownerMemberId: string) { return requestJson<Lead>(`/api/v1/leads/${id}/owner`, { method: 'PUT', data: { ownerMemberId } }) }
export function convertLead(id: string, input: LeadConversionInput) { return requestJson<{ leadId: string; customerId: string; opportunityId: string }>(`/api/v1/leads/${id}/convert`, { method: 'POST', data: input }) }
export function importLeads(leads: LeadUpsertInput[]) { return requestJson<LeadImportResult>('/api/v1/leads/import', { method: 'POST', data: { leads } }) }

export type { OwnerOption }
