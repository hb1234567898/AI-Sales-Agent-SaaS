import { ArrowRight, CheckCircle, Robot, Sparkle, UserCircle, Wrench } from '@phosphor-icons/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { sendMcpChatMessage, type AssistantToolTrace } from '../api/mcp-chat-api'
import { useIsGuest } from '../auth/use-auth'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  traces?: AssistantToolTrace[]
  createdAt: string
}

const quickPrompts = [
  '查看待审批',
  '查看跟进任务',
  '运行 Agent 分析最近客户',
  '给云岚科技导入聊天：客户说下周想看报价，需要私有化方案。',
]

export function McpAssistantPage() {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: '我是 MCP 自动化助手。你可以直接说“给某客户导入聊天并跑 Agent”，我会自动查客户、导入互动记录、触发客户跟进建议流程。',
      createdAt: new Date().toISOString(),
    },
  ])

  const chatMutation = useMutation({
    mutationFn: sendMcpChatMessage,
    onSuccess: (response) => {
      setMessages((current) => [...current, {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.content,
        traces: response.toolTraces,
        createdAt: response.createdAt,
      }])
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
    chatMutation.mutate(content)
  }

  return (
    <section className="module-page mcp-page">
      <DemoPageHeader
        title="MCP 自动化助手"
        description="用聊天方式调用客户、互动、Agent、审批与跟进工具，减少手动跳页面。"
        actions={<span className="mcp-status"><Sparkle size={14} />工具编排已启用</span>}
      />

      <div className="mcp-layout">
        <section className="surface mcp-chat-panel">
          <div className="mcp-message-list" aria-live="polite">
            {messages.map((message) => (
              <article key={message.id} className={`mcp-message is-${message.role}`}>
                <span className="mcp-avatar" aria-hidden>
                  {message.role === 'user' ? <UserCircle size={20} /> : <Robot size={20} />}
                </span>
                <div className="mcp-bubble">
                  <p>{message.content}</p>
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
                <div className="mcp-bubble"><p>正在调用业务工具…</p></div>
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
            <div><strong>customer.search</strong><span>按客户名匹配客户</span></div>
            <div><strong>interaction.chat_import</strong><span>自动导入粘贴的聊天内容</span></div>
            <div><strong>agent.sales_follow_up.run</strong><span>读取互动记录并生成建议</span></div>
            <div><strong>approval.list / approve</strong><span>查看或显式批准建议</span></div>
            <div><strong>follow_up.list</strong><span>读取开放中的跟进任务</span></div>
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
