import { Check, CheckCircle, CircleNotch, DotsThree, EnvelopeSimple, MagicWand, PaperPlaneTilt, Paperclip, PlusSquare, Robot, ShieldCheck, Sparkle, SquaresFour, UserCircle, WarningCircle, Wrench, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, type CSSProperties, useEffect, useMemo, useRef, useState } from 'react'
import {
  getMcpConversations,
  getMcpMessages,
  streamMcpChatMessage,
  type SendMcpChatMessageInput,
  type AssistantMessage,
  type AssistantToolTrace,
} from '../api/mcp-chat-api'
import { getAiModelStatus, type AiModelStatus } from '../api/ai-settings-api'
import { uploadFile, type UploadedFile } from '../api/files-api'
import { approveApproval, getApproval, rejectApproval, type Approval } from '../api/approvals-api'
import { getMembers, updateMemberTokenQuota, type AdminMember } from '../api/admin-api'
import { useAuth, useIsGuest } from '../auth/use-auth'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoningSummary?: string | null
  traces?: AssistantToolTrace[]
  attachments?: UploadedFile[]
  data?: Record<string, unknown>
  createdAt: string
  streaming?: boolean
  progress?: string
  error?: string
}

interface ComposerAttachment {
  id: string
  file: File
  filename: string
  sizeBytes: number
  uploaded?: UploadedFile
}

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
  const session = useAuth()
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [activeConversationId, setActiveConversationId] = useState<string | undefined>()
  const [messages, setMessages] = useState<ChatMessage[]>([welcomeMessage])
  const [useLocalMessages, setUseLocalMessages] = useState(true)
  const [attachments, setAttachments] = useState<ComposerAttachment[]>([])
  const [attachmentError, setAttachmentError] = useState('')
  const [uploadingAttachments, setUploadingAttachments] = useState(false)
  const [tokenAllocationOpen, setTokenAllocationOpen] = useState(false)
  const pendingId = useRef('')
  const pendingConversationId = useRef<string | undefined>(undefined)
  const busy = useRef(false)
  const streamController = useRef<AbortController | null>(null)
  const messageListRef = useRef<HTMLDivElement | null>(null)
  const composerRef = useRef<HTMLTextAreaElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

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
  const quotaUnavailable = !isGuest && !modelQuery.isLoading && (
    modelQuery.data?.usage?.memberAllocatedTokens == null
      || (modelQuery.data.usage.memberRemainingTokens ?? 0) <= 0
  )
  const mcpDisabled = isGuest || quotaUnavailable
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
      setAttachments([])
      setMessages((current) => current.map((message) => message.id !== pendingId.current ? message : {
        id: response.messageId,
        role: 'assistant',
        content: response.content,
        reasoningSummary: response.reasoningSummary,
        traces: response.toolTraces,
        data: response.data,
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

  function handleAttachmentChange(event: ChangeEvent<HTMLInputElement>) {
    const selectedFiles = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (!selectedFiles.length || chatMutation.isPending || uploadingAttachments) return
    setAttachmentError('')
    if (attachments.length + selectedFiles.length > 5) {
      setAttachmentError('一次消息最多携带 5 个附件')
      return
    }
    const oversized = selectedFiles.find((file) => file.size > 10 * 1024 * 1024)
    if (oversized) {
      setAttachmentError(`${oversized.name} 超过 10MB，请压缩后再上传`)
      return
    }
    setAttachments((current) => [...current, ...selectedFiles.map((file) => ({
      id: crypto.randomUUID(),
      file,
      filename: file.name,
      sizeBytes: file.size,
    }))])
  }

  function removeAttachment(fileId: string) {
    if (chatMutation.isPending || uploadingAttachments) return
    setAttachments((current) => current.filter((file) => file.id !== fileId))
  }

  async function submit(message = input) {
    const content = message.trim()
    if (!content || busy.current || mcpDisabled || uploadingAttachments) return
    busy.current = true
    setAttachmentError('')
    setUploadingAttachments(true)
    let submittedAttachments: UploadedFile[]
    try {
      submittedAttachments = await Promise.all(attachments.map((attachment) => (
        attachment.uploaded ?? uploadFile(attachment.file)
      )))
      setAttachments((current) => current.map((attachment, index) => ({
        ...attachment,
        uploaded: submittedAttachments[index] ?? attachment.uploaded,
      })))
    } catch (error) {
      setAttachmentError(error instanceof Error ? error.message : '附件上传失败，请重试')
      busy.current = false
      setUploadingAttachments(false)
      return
    }
    setUploadingAttachments(false)
    streamController.current = new AbortController()
    pendingId.current = crypto.randomUUID()
    setUseLocalMessages(true)
    setMessages([...displayedMessages, {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      attachments: submittedAttachments,
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
    setAttachmentError('')
    chatMutation.mutate({
      conversationId: activeConversationId ?? pendingConversationId.current,
      message: content,
      attachmentIds: submittedAttachments.map((file) => file.id),
    })
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
    setAttachments([])
    setAttachmentError('')
  }

  const readableStatus = chatMutation.isPending ? '正在执行' : activeConversation ? '历史已保存' : '新会话'

  return (
    <section className="mcp-page">
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
          <ModelInfoPanel
            status={modelQuery.data}
            loading={modelQuery.isLoading}
            canAllocate={session.role === 'OWNER' || session.role === 'ADMIN'}
            onAllocate={() => setTokenAllocationOpen(true)}
          />
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
                <h2>销售自动化助手</h2>
                <p className="mcp-empty-intro">
                  把客户资料、聊天记录、跟进建议和审批动作放在同一个工作流里，帮助销售团队更快整理线索、沉淀过程，并把需要人工确认的动作留在可追踪的记录中。
                </p>
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
                    <>
                      <p>{message.content}</p>
                      <MessageAttachmentList attachments={message.attachments ?? []} />
                    </>
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
                  disabled={mcpDisabled || chatMutation.isPending}
                  aria-label={`套用 ${guide.tool} 工具模板`}
                  title={`${guide.tool}：${guide.result}`}
                  onClick={() => applyToolTemplate(guide.say)}
                >
                  <Wrench size={13} />
                  <span>{guide.tool}</span>
                </button>
              ))}
            </div>
            {attachments.length ? (
              <div className="mcp-attachment-list" aria-label="已选择附件">
                {attachments.map((file) => (
                  <span key={file.id} title={file.filename}>
                    <Paperclip size={13} />
                    <b>{file.filename}</b>
                    <small>{formatFileSize(file.sizeBytes)}</small>
                    <button type="button" aria-label={`移除附件 ${file.filename}`} disabled={chatMutation.isPending || uploadingAttachments} onClick={() => removeAttachment(file.id)}>
                      <X size={12} />
                    </button>
                  </span>
                ))}
              </div>
            ) : null}
            {attachmentError ? <p className="mcp-attachment-error" role="alert">{attachmentError}</p> : null}
            <textarea
              ref={composerRef}
              value={input}
              disabled={mcpDisabled || uploadingAttachments}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
                event.preventDefault()
                submit()
              }}
              placeholder={isGuest ? '游客模式不能执行自动化操作' : quotaUnavailable ? '尚未分配 Token 额度，请联系管理员' : '例如：给云岚科技导入聊天。输入消息，按 Shift + Enter 换行，按 Enter 发送'}
              rows={2}
            />
            <div className="mcp-composer-footer">
              <div className="mcp-composer-tools">
                <input
                  ref={fileInputRef}
                  className="mcp-file-input"
                  type="file"
                  multiple
                  disabled={mcpDisabled}
                  aria-label="选择 MCP 聊天附件"
                  onChange={handleAttachmentChange}
                />
                <button
                  type="button"
                  aria-label="选择附件"
                  title="选择报价、方案、合同等附件，发送消息时才会上传"
                  disabled={mcpDisabled || chatMutation.isPending || uploadingAttachments || attachments.length >= 5}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Paperclip size={18} />
                </button>
                <span><Sparkle size={15} />{modelQuery.data?.model ?? 'qwen3.5-plus'}</span>
                <span><MagicWand size={15} />智能模式</span>
                {uploadingAttachments ? <span><CircleNotch size={15} className="mcp-spin" />正在上传附件</span> : null}
              </div>
              <button className="mcp-send-button" type="submit" aria-label="发送指令" disabled={mcpDisabled || chatMutation.isPending || uploadingAttachments || !input.trim()}>
                <PaperPlaneTilt size={21} weight="fill" />
              </button>
            </div>
          </form>
        </section>
      </div>
      {tokenAllocationOpen ? <TokenAllocationDialog onClose={() => setTokenAllocationOpen(false)} /> : null}
    </section>
  )
}

