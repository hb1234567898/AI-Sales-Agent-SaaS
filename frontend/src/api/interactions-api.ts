import { getJson, requestJson } from './http-client'

export type InteractionType = 'EMAIL_SENT' | 'EMAIL_RECEIVED' | 'EMAIL_OPENED' | 'CALL' | 'MEETING' | 'NOTE' | 'CHAT_IMPORT' | 'TASK_CREATED' | 'TASK_COMPLETED' | 'CRM_UPDATE'
export type InteractionDirection = 'INBOUND' | 'OUTBOUND' | 'NONE'
export type ChatPlatform = 'WECHAT' | 'WHATSAPP' | 'OTHER'

export interface CustomerInteraction {
  id: string
  customerId: string
  type: InteractionType
  direction: InteractionDirection
  occurredAt: string
  subject: string | null
  bodyText: string
  bodyPreview: string
  participants: string[]
  source: string
  createdAt: string
}

export interface InteractionPage {
  content: CustomerInteraction[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface InteractionCreateInput {
  type: 'EMAIL_SENT' | 'EMAIL_RECEIVED' | 'CALL' | 'MEETING' | 'NOTE'
  direction: InteractionDirection
  occurredAt: string
  subject?: string | null
  bodyText: string
  participantName?: string | null
}

export interface ChatImportInput {
  platform: ChatPlatform
  occurredAt: string
  subject?: string | null
  content: string
  participantName?: string | null
}

export function getCustomerInteractions(customerId: string) {
  return getJson<InteractionPage>(`/api/v1/customers/${customerId}/interactions?page=0&size=50`)
}

export function createCustomerInteraction(customerId: string, input: InteractionCreateInput) {
  return requestJson<CustomerInteraction>(`/api/v1/customers/${customerId}/interactions`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function importCustomerChat(customerId: string, input: ChatImportInput) {
  return requestJson<CustomerInteraction>(`/api/v1/customers/${customerId}/interactions/chat-import`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}
