import { CheckCircle, CircleNotch, DotsThree, MagicWand, PaperPlaneTilt, Paperclip, PlusSquare, Robot, Sparkle, SquaresFour, UserCircle, WarningCircle, Wrench } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type CSSProperties, useEffect, useMemo, useRef, useState } from 'react'
import {
  getMcpConversations,
  getMcpMessages,
  streamMcpChatMessage,
  type SendMcpChatMessageInput,
  type AssistantMessage,
  type AssistantToolTrace,
} from '../api/mcp-chat-api'
import { getAiModelStatus, type AiModelStatus } from '../api/ai-settings-api'
import { useIsGuest } from '../auth/use-auth'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoningSummary?: string | null
  traces?: AssistantToolTrace[]
  createdAt: string
  streaming?: boolean
  progress?: string
  error?: string
}

const quickPrompts = [
  '新增客户：沐光医疗，行业：医疗科技，联系人：苏恬，电话：13800000007，邮箱：su@example.com',
  '查看待审批',
  '查看跟进任务',
  '运行 Agent 分析最近客户',
  '新增客户云岚科技并导入聊天：客户说下周想看报价，需要私有化方案。',
]

const assistantTabs = ['AI对话', '助手列表', '技能管理', '额度管理', '设置']

const toolGuides = [
  {
    tool: 'customer.create',
    say: '新增客户：沐光医疗，行业：医疗科技，联系人：苏恬，电话：13800000007',
    result: '创建客户主档和主要联系人',
  },
  {
    tool: 'interaction.chat_import + agent.sales_follow_up.run',
    say: '给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。',
    result: '保存聊天记录，并只针对该客户运行跟进建议 Agent',
  },
  {
    tool: 'approval.list / approval.approve',
    say: '查看待审批；批准 <审批ID>',
    result: '查询或批准 Agent 生成的待审批建议',
  },
  {
    tool: 'follow_up.list',
    say: '查看跟进任务',
    result: '读取开放中的客户跟进任务',
  },
]

const welcomeMessage: ChatMessage = {
  id: 'welcome',
  role: 'assistant',
  content: '我是 MCP 自动化助手。你可以直接说“新增客户”“给某客户导入聊天并跑 Agent”，我会自动调用客户、互动、Agent、审批和跟进工具。聊天记录现在会保存到数据库，刷新页面也能找回来。',
  reasoningSummary: '等待用户输入业务指令。',
  createdAt: new Date().toISOString(),
}

