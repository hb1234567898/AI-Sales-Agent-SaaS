import { getJson, requestJson } from './http-client'

export type AiModelStatusCode = 'READY' | 'MISSING_API_KEY' | 'ENCRYPTION_KEY_UNAVAILABLE'

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

export interface AiModelUpdateInput {
  provider: 'QWEN'
  model: string
  baseUrl: string
  apiKey?: string
}

export function getAiModelStatus() {
  return getJson<AiModelStatus>('/api/v1/ai/model')
}

export function saveAiModelConfiguration(input: AiModelUpdateInput) {
  return requestJson<AiModelStatus>('/api/v1/ai/model', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function testAiModelConnection() {
  return requestJson<AiModelTestResult>('/api/v1/ai/model/test', { method: 'POST' })
}