function ModelInfoPanel({ status, loading, canAllocate, onAllocate }: { status?: AiModelStatus; loading: boolean; canAllocate: boolean; onAllocate: () => void }) {
  const usage = status?.usage
  const totalTokens = usage?.totalTokens ?? 0
  const memberUsedTokens = usage?.memberUsedTokens ?? 0
  const memberAllocatedTokens = usage?.memberAllocatedTokens ?? null
  return (
    <section className="mcp-model-panel" aria-label="用量统计">
      <header>
        <strong>用量统计</strong>
        <span>{memberAllocatedTokens == null ? '未分配额度' : `剩 ${formatCompactNumber(usage?.memberRemainingTokens ?? 0)}`}</span>
      </header>
      {loading ? (
        <div className="mcp-model-loading"><span /><span /><span /></div>
      ) : (
        <>
          {memberAllocatedTokens == null || memberAllocatedTokens <= 0 ? (
            <div className="mcp-usage-total is-blocked"><span>当前不可用</span><strong>0</strong><small>可用 Token</small></div>
          ) : (
            <TokenProgress label="个人用量" value={memberUsedTokens} total={memberAllocatedTokens} tone="blue" />
          )}
          <div className="mcp-usage-breakdown">
            <span><small>团队累计</small><strong>{formatCompactNumber(totalTokens)}</strong></span>
            <span><small>模型调用</small><strong>{formatCompactNumber(usage?.successfulCalls ?? 0)}</strong></span>
          </div>
          <div className="mcp-model-meta">
            <span>{status?.model ?? '-'}</span>
            <span>{modelStatusLabel(status?.status)}</span>
          </div>
          {canAllocate ? <button type="button" className="mcp-quota-button" onClick={onAllocate}>Token 分配</button> : null}
          {usage?.lastCalledAt ? <p className="mcp-model-time">最近调用：{formatTime(usage.lastCalledAt)}</p> : null}
        </>
      )}
    </section>
  )
}

