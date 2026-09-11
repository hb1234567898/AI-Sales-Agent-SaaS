import { getJson } from './axios-client'

export interface HealthResponse {
  service: string
  status: string
  timestamp: string
}

export function getSystemHealth() {
  return getJson<HealthResponse>('/api/v1/system/health')
}
