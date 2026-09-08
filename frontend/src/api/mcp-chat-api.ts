import { apiClient, getJson, requestJson } from './axios-client'

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

export type AssistantStreamEvent =
  | { type: 'meta'; data: { conversationId: string } }
  | { type: 'progress' | 'summary' | 'delta'; data: { text: string } }
  | { type: 'tool'; data: AssistantToolTrace }
  | { type: 'result' | 'done'; data: AssistantChatResponse }
  | { type: 'error'; data: { message: string } }

/** POST SSE 复用 Axios 鉴权/刷新；开始接收事件后绝不自动重放业务请求。 */
export async function streamMcpChatMessage(
  input: SendMcpChatMessageInput,
  onEvent: (event: AssistantStreamEvent) => void,
  signal?: AbortSignal,
): Promise<AssistantChatResponse> {
  const controller = new AbortController()
  const abort = () => controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) controller.abort()
  const deadline = window.setTimeout(abort, 660_000)
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  try {
    const response = await apiClient.post<ReadableStream<Uint8Array>>('/api/v1/mcp/chat/stream', {
      ...input,
      channel: window.__TAURI_INTERNALS__ ? 'DESKTOP' : 'WEB',
    }, {
      responseType: 'stream',
      headers: { Accept: 'text/event-stream' },
      timeout: 0,
      signal: controller.signal,
    })
    if (!String(response.headers['content-type']).includes('text/event-stream') || !response.data?.getReader) {
      throw new Error('服务器未返回流式响应，请确认后端已更新。')
    }
    reader = response.data.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let result: AssistantChatResponse | undefined
    let streamError = ''
    while (!result) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      let separator: RegExpExecArray | null
      while ((separator = /\r?\n\r?\n/.exec(buffer))) {
        const frame = buffer.slice(0, separator.index)
        buffer = buffer.slice(separator.index + separator[0].length)
        const lines = frame.split(/\r?\n/)
        const type = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
        const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).replace(/^ /, '')).join('\n')
        if (!type || type === 'ping' || !data) continue
        if (!['meta', 'progress', 'summary', 'delta', 'tool', 'result', 'done', 'error'].includes(type)) continue
        const event = { type, data: JSON.parse(data) } as AssistantStreamEvent
        onEvent(event)
        if (event.type === 'error') streamError = event.data.message
        if (event.type === 'done') { result = event.data; break }
      }
      if (buffer.length > 2_000_000) throw new Error('流式响应过大，请缩小请求范围。')
      if (done) break
    }
    if (!result) throw new Error(streamError || '连接已中断，已收到的内容会保留。请查看会话和业务记录，避免重复执行。')
    return result
  } finally {
    window.clearTimeout(deadline)
    signal?.removeEventListener('abort', abort)
    await reader?.cancel().catch(() => undefined)
    reader?.releaseLock()
  }
}