function TokenAllocationDialog({ onClose }: { onClose: () => void }) {
  const query = useQuery({
    queryKey: ['admin-members', 'token-allocation'],
    queryFn: () => getMembers({ keyword: '', role: '', status: 'ACTIVE', page: 0, size: 100 }),
  })
  return (
    <div className="admin-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="admin-modal mcp-token-modal" role="dialog" aria-modal="true" aria-labelledby="token-allocation-title">
        <header><span className="drawer-title-icon"><Sparkle size={20} /></span><div><h2 id="token-allocation-title">Token 分配</h2><p>成员只有获得正数额度后才能使用 MCP 助手，留空或 0 表示停用。</p></div><button className="icon-button" type="button" onClick={onClose} aria-label="关闭"><X size={18} /></button></header>
        <div className="mcp-token-member-list">
          {query.data?.content.map((member) => <TokenAllocationRow key={member.id} member={member} />)}
          {query.isPending ? <p className="audit-state">正在读取成员用量…</p> : null}
          {query.isError ? <p className="audit-state is-error">{query.error.message}</p> : null}
        </div>
        <footer><button className="button button-secondary" type="button" onClick={onClose}>完成</button></footer>
      </section>
    </div>
  )
}

function TokenAllocationRow({ member }: { member: AdminMember }) {
  const queryClient = useQueryClient()
  const [value, setValue] = useState(member.allocatedTokens == null ? '' : String(member.allocatedTokens))
  const mutation = useMutation({
    mutationFn: () => updateMemberTokenQuota(member.id, value.trim() === '' ? null : Number(value)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-members'] })
      await queryClient.invalidateQueries({ queryKey: ['ai-model-status'] })
    },
  })
  const invalid = value.trim() !== '' && (!/^\d+$/.test(value.trim()) || Number(value) > 1_000_000_000_000)
  return (
    <div className="mcp-token-member-row">
      <span className="member-identity"><i>{member.displayName.slice(0, 1)}</i><span><strong>{member.displayName}</strong><small>已用 {formatCompactNumber(member.usedTokens)} Token</small></span></span>
      <label><span>分配额度</span><input aria-label={`${member.displayName} Token 额度`} inputMode="numeric" placeholder="未分配" value={value} onChange={(event) => setValue(event.target.value)} /></label>
      <button className="button button-primary" type="button" disabled={invalid || mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? '保存中' : '保存'}</button>
      {mutation.isError ? <p role="alert">{mutation.error.message}</p> : null}
      {mutation.isSuccess ? <p className="is-success">已更新</p> : null}
    </div>
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
    attachments: dataAttachments(message.data),
    data: message.data,
    createdAt: message.createdAt,
  }
}