export function McpAssistantPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [activeConversationId, setActiveConversationId] = useState<string | undefined>()
  const [messages, setMessages] = useState<ChatMessage[]>([welcomeMessage])
  const [useLocalMessages, setUseLocalMessages] = useState(true)
  const pendingId = useRef('')
  const pendingConversationId = useRef<string | undefined>(undefined)
  const busy = useRef(false)
  const streamController = useRef<AbortController | null>(null)
  const messageListRef = useRef<HTMLDivElement | null>(null)
  const composerRef = useRef<HTMLTextAreaElement | null>(null)

  const conversationsQuery = useQuery({
    queryKey: ['mcp-conversations'],
    queryFn: getMcpConversations,
    enabled: !isGuest,
  })

  const messagesQuery = useQuery({
    queryKey: ['mcp-messages', activeConversationId],
    queryFn: () => getMcpMessages(activeConversationId!),
    enabled: !isGuest && Boolean(activeConversationId),
  })
  const modelQuery = useQuery({
    queryKey: ['ai-model-status'],
    queryFn: getAiModelStatus,
    enabled: !isGuest,
  })

  const conversations = conversationsQuery.data?.content ?? []
  const activeConversation = conversations.find((conversation) => conversation.id === activeConversationId)

  const displayedMessages = useMemo(() => useLocalMessages ? messages : (messagesQuery.data?.content ?? [])
      .filter((message) => message.role === 'user' || message.role === 'assistant')
      .map(toChatMessage), [useLocalMessages, messages, messagesQuery.data])
  const chatMessages = displayedMessages.filter((message) => message.id !== 'welcome')

  const chatMutation = useMutation({
    retry: false,
    mutationFn: (request: SendMcpChatMessageInput) => streamMcpChatMessage(request, (event) => {
      if (event.type === 'meta') {
        pendingConversationId.current = event.data.conversationId
        return
      }
      setMessages((current) => current.map((message) => {
        if (message.id !== pendingId.current) return message
        if (event.type === 'delta') return { ...message, content: message.content + event.data.text }
        if (event.type === 'progress') return { ...message, progress: event.data.text }
        if (event.type === 'summary') return { ...message, reasoningSummary: event.data.text }
        if (event.type === 'tool') return { ...message, traces: [...(message.traces ?? []).filter((trace) => trace.name !== event.data.name), event.data] }
        if (event.type === 'error') return { ...message, error: event.data.message }
        return message
      }))
    }, streamController.current?.signal),
    onSuccess: (response) => {
      setActiveConversationId(response.conversationId)
      setMessages((current) => current.map((message) => message.id !== pendingId.current ? message : {
        id: response.messageId,
        role: 'assistant',
        content: response.content,
        reasoningSummary: response.reasoningSummary,
        traces: response.toolTraces,
        createdAt: response.createdAt,
        error: message.error,
      }))
      void queryClient.invalidateQueries({ queryKey: ['mcp-conversations'] })
      void queryClient.invalidateQueries({ queryKey: ['mcp-messages', response.conversationId] })
      void queryClient.invalidateQueries({ queryKey: ['agent-runs'] })
      void queryClient.invalidateQueries({ queryKey: ['approvals'] })
      void queryClient.invalidateQueries({ queryKey: ['follow-ups'] })
      void queryClient.invalidateQueries({ queryKey: ['customers'] })
      void queryClient.invalidateQueries({ queryKey: ['ai-model-status'] })
    },
    onError: (error) => {
      setMessages((current) => current.map((message) => message.id !== pendingId.current ? message : {
        ...message,
        streaming: false,
        error: error instanceof Error ? error.message : '连接中断，请查看历史记录确认执行结果。',
      }))
      void queryClient.invalidateQueries({ queryKey: ['mcp-conversations'] })
    },
    onSettled: () => {
      busy.current = false
      streamController.current = null
    },
  })

  useEffect(() => () => streamController.current?.abort(), [])

  useEffect(() => {
    const messageList = messageListRef.current
    if (!messageList || typeof messageList.scrollTo !== 'function') return
    messageList.scrollTo({
      top: messageList.scrollHeight,
      behavior: 'smooth',
    })
  }, [displayedMessages, chatMutation.isPending])

  function submit(message = input) {
    const content = message.trim()
    if (!content || busy.current || isGuest) return
    busy.current = true
    streamController.current = new AbortController()
    pendingId.current = crypto.randomUUID()
    setUseLocalMessages(true)
    setMessages([...displayedMessages, {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    }, {
      id: pendingId.current,
      role: 'assistant',
      content: '',
      streaming: true,
      progress: '正在连接助手…',
      createdAt: new Date().toISOString(),
    }])
    setInput('')
    chatMutation.mutate({ conversationId: activeConversationId ?? pendingConversationId.current, message: content })
  }

  function applyToolTemplate(template: string) {
    if (chatMutation.isPending) return
    setInput(template)
    window.setTimeout(() => {
      composerRef.current?.focus()
      composerRef.current?.setSelectionRange(template.length, template.length)
    }, 0)
  }

  function startNewConversation() {
    setActiveConversationId(undefined)
    setUseLocalMessages(true)
    pendingConversationId.current = undefined
    setMessages([welcomeMessage])
    setInput('')
  }

  const readableStatus = chatMutation.isPending ? '正在执行' : activeConversation ? '历史已保存' : '新会话'

  return (
    <section className="mcp-page">
      <nav className="mcp-top-tabs" aria-label="MCP 助手导航">
        {assistantTabs.map((tab) => (
          <button key={tab} type="button" className={tab === 'AI对话' ? 'is-active' : ''} aria-current={tab === 'AI对话' ? 'page' : undefined}>
            {tab}
          </button>
        ))}
        <span className="mcp-edition-badge">企业版</span>
      </nav>

      <div className="mcp-layout">
        <aside className="mcp-history-panel">
          <header>
            <h2>会话列表</h2>
            <button type="button" onClick={startNewConversation} disabled={chatMutation.isPending || isGuest}>
              <PlusSquare size={16} />
              <span>新建</span>
            </button>
          </header>
          {isGuest ? (
            <p className="mcp-history-empty">游客模式不会保存自动化聊天，登录后可使用会话历史。</p>
          ) : conversations.length === 0 ? (
            <div className="mcp-empty-history">
              <Robot size={52} />
              <p>{conversationsQuery.isLoading ? '正在读取历史会话…' : '暂无可用会话'}</p>
            </div>
          ) : (
            <div className="mcp-history-list">
              {conversations.map((conversation) => (
                <button
                  key={conversation.id}
                  type="button"
                  className={conversation.id === activeConversationId ? 'is-active' : ''}
                  disabled={chatMutation.isPending}
                  onClick={() => { setActiveConversationId(conversation.id); setUseLocalMessages(false) }}
                >
                  <strong>{conversation.title}</strong>
                  <span>{formatTime(conversation.lastMessageAt ?? conversation.createdAt)} · {conversation.channel}</span>
                </button>
              ))}
            </div>
          )}
          <ModelInfoPanel status={modelQuery.data} loading={modelQuery.isLoading} />
        </aside>

        <section className="mcp-chat-panel">
          <header className="mcp-chat-header">
            <h1><SquaresFour size={18} />AI助手</h1>
            <span className="mcp-status"><Sparkle size={14} />{readableStatus}</span>
          </header>
          <div ref={messageListRef} className="mcp-message-list" aria-live="polite">
            {messagesQuery.isFetching && activeConversationId ? (
              <article className="mcp-message is-assistant">
                <span className="mcp-avatar" aria-hidden><Robot size={20} /></span>
                <div className="mcp-bubble">
                  <ThinkingSkeleton title="正在加载历史消息" />
                </div>
              </article>
            ) : null}
            {chatMessages.length === 0 && !messagesQuery.isFetching ? (
              <div className="mcp-empty-chat">
                <h2>有什么我能帮你的吗?</h2>
                <div className="mcp-empty-prompts" aria-label="快捷指令">
                  {quickPrompts.map((prompt) => (
                    <button key={prompt} type="button" disabled={isGuest || chatMutation.isPending} onClick={() => submit(prompt)}>
                      {prompt}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
            {chatMessages.map((message) => (
              <article key={message.id} className={`mcp-message is-${message.role}`}>
                <span className="mcp-avatar" aria-hidden>
                  {message.role === 'user' ? <UserCircle size={20} /> : <Robot size={20} />}
                </span>
                <div className="mcp-bubble">
                  {message.role === 'assistant' ? (
                    <AssistantOutput message={message} />
                  ) : (
                    <p>{message.content}</p>
                  )}
                </div>
              </article>
            ))}
          </div>

          <form
            className="mcp-composer"
            onSubmit={(event) => {
              event.preventDefault()
              submit()
            }}
          >
            <div className="mcp-tool-capsules" aria-label="可调用工具">
              {toolGuides.map((guide) => (
                <button
                  key={guide.tool}
                  type="button"
                  disabled={chatMutation.isPending}
                  aria-label={`套用 ${guide.tool} 工具模板`}
                  title={`${guide.tool}：${guide.result}`}
                  onClick={() => applyToolTemplate(guide.say)}
                >
                  <Wrench size={13} />
                  <span>{guide.tool}</span>
                </button>
              ))}
            </div>
            <textarea
              ref={composerRef}
              value={input}
              disabled={isGuest}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
                event.preventDefault()
                submit()
              }}
              placeholder={isGuest ? '游客模式不能执行自动化操作' : '例如：给云岚科技导入聊天。输入消息，按 Shift + Enter 换行，按 Enter 发送'}
              rows={2}
            />
            <div className="mcp-composer-footer">
              <div className="mcp-composer-tools">
                <button type="button" aria-label="上传附件" title="聊天附件稍后接入" disabled>
                  <Paperclip size={18} />
                </button>
                <span><Sparkle size={15} />{modelQuery.data?.model ?? 'qwen3.5-plus'}</span>
                <span><MagicWand size={15} />智能模式</span>
              </div>
              <button className="mcp-send-button" type="submit" aria-label="发送指令" disabled={isGuest || chatMutation.isPending || !input.trim()}>
                <PaperPlaneTilt size={21} weight="fill" />
              </button>
            </div>
          </form>
        </section>
      </div>
    </section>
  )
}

function ModelInfoPanel({ status, loading }: { status?: AiModelStatus; loading: boolean }) {
  const usage = status?.usage
  const totalTokens = usage?.totalTokens ?? 0
  const remainingTokens = usage?.remainingTokens ?? null
  const quotaTotal = remainingTokens == null ? null : totalTokens + remainingTokens
  return (
    <section className="mcp-model-panel" aria-label="资源包额度">
      <header>
        <strong>资源包额度</strong>
        <span>{remainingTokens == null ? '额度未知' : `剩${formatCompactNumber(remainingTokens)} Token`}</span>
      </header>
      {loading ? (
        <div className="mcp-model-loading"><span /><span /><span /></div>
      ) : (
        <>
          <TokenProgress label="累计已用" value={totalTokens} total={quotaTotal ?? totalTokens} tone="blue" />
          <div className="mcp-model-meta">
            <span>{status?.model ?? '-'}</span>
            <span>{modelStatusLabel(status?.status)}</span>
          </div>
          {quotaTotal == null ? (
            <p className="mcp-quota-note">剩余额度：千问聊天接口未返回账户余额</p>
          ) : null}
          <button type="button" className="mcp-quota-button">额度管理</button>
          {usage?.lastCalledAt ? <p className="mcp-model-time">最近调用：{formatTime(usage.lastCalledAt)}</p> : null}
        </>
      )}
    </section>
  )
}

function TokenProgress({ label, value, total, tone = 'blue' }: { label: string; value: number; total: number; tone?: 'blue' | 'green' | 'amber' }) {
  const percent = percentage(value, total)
  return (
    <div className={`mcp-token-progress is-${tone}`}>
      <div><span>{label}</span><strong>{percent}%</strong></div>
      <progress value={percent} max={100} aria-label={`${label} ${percent}%`} />
      <span style={{ '--progress': `${percent}%` } as CSSProperties} aria-hidden />
      <small>{formatCompactNumber(value)} / {formatCompactNumber(total)}</small>
    </div>
  )
}

function toChatMessage(message: AssistantMessage): ChatMessage {
  return {
    id: message.id,
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content,
    reasoningSummary: message.reasoningSummary,
    traces: message.toolTraces,
    createdAt: message.createdAt,
  }
}

function AssistantOutput({ message }: { message: ChatMessage }) {
  const traces = message.traces ?? []
  return (
    <div className="mcp-assistant-output">
      {message.streaming ? (
        <div className="mcp-pending-head" aria-label="Agent 执行进度">
          <CircleNotch size={18} className="mcp-spin" />
          <div><strong>Agent 正在处理</strong><span>{message.progress}</span></div>
        </div>
      ) : null}
      <section className="mcp-output-card">
        <header><Sparkle size={14} />结果输出</header>
        <p>{message.content}{message.streaming && message.content ? <span className="mcp-stream-cursor" aria-hidden>▍</span> : null}</p>
      </section>
      {message.error ? <p role="alert" className="mcp-stream-error"><WarningCircle size={15} />{message.error}</p> : null}

      {message.reasoningSummary ? (
        <section className="mcp-reasoning">
          <strong><DotsThree size={15} />执行过程</strong>
          <span>{message.reasoningSummary}</span>
        </section>
      ) : null}

      {traces.length > 0 ? <ToolTraceList traces={traces} /> : null}
    </div>
  )
}

function ThinkingSkeleton({ title }: { title: string }) {
  return (
    <div className="mcp-thinking-skeleton" aria-label={title}>
      <strong>{title}<i /><i /><i /></strong>
      <span />
      <span />
      <span />
    </div>
  )
}

function ToolTraceList({ traces }: { traces: AssistantToolTrace[] }) {
  return (
    <section className="mcp-traces">
      <strong>工具轨迹</strong>
      {traces.map((trace) => {
        const failed = trace.status.toUpperCase() === 'FAILED'
        const running = trace.status.toUpperCase() === 'RUNNING'
        return (
          <span key={`${trace.name}-${trace.summary}`} className={failed ? 'is-failed' : 'is-success'}>
            {running ? <CircleNotch size={13} className="mcp-spin" /> : failed ? <WarningCircle size={13} /> : <CheckCircle size={13} />}
            <b>{trace.name}</b>
            <em>{readableTraceStatus(trace.status)}</em>
            <small>{trace.summary}</small>
          </span>
        )
      })}
    </section>
  )
}

function readableTraceStatus(status: string) {
  const normalized = status.toUpperCase()
  if (normalized === 'SUCCEEDED') return '成功'
  if (normalized === 'FAILED') return '失败'
  if (normalized === 'SKIPPED') return '跳过'
  if (normalized === 'RUNNING') return '执行中'
  return status
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function modelStatusLabel(status?: string) {
  if (status === 'READY') return '可用'
  if (status === 'MISSING_API_KEY') return '未配置 Key'
  if (status === 'ENCRYPTION_KEY_UNAVAILABLE') return '密钥不可解密'
  return status ?? '-'
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

function percentage(value: number, total: number) {
  if (!Number.isFinite(value) || !Number.isFinite(total) || total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((value / total) * 100)))
}
