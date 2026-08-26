import { getJson, requestJson } from './http-client'

export type AiModelStatusCode = 'READY' | 'DISABLED' | 'MISSING_API_KEY'

export interface AiModelStatus {
  provider: 'QWEN'
  model: string
  baseUrl: string
  apiKeyConfigured: boolean
  ready: boolean
  status: AiModelStatusCode
}

export interface AiModelTestResult {
  provider: 'QWEN'
  model: string
  status: 'CONNECTED'
  responsePreview: string
  latencyMs: number
}

export function getAiModelStatus() {
  return getJson<AiModelStatus>('/api/v1/ai/model')
}

export function testAiModelConnection() {
  return requestJson<AiModelTestResult>('/api/v1/ai/model/test', { method: 'POST' })
}