function MessageAttachmentList({ attachments }: { attachments: UploadedFile[] }) {
  if (!attachments.length) return null
  return (
    <div className="mcp-message-attachments" aria-label="消息附件">
      {attachments.map((file) => (
        <span key={file.id} title={file.filename}>
          <Paperclip size={13} />
          <b>{file.filename}</b>
          <small>{formatFileSize(file.sizeBytes)}</small>
        </span>
      ))}
    </div>
  )
}

function AssistantOutput({ message }: { message: ChatMessage }) {
  const traces = message.traces ?? []
  const approvalIds = dataApprovalIds(message.data)
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

      {approvalIds.map((approvalId) => (
        <InlineApproval key={approvalId} approvalId={approvalId} />
      ))}

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

function dataApprovalIds(data?: Record<string, unknown>) {
  if (!data) return []
  const ids: string[] = []
  if (typeof data.approvalId === 'string') ids.push(data.approvalId)
  if (Array.isArray(data.approvals)) {
    data.approvals.forEach((item) => {
      if (!item || typeof item !== 'object') return
      const id = (item as Record<string, unknown>).id
      if (typeof id === 'string') ids.push(id)
    })
  }
  return [...new Set(ids)]
}

function previewText(value: unknown) {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : null
}

function previewAttachments(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') return []
    const record = item as Record<string, unknown>
    const id = previewText(record.id)
    const name = previewText(record.name)
    return id && name ? [{ id, name }] : []
  })
}

function actionLabel(value: string) {
  if (value === 'SEND_EMAIL') return '发送邮件'
  if (value === 'CREATE_CRM_TASK') return '创建 CRM 任务'
  if (value === 'CREATE_INTERNAL_FOLLOW_UP') return '创建跟进任务'
  return value
}

function approvalStatusLabel(value: string) {
  if (value === 'APPROVED') return '审批已通过'
  if (value === 'REJECTED') return '审批已拒绝'
  if (value === 'EXPIRED') return '审批已过期'
  return '审批已处理'
}

function riskText(value: string) {
  if (value === 'HIGH') return '高'
  if (value === 'MEDIUM') return '中'
  return '低'
}

function approvalResultTone(approval: Approval) {
  if (approval.status === 'REJECTED' || approval.status === 'EXPIRED' || approval.actionStatus === 'FAILED') return 'error'
  if (approval.actionStatus === 'SUCCEEDED') return 'success'
  return 'pending'
}

