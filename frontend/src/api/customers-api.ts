import { getJson, requestJson } from './axios-client'

export type CustomerStage = 'LEAD' | 'QUALIFIED' | 'DISCOVERY' | 'DEMO' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST'
export type CustomerStatus = 'ACTIVE' | 'ARCHIVED'
export type CustomerSource = 'MANUAL' | 'IMPORT' | 'CRM' | 'CHAT' | 'API'

export interface PrimaryContact {
  name: string
  email: string | null
  phone: string | null
}

export interface Customer {
  id: string
  name: string
  website: string | null
  industry: string | null
  employeeRange: string | null
  stage: CustomerStage
  status: CustomerStatus
  source: CustomerSource
  ownerMemberId: string | null
  ownerName: string | null
  score: number | null
  estimatedValue: number | null
  nextAction: string | null
  lastInteractionAt: string | null
  nextFollowUpAt: string | null
  primaryContact: PrimaryContact | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface CustomerMetrics {
  total: number
  highIntent: number
  activeOpportunities: number
  averageScore: number
}

export interface OwnerOption {
  id: string
  name: string
}

export interface CustomerPage {
  content: Customer[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface CustomerUpsertInput {
  name: string
  website?: string | null
  industry?: string | null
  employeeRange?: string | null
  stage: CustomerStage
  status: CustomerStatus
  source: CustomerSource
  ownerMemberId?: string | null
  score?: number | null
  estimatedValue?: number | null
  nextAction?: string | null
  nextFollowUpAt?: string | null
  primaryContactName?: string | null
  primaryContactEmail?: string | null
  primaryContactPhone?: string | null
}

export interface CustomerQuery {
  query: string
  stage?: CustomerStage
  page: number
  size: number
}

export function getCustomers(filters: CustomerQuery) {
  const params = new URLSearchParams({
    query: filters.query,
    page: String(filters.page),
    size: String(filters.size),
    status: 'ACTIVE',
  })
  if (filters.stage) params.set('stage', filters.stage)
  return getJson<CustomerPage>(`/api/v1/customers?${params.toString()}`)
}

export function getCustomer(customerId: string) {
  return getJson<Customer>(`/api/v1/customers/${customerId}`)
}

export function getCustomerMetrics() {
  return getJson<CustomerMetrics>('/api/v1/customers/metrics')
}

export function getCustomerOwners() {
  return getJson<OwnerOption[]>('/api/v1/customers/owners')
}

export function createCustomer(input: CustomerUpsertInput) {
  return requestJson<Customer>('/api/v1/customers', {
    method: 'POST',
    data: input,
  })
}

export function updateCustomer(customerId: string, input: CustomerUpsertInput) {
  return requestJson<Customer>(`/api/v1/customers/${customerId}`, {
    method: 'PUT',
    data: input,
  })
}
