import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamMcpChatMessage } from './mcp-chat-api'
import { saveAuthTokens } from '../auth/auth-token-storage'

afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear() })

const finalResponse = { conversationId: 'c1', messageId: 'm1', content: '你好世界', role: 'assistant', reasoningSummary: '', toolTraces: [], data: {}, createdAt: '2026-09-08T00:00:00Z' }
const frame = (type: string, data: unknown) => `event: ${type}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`

describe('MCP SSE transport', () => {
  it('建立流前 401 刷新 JWT，新 token 用于流式重试', async () => {
    // 未过期 token：主动刷新不触发，401 由响应拦截器被动刷新并重试。
    const tokens = { accessToken: 'old-token', refreshToken: 'refresh-token', accessTokenExpiresAt: '2099-01-01T00:00:00Z', refreshTokenExpiresAt: '2099-01-01T00:00:00Z' }
    saveAuthTokens(tokens, false)
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request
      if (request.url.endsWith('/auth/refresh')) return new Response(JSON.stringify({ ...tokens, accessToken: 'new-token' }), { headers: { 'Content-Type': 'application/json' } })
      if (request.headers.get('Authorization') === 'Bearer old-token') return new Response('{}', { status: 401, headers: { 'Content-Type': 'application/problem+json' } })
      expect(request.headers.get('Authorization')).toBe('Bearer new-token')
      return new Response(frame('done', finalResponse), { headers: { 'Content-Type': 'text/event-stream' } })
    })
    await expect(streamMcpChatMessage({ message: '查看待审批' }, vi.fn())).resolves.toEqual(finalResponse)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('Access Token 已过期时流式请求前主动刷新，一次成功', async () => {
    const tokens = { accessToken: 'old-token', refreshToken: 'refresh-token', accessTokenExpiresAt: '2026-01-01T00:00:00Z', refreshTokenExpiresAt: '2099-01-01T00:00:00Z' }
    saveAuthTokens(tokens, false)
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const request = input as Request
      if (request.url.endsWith('/auth/refresh')) return new Response(JSON.stringify({ ...tokens, accessToken: 'new-token' }), { headers: { 'Content-Type': 'application/json' } })
      expect(request.headers.get('Authorization')).toBe('Bearer new-token')
      return new Response(frame('done', finalResponse), { headers: { 'Content-Type': 'text/event-stream' } })
    })
    await expect(streamMcpChatMessage({ message: '查看待审批' }, vi.fn())).resolves.toEqual(finalResponse)
    // 主动刷新在请求前完成，流式请求直接带新 token 成功，不再走 401 重试。
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('处理跨字节中文、拆开的 CRLF 和同包多事件，done 前已交付增量', async () => {
    let producer!: ReadableStreamDefaultController<Uint8Array>
    const stream = new ReadableStream<Uint8Array>({ start(controller) { producer = controller } })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))
    let resolveDelta!: () => void
    const deltaReceived = new Promise<void>((resolve) => { resolveDelta = resolve })
    const events: string[] = []
    const result = streamMcpChatMessage({ message: '查看待审批' }, (event) => {
      events.push(event.type)
      if (event.type === 'delta') { expect(event.data.text).toBe('你好世界'); resolveDelta() }
    })
    const bytes = new TextEncoder().encode(frame('ping', {}) + frame('delta', { text: '你好世界' }))
    for (const byte of bytes) producer.enqueue(new Uint8Array([byte]))
    await deltaReceived
    expect(events).toEqual(['delta'])
    producer.enqueue(new TextEncoder().encode(frame('done', finalResponse)))
    producer.close()
    await expect(result).resolves.toEqual(finalResponse)
  })

  it('无 done 的断流报错，不自动重放已执行的 POST', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(frame('delta', { text: '部分回答' }), {
      headers: { 'Content-Type': 'text/event-stream' },
    }))
    const onEvent = vi.fn()
    await expect(streamMcpChatMessage({ message: '新增客户：测试' }, onEvent)).rejects.toThrow('连接已中断')
    expect(onEvent).toHaveBeenCalledWith({ type: 'delta', data: { text: '部分回答' } })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('保存失败事件转换为可见错误，不报告成功', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(frame('error', { message: '结果保存失败' }), {
      headers: { 'Content-Type': 'text/event-stream' },
    }))
    await expect(streamMcpChatMessage({ message: '查看待审批' }, vi.fn())).rejects.toThrow('结果保存失败')
  })
})
