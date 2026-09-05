import { ArrowRight, CheckCircle, ClockCounterClockwise, CircleNotch, DotsThree, Plus, Robot, Sparkle, UserCircle, WarningCircle, Wrench } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  getMcpConversations,
  getMcpMessages,
  sendMcpChatMessage,
  type AssistantMessage,
  type AssistantToolTrace,
} from '../api/mcp-chat-api'
import { useIsGuest } from '../auth/use-auth'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoningSummary?: string | null
  traces?: AssistantToolTrace[]
  createdAt: string
}

const quickPrompts = [
  '新增客户：沐光医疗，行业：医疗科技，联系人：苏恬，电话：13800000007，邮箱：su@example.com',
  '查看待审批',
  '查看跟进任务',
  '运行 Agent 分析最近客户',
  '新增客户云岚科技并导入聊天：客户说下周想看报价，需要私有化方案。',
]

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

const pendingSteps = [
  { title: '理解业务意图', detail: '识别你想操作的对象、动作和约束条件。' },
  { title: '规划工具调用', detail: '选择客户、互动、Agent、审批或跟进工具，并整理参数。' },
  { title: '执行并校验结果', detail: '调用后端业务接口，检查返回状态和异常信息。' },
  { title: '整理输出', detail: '把执行结果、下一步建议和工具轨迹写回会话。' },
]

