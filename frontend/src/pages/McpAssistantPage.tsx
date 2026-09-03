import { ArrowRight, CheckCircle, ClockCounterClockwise, Plus, Robot, Sparkle, UserCircle, Wrench } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
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

export function McpAssistantPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [activeConversationId, setActiveConversationId] = useState<string | undefined>()
  const [messages, setMessages] = useState<ChatMessage[]>([welcomeMessage])

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

  useEffect(() => {
    if (!messagesQuery.data || !activeConversationId) return
    const persistedMessages = messagesQuery.data.content
      .filter((message) => message.role === 'user' || message.role === 'assistant')
      .map(toChatMessage)
    setMessages(persistedMessages.length > 0 ? persistedMessages : [welcomeMessage])
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

  function submit(message = input) {
    const content = message.trim()
    if (!content || chatMutation.isPending || isGuest) return
    setMessages((current) => [...current, {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    }])
    setInput('')
    chatMutation.mutate({ conversationId: activeConversationId, message: content })
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
          <div className="mcp-message-list" aria-live="polite">
            {messagesQuery.isFetching && activeConversationId ? (
              <article className="mcp-message is-assistant">
                <span className="mcp-avatar" aria-hidden><Robot size={20} /></span>
                <div className="mcp-bubble"><p>正在加载这条会话的历史消息…</p></div>
              </article>
            ) : null}
            {messages.map((message) => (
              <article key={message.id} className={`mcp-message is-${message.role}`}>
                <span className="mcp-avatar" aria-hidden>
                  {message.role === 'user' ? <UserCircle size={20} /> : <Robot size={20} />}
                </span>
                <div className="mcp-bubble">
                  <p>{message.content}</p>
                  {message.reasoningSummary ? (
                    <div className="mcp-reasoning">
                      <strong>执行过程</strong>
                      <span>{message.reasoningSummary}</span>
                    </div>
                  ) : null}
                  {message.traces && message.traces.length > 0 ? (
                    <div className="mcp-traces">
                      {message.traces.map((trace) => (
                        <span key={`${message.id}-${trace.name}`}>
                          <CheckCircle size={13} />{trace.name} · {trace.summary}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
              </article>
            ))}
            {chatMutation.isPending ? (
              <article className="mcp-message is-assistant">
                <span className="mcp-avatar" aria-hidden><Robot size={20} /></span>
                <div className="mcp-bubble">
                  <p>正在解析指令并调用业务工具…</p>
                  <div className="mcp-reasoning">
                    <strong>实时进度</strong>
                    <span>识别意图 → 准备工具参数 → 执行业务接口 → 保存结果</span>
                  </div>
                </div>
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
              <div key={guide.tool}>
                <strong>{guide.tool}</strong>
                <span>你可以说：{guide.say}</span>
                <small>结果：{guide.result}</small>
              </div>
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

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