function approvalResultText(approval: Approval) {
  if (approval.status === 'REJECTED') return '已拒绝，相关动作不会执行。'
  if (approval.status === 'EXPIRED') return '审批已过期，未执行相关动作。'
  if (approval.actionStatus === 'FAILED') return `审批已通过，但执行失败：${approval.failureMessage ?? '请检查配置后重新发起。'}`
  if (approval.actionStatus === 'SUCCEEDED') return approval.actionType === 'SEND_EMAIL' ? '审批已通过，邮件发送成功。' : '审批已通过，业务动作执行成功。'
  return '审批已通过，动作正在执行。'
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

function InlineApproval({ approvalId }: { approvalId: string }) {
  const isGuest = useIsGuest()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['approval', approvalId],
    queryFn: () => getApproval(approvalId),
  })
  const approveMutation = useMutation({
    mutationFn: approveApproval,
    onSuccess: (approval) => finishDecision(approval),
  })
  const rejectMutation = useMutation({
    mutationFn: rejectApproval,
    onSuccess: (approval) => finishDecision(approval),
  })

  function finishDecision(approval: Approval) {
    queryClient.setQueryData(['approval', approvalId], approval)
    void queryClient.invalidateQueries({ queryKey: ['approvals'] })
    void queryClient.invalidateQueries({ queryKey: ['agent-runs'] })
    void queryClient.invalidateQueries({ queryKey: ['follow-ups'] })
    void queryClient.invalidateQueries({ queryKey: ['customers'] })
  }

  if (query.isPending) {
    return <section className="mcp-inline-approval is-loading"><CircleNotch size={16} className="mcp-spin" />正在读取审批状态</section>
  }
  if (query.isError || !query.data) {
    return <section className="mcp-inline-approval is-error"><WarningCircle size={16} />审批状态暂时无法读取，请稍后重试</section>
  }

  const approval = query.data
  const pending = approval.status === 'PENDING'
  const busy = approveMutation.isPending || rejectMutation.isPending
  const mutationError = approveMutation.error ?? rejectMutation.error
  const to = previewText(approval.preview.to)
  const subject = previewText(approval.preview.subject)
  const attachments = previewAttachments(approval.preview.attachments)
  return (
    <section className={`mcp-inline-approval ${pending ? 'is-pending' : 'is-resolved'}`} aria-label="聊天内审批">
      <header>
        <span><ShieldCheck size={17} />{pending ? '高风险操作待确认' : approvalStatusLabel(approval.status)}</span>
        <em className={`risk-label ${approval.riskLevel === 'HIGH' ? 'risk-high' : approval.riskLevel === 'MEDIUM' ? 'risk-medium' : 'risk-low'}`}>{riskText(approval.riskLevel)}风险</em>
      </header>
      <strong>{actionLabel(approval.actionType)} · {approval.customerName}</strong>
      <p>{approval.reason}</p>
      {approval.actionType === 'SEND_EMAIL' ? (
        <dl>
          <div><dt><EnvelopeSimple size={14} />收件人</dt><dd>{to ?? '未解析'}</dd></div>
          <div><dt>主题</dt><dd>{subject ?? '(无主题)'}</dd></div>
          <div><dt>附件</dt><dd>{attachments.length ? attachments.map((item) => item.name).join('、') : '无附件'}</dd></div>
        </dl>
      ) : null}
      {pending ? (
        <div className="mcp-inline-approval-actions">
          <button type="button" className="button button-secondary" disabled={isGuest || busy} onClick={() => rejectMutation.mutate(approval)}><X size={15} />拒绝</button>
          <button type="button" className="button button-primary" disabled={isGuest || busy} onClick={() => approveMutation.mutate(approval)}>
            {approveMutation.isPending ? <CircleNotch size={15} className="mcp-spin" /> : <Check size={15} />}
            {approval.actionType === 'SEND_EMAIL' ? '批准并发送' : '批准并执行'}
          </button>
        </div>
      ) : (
        <p className={`mcp-inline-approval-result is-${approvalResultTone(approval)}`}>{approvalResultText(approval)}</p>
      )}
      {mutationError ? <p className="mcp-inline-approval-error" role="alert">{mutationError.message}</p> : null}
    </section>
  )
}

function formatFileSize(value: number) {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value >= 1024) return `${Math.ceil(value / 1024)} KB`
  return `${value} B`
}

function percentage(value: number, total: number) {
  if (!Number.isFinite(value) || !Number.isFinite(total) || total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((value / total) * 100)))
}

function dataAttachments(data: Record<string, unknown> | null | undefined): UploadedFile[] {
  const value = data?.attachments
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') return []
    const record = item as Record<string, unknown>
    const id = typeof record.id === 'string' ? record.id : ''
    const filename = typeof record.filename === 'string'
      ? record.filename
      : typeof record.name === 'string' ? record.name : ''
    if (!id || !filename) return []
    return [{
      id,
      customerId: typeof record.customerId === 'string' ? record.customerId : null,
      filename,
      contentType: typeof record.contentType === 'string' ? record.contentType : 'application/octet-stream',
      sizeBytes: typeof record.sizeBytes === 'number' ? record.sizeBytes : 0,
      sha256: typeof record.sha256 === 'string' ? record.sha256 : '',
      createdAt: typeof record.createdAt === 'string' ? record.createdAt : '',
    }]
  })
}