export function McpAssistantPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [activeConversationId, setActiveConversationId] = useState<string | undefined>()
  const [messages, setMessages] = useState<ChatMessage[]>([welcomeMessage])
  const [pendingStepIndex, setPendingStepIndex] = useState(0)
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

  const conversations = conversationsQuery.data?.content ?? []
  const activeConversation = conversations.find((conversation) => conversation.id === activeConversationId)
  const pendingReasoning = useMemo(() => pendingSteps[pendingStepIndex] ?? pendingSteps[pendingSteps.length - 1], [pendingStepIndex])

  useEffect(() => {
    if (!messagesQuery.data || !activeConversationId) return
    const persistedMessages = messagesQuery.data.content
      .filter((message) => message.role === 'user' || message.role === 'assistant')
      .map(toChatMessage)
    if (persistedMessages.length > 0) {
      setMessages(persistedMessages)
    }
  }, [activeConversationId, messagesQuery.data])

  const chatMutation = useMutation({
    mutationFn: sendMcpChatMessage,
    onSuccess: (response) => {
      setActiveConversationId(response.conversationId)
      setMessages((current) => [...current, {
        id: response.messageId,
        role: 'assistant',
        content: response.content,
        reasoningSummary: response.reasoningSummary,
        traces: response.toolTraces,
        createdAt: response.createdAt,
      }])
      void queryClient.invalidateQueries({ queryKey: ['mcp-conversations'] })
      void queryClient.invalidateQueries({ queryKey: ['mcp-messages', response.conversationId] })
      void queryClient.invalidateQueries({ queryKey: ['agent-runs'] })
      void queryClient.invalidateQueries({ queryKey: ['approvals'] })
      void queryClient.invalidateQueries({ queryKey: ['follow-ups'] })
      void queryClient.invalidateQueries({ queryKey: ['customers'] })
    },
    onError: (error) => {
      setMessages((current) => [...current, {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: error instanceof Error ? error.message : '自动化请求失败，请稍后重试。',
        createdAt: new Date().toISOString(),
      }])
    },
  })

  useEffect(() => {
    if (!chatMutation.isPending) return
    const timer = window.setInterval(() => {
      setPendingStepIndex((current) => Math.min(current + 1, pendingSteps.length - 1))
    }, 1200)
    return () => window.clearInterval(timer)
  }, [chatMutation.isPending])

  useEffect(() => {
    const messageList = messageListRef.current
    if (!messageList || typeof messageList.scrollTo !== 'function') return
    messageList.scrollTo({
      top: messageList.scrollHeight,
      behavior: 'smooth',
    })
  }, [messages, chatMutation.isPending, pendingStepIndex])

  function submit(message = input) {
    const content = message.trim()
    if (!content || chatMutation.isPending || isGuest) return
    setMessages((current) => [...current, {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    }])
    setPendingStepIndex(0)
    setInput('')
    chatMutation.mutate({ conversationId: activeConversationId, message: content })
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
    setMessages([welcomeMessage])
    setInput('')
  }

  const readableStatus = chatMutation.isPending ? '正在执行' : activeConversation ? '历史已保存' : '新会话'

  return (
    <section className="module-page mcp-page">
      <DemoPageHeader
        title="MCP 自动化助手"
        description="用聊天方式调用客户、互动、Agent、审批与跟进工具；会话历史会保存，后续桌面端也能复用。"
        actions={<span className="mcp-status"><Sparkle size={14} />{readableStatus}</span>}
      />

      <div className="mcp-layout">
        <aside className="surface mcp-history-panel">
          <header>
            <h2><ClockCounterClockwise size={18} />会话记录</h2>
            <button type="button" onClick={startNewConversation} disabled={chatMutation.isPending || isGuest}>
              <Plus size={14} />新建
            </button>
          </header>
          {isGuest ? (
            <p className="mcp-history-empty">游客模式不会保存自动化聊天，登录后可使用会话历史。</p>
          ) : conversations.length === 0 ? (
            <p className="mcp-history-empty">{conversationsQuery.isLoading ? '正在读取历史会话…' : '暂无历史会话，发送第一条指令后会自动保存。'}</p>
          ) : (
            <div className="mcp-history-list">
              {conversations.map((conversation) => (
                <button
                  key={conversation.id}
                  type="button"
                  className={conversation.id === activeConversationId ? 'is-active' : ''}
                  onClick={() => setActiveConversationId(conversation.id)}
                >
                  <strong>{conversation.title}</strong>
                  <span>{formatTime(conversation.lastMessageAt ?? conversation.createdAt)} · {conversation.channel}</span>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="surface mcp-chat-panel">
          <div ref={messageListRef} className="mcp-message-list" aria-live="polite">
            {messagesQuery.isFetching && activeConversationId ? (
              <article className="mcp-message is-assistant">
                <span className="mcp-avatar" aria-hidden><Robot size={20} /></span>
                <div className="mcp-bubble">
                  <ThinkingSkeleton title="正在加载历史消息" />
                </div>
              </article>
            ) : null}
            {messages.map((message) => (
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
            {chatMutation.isPending ? (
              <article className="mcp-message is-assistant">
                <span className="mcp-avatar" aria-hidden><Robot size={20} /></span>
                <PendingAssistantBubble activeIndex={pendingStepIndex} activeStep={pendingReasoning} />
              </article>
            ) : null}
          </div>

          <form
            className="mcp-composer"
            onSubmit={(event) => {
              event.preventDefault()
              submit()
            }}
          >
            <textarea
              ref={composerRef}
              value={input}
              disabled={isGuest}
              onChange={(event) => setInput(event.target.value)}
              placeholder={isGuest ? '游客模式不能执行自动化操作' : '例如：给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。'}
              rows={4}
            />
            <button className="button button-primary" type="submit" disabled={isGuest || chatMutation.isPending || !input.trim()}>
              发送指令 <ArrowRight size={15} />
            </button>
          </form>
        </section>

        <aside className="surface mcp-side-panel">
          <h2><Wrench size={18} />可调用工具</h2>
          <div className="mcp-tool-list">
            {toolGuides.map((guide) => (
              <button
                key={guide.tool}
                type="button"
                disabled={chatMutation.isPending}
                aria-label={`套用 ${guide.tool} 工具模板`}
                onClick={() => applyToolTemplate(guide.say)}
              >
                <strong>{guide.tool}</strong>
                <span>你可以说：{guide.say}</span>
                <small>结果：{guide.result}</small>
                <em>点击套用模板</em>
              </button>
            ))}
          </div>

          <h2>快捷指令</h2>
          <div className="mcp-quick-list">
            {quickPrompts.map((prompt) => (
              <button key={prompt} type="button" disabled={isGuest || chatMutation.isPending} onClick={() => submit(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </aside>
      </div>
    </section>
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
      <section className="mcp-output-card">
        <header><Sparkle size={14} />结果输出</header>
        <p>{message.content}</p>
      </section>

      {message.reasoningSummary ? (
        <section className="mcp-reasoning">
          <strong><DotsThree size={15} />思考摘要</strong>
          <span>{message.reasoningSummary}</span>
        </section>
      ) : null}

      {traces.length > 0 ? <ToolTraceList traces={traces} /> : null}
    </div>
  )
}

function PendingAssistantBubble({ activeIndex, activeStep }: { activeIndex: number; activeStep: typeof pendingSteps[number] }) {
  return (
    <div className="mcp-bubble mcp-bubble-pending">
      <div className="mcp-pending-head">
        <CircleNotch size={18} className="mcp-spin" />
        <div>
          <strong>Agent 正在处理</strong>
          <span>{activeStep.title} · {activeStep.detail}</span>
        </div>
      </div>
      <div className="mcp-thinking-steps" aria-label="Agent 执行进度">
        {pendingSteps.map((step, index) => (
          <div
            key={step.title}
            className={[
              'mcp-thinking-step',
              index < activeIndex ? 'is-done' : '',
              index === activeIndex ? 'is-active' : '',
            ].filter(Boolean).join(' ')}
          >
            <span>{index < activeIndex ? <CheckCircle size={14} /> : index === activeIndex ? <CircleNotch size={14} className="mcp-spin" /> : index + 1}</span>
            <div>
              <strong>{step.title}</strong>
              <small>{step.detail}</small>
            </div>
          </div>
        ))}
      </div>
      <ThinkingSkeleton title="正在等待工具返回" />
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
        return (
          <span key={`${trace.name}-${trace.summary}`} className={failed ? 'is-failed' : 'is-success'}>
            {failed ? <WarningCircle size={13} /> : <CheckCircle size={13} />}
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
