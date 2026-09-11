import { getJson, requestJson } from './axios-client'

export interface AgentRun {
  id: string
  name: string
  triggerType: string
  status: string
  businessDate: string
  scope: Record<string, unknown>
  outputSummary: Record<string, unknown>
  totalCandidates: number
  processedCount: number
  succeededCount: number
  skippedCount: number
  failedCount: number
  pendingApprovalCount: number
  errorMessage: string | null
  queuedAt: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface AgentStep {
  id: string
  customerId: string | null
  sequenceNo: number
  stepType: string
  name: string
  status: string
  inputSnapshot: Record<string, unknown>
  outputSnapshot: Record<string, unknown>
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
  durationMs: number | null
}

export interface AgentRunPage {
  content: AgentRun[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AgentStepPage {
  content: AgentStep[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export function getAgentRuns() {
  return getJson<AgentRunPage>('/api/v1/agent-runs?page=0&size=20')
}

export function getAgentRunSteps(runId: string) {
  return getJson<AgentStepPage>(`/api/v1/agent-runs/${runId}/steps?page=0&size=100`)
}

export function createAgentRun() {
  return requestJson<AgentRun>('/api/v1/agent-runs', {
    method: 'POST',
    data: { maxCustomers: 5, recentDays: 30 },
  })
}
