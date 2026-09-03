import { getJson, requestJson } from './axios-client'

export interface AssistantToolTrace {
  name: string
  status: string
  summary: string
}

export interface AssistantConversation {
  id: string
  title: string
  channel: string
  status: string
  lastMessageAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AssistantMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  reasoningSummary: string | null
  toolTraces: AssistantToolTrace[]
  data: Record<string, unknown>
  createdAt: string
}

export interface AssistantChatResponse {
  conversationId: string
  messageId: string
  role: 'assistant'
  content: string
  reasoningSummary: string | null
  toolTraces: AssistantToolTrace[]
  data: Record<string, unknown>
  createdAt: string
}

export interface AssistantConversationPage {
  content: AssistantConversation[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AssistantMessagePage {
  content: AssistantMessage[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface SendMcpChatMessageInput {
  conversationId?: string
  message: string
}

export function getMcpConversations() {
  return getJson<AssistantConversationPage>('/api/v1/mcp/conversations?page=0&size=30')
}

export function getMcpMessages(conversationId: string) {
  return getJson<AssistantMessagePage>(`/api/v1/mcp/conversations/${conversationId}/messages?page=0&size=100`)
}

export function sendMcpChatMessage(input: SendMcpChatMessageInput) {
  return requestJson<AssistantChatResponse>('/api/v1/mcp/chat', {
    method: 'POST',
    data: { conversationId: input.conversationId, message: input.message, channel: 'WEB' },
  })
}
