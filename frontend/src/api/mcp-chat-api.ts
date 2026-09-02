import { requestJson } from './axios-client'

export interface AssistantToolTrace {
  name: string
  status: string
  summary: string
}

export interface AssistantChatResponse {
  role: 'assistant'
  content: string
  toolTraces: AssistantToolTrace[]
  data: Record<string, unknown>
  createdAt: string
}

export function sendMcpChatMessage(message: string) {
  return requestJson<AssistantChatResponse>('/api/v1/mcp/chat', {
    method: 'POST',
    data: { message },
  })
}
