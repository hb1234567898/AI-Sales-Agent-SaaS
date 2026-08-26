import {
  ChatCircleDots,
  ClockCounterClockwise,
  EnvelopeSimple,
  NotePencil,
  Phone,
  Plus,
  SpinnerGap,
  UploadSimple,
  UsersThree,
  WarningCircle,
  X,
} from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import {
  createCustomerInteraction,
  getCustomerInteractions,
  importCustomerChat,
  type ChatImportInput,
  type ChatPlatform,
  type InteractionCreateInput,
  type InteractionDirection,
  type InteractionType,
} from '../../api/interactions-api'
import { SelectField } from '../forms/SelectField'

type ComposerMode = 'manual' | 'chat' | null

const typeLabels: Record<InteractionType, string> = {
  EMAIL_SENT: '已发送邮件',
  EMAIL_RECEIVED: '收到邮件',
  EMAIL_OPENED: '邮件已打开',
  CALL: '电话沟通',
  MEETING: '客户会议',
  NOTE: '销售备注',
  CHAT_IMPORT: '聊天导入',
  TASK_CREATED: '已创建任务',
  TASK_COMPLETED: '任务已完成',
  CRM_UPDATE: 'CRM 更新',
}

const sourceLabels: Record<string, string> = {
  INTERNAL: '手工记录',
  WECHAT: '微信',
  WHATSAPP: 'WhatsApp',
  OTHER: '其他聊天工具',
}

const directionLabels: Record<InteractionDirection, string> = {
  INBOUND: '客户发起',
  OUTBOUND: '销售发起',
  NONE: '双向/不区分',
}

function nowForInput() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 16)
}

function toIso(value: string) {
  return new Date(value).toISOString()
}

function formatOccurredAt(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function interactionIcon(type: InteractionType) {
  if (type === 'CHAT_IMPORT') return <ChatCircleDots size={16} />
  if (type === 'CALL') return <Phone size={16} />
  if (type === 'EMAIL_SENT' || type === 'EMAIL_RECEIVED') return <EnvelopeSimple size={16} />
  if (type === 'MEETING') return <UsersThree size={16} />
  return <NotePencil size={16} />
}

interface CustomerInteractionsPanelProps {
  customerId: string
}

export function CustomerInteractionsPanel({ customerId }: CustomerInteractionsPanelProps) {
  const queryClient = useQueryClient()
  const [composer, setComposer] = useState<ComposerMode>(null)
  const [manual, setManual] = useState({
    type: 'NOTE' as InteractionCreateInput['type'],
    direction: 'NONE' as InteractionDirection,
    occurredAt: nowForInput(),
    subject: '',
    bodyText: '',
    participantName: '',
  })
  const [chat, setChat] = useState({
    platform: 'WECHAT' as ChatPlatform,
    occurredAt: nowForInput(),
    subject: '',
    content: '',
    participantName: '',
  })

  const interactionsQuery = useQuery({
    queryKey: ['customer-interactions', customerId],
    queryFn: () => getCustomerInteractions(customerId),
  })

  async function refreshCustomerData() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['customer-interactions', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['customers'] }),
    ])
    setComposer(null)
  }

  const manualMutation = useMutation({
    mutationFn: (input: InteractionCreateInput) => createCustomerInteraction(customerId, input),
    onSuccess: refreshCustomerData,
  })
  const chatMutation = useMutation({
    mutationFn: (input: ChatImportInput) => importCustomerChat(customerId, input),
    onSuccess: refreshCustomerData,
  })

  async function submitManual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await manualMutation.mutateAsync({
        ...manual,
        occurredAt: toIso(manual.occurredAt),
        subject: manual.subject.trim() || null,
        bodyText: manual.bodyText.trim(),
        participantName: manual.participantName.trim() || null,
      })
    } catch {
      // Mutation error is rendered in the composer.
    }
  }

  async function submitChat(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await chatMutation.mutateAsync({
        ...chat,
        occurredAt: toIso(chat.occurredAt),
        subject: chat.subject.trim() || null,
        content: chat.content.trim(),
        participantName: chat.participantName.trim() || null,
      })
    } catch {
      // Mutation error is rendered in the composer.
    }
  }

  const mutationError = manualMutation.error?.message ?? chatMutation.error?.message
  const pending = manualMutation.isPending || chatMutation.isPending
  const interactions = interactionsQuery.data?.content ?? []

  return (
    <div className="customer-interactions-panel">
      <div className="interaction-toolbar">
        <div><strong>客户时间线</strong><span>{interactionsQuery.data ? `${interactionsQuery.data.totalElements} 条互动记录` : '正在读取记录'}</span></div>
        <div>
          <button className="button button-secondary" type="button" onClick={() => { setComposer('manual'); manualMutation.reset(); chatMutation.reset() }}><Plus size={14} />记录互动</button>
          <button className="button button-primary" type="button" onClick={() => { setComposer('chat'); manualMutation.reset(); chatMutation.reset() }}><UploadSimple size={14} />导入聊天</button>
        </div>
      </div>

      {composer === 'manual' ? (
        <form className="interaction-composer" onSubmit={(event) => void submitManual(event)}>
          <header><div><NotePencil size={16} /><strong>记录一次客户互动</strong></div><button type="button" onClick={() => setComposer(null)} aria-label="关闭互动表单"><X size={16} /></button></header>
          <div className="interaction-form-grid">
              <label><span>互动类型</span><SelectField value={manual.type} onChange={(value) => setManual((current) => ({ ...current, type: value as InteractionCreateInput['type'] }))} ariaLabel="互动类型" options={[{ value: 'NOTE', label: '销售备注' }, { value: 'CALL', label: '电话沟通' }, { value: 'MEETING', label: '客户会议' }, { value: 'EMAIL_SENT', label: '已发送邮件' }, { value: 'EMAIL_RECEIVED', label: '收到邮件' }]} /></label>
            <label><span>沟通方向</span><SelectField value={manual.direction} onChange={(value) => setManual((current) => ({ ...current, direction: value as InteractionDirection }))} ariaLabel="沟通方向" options={Object.entries(directionLabels).map(([value, label]) => ({ value, label }))} /></label>
            <label><span>发生时间</span><input type="datetime-local" max={nowForInput()} required value={manual.occurredAt} onChange={(event) => setManual((current) => ({ ...current, occurredAt: event.target.value }))} /></label>
            <label><span>参与人</span><input maxLength={220} value={manual.participantName} onChange={(event) => setManual((current) => ({ ...current, participantName: event.target.value }))} placeholder="例如：林婉清" /></label>
            <label className="field-span-2"><span>主题</span><input maxLength={500} value={manual.subject} onChange={(event) => setManual((current) => ({ ...current, subject: event.target.value }))} placeholder="本次沟通的简短标题" /></label>
            <label className="field-span-2"><span>内容 <b>*</b></span><textarea required maxLength={50000} rows={5} value={manual.bodyText} onChange={(event) => setManual((current) => ({ ...current, bodyText: event.target.value }))} placeholder="记录客户需求、异议、承诺与下一步安排…" /></label>
          </div>
          {mutationError ? <p className="form-error" role="alert">{mutationError}</p> : null}
          <footer><button className="button button-secondary" type="button" onClick={() => setComposer(null)} disabled={pending}>取消</button><button className="button button-primary" type="submit" disabled={pending || !manual.bodyText.trim()}>{pending ? '保存中…' : '保存互动'}</button></footer>
        </form>
      ) : null}

      {composer === 'chat' ? (
        <form className="interaction-composer chat-import-composer" onSubmit={(event) => void submitChat(event)}>
          <header><div><ChatCircleDots size={16} /><strong>粘贴聊天记录</strong></div><button type="button" onClick={() => setComposer(null)} aria-label="关闭聊天导入表单"><X size={16} /></button></header>
          <p className="chat-import-hint">原文会作为客户敏感数据保存在时间线中。导入只记录数据，不会自动回复或通知客户。</p>
          <div className="interaction-form-grid">
            <label><span>聊天平台</span><SelectField value={chat.platform} onChange={(value) => setChat((current) => ({ ...current, platform: value as ChatPlatform }))} ariaLabel="聊天平台" options={[{ value: 'WECHAT', label: '微信' }, { value: 'WHATSAPP', label: 'WhatsApp' }, { value: 'OTHER', label: '其他聊天工具' }]} /></label>
            <label><span>最近聊天时间</span><input type="datetime-local" max={nowForInput()} required value={chat.occurredAt} onChange={(event) => setChat((current) => ({ ...current, occurredAt: event.target.value }))} /></label>
            <label><span>客户联系人</span><input maxLength={220} value={chat.participantName} onChange={(event) => setChat((current) => ({ ...current, participantName: event.target.value }))} placeholder="例如：林婉清" /></label>
            <label><span>聊天主题</span><input maxLength={500} value={chat.subject} onChange={(event) => setChat((current) => ({ ...current, subject: event.target.value }))} placeholder="留空将使用平台名称" /></label>
            <label className="field-span-2"><span>聊天原文 <b>*</b></span><textarea required maxLength={100000} rows={10} value={chat.content} onChange={(event) => setChat((current) => ({ ...current, content: event.target.value }))} placeholder={'直接粘贴聊天内容，例如：\n10:24 客户：方案里能否补充部署周期？\n10:26 我：可以，今天下午补充。'} /></label>
          </div>
          {mutationError ? <p className="form-error" role="alert">{mutationError}</p> : null}
          <footer><span>{chat.content.length.toLocaleString('zh-CN')} / 100,000 字符</span><button className="button button-secondary" type="button" onClick={() => setComposer(null)} disabled={pending}>取消</button><button className="button button-primary" type="submit" disabled={pending || !chat.content.trim()}>{pending ? '导入中…' : '确认导入'}</button></footer>
        </form>
      ) : null}

      {interactionsQuery.isPending ? (
        <div className="interaction-state"><SpinnerGap className="is-spinning" size={20} /><span>正在加载客户时间线…</span></div>
      ) : interactionsQuery.isError ? (
        <div className="interaction-state is-error"><WarningCircle size={20} /><strong>互动记录加载失败</strong><span>{interactionsQuery.error.message}</span><button className="compact-button" type="button" onClick={() => void interactionsQuery.refetch()}>重新加载</button></div>
      ) : interactions.length === 0 ? (
        <div className="interaction-state"><ClockCounterClockwise size={22} /><strong>还没有互动记录</strong><span>记录一次沟通，或粘贴微信、WhatsApp 聊天作为 AI 分析依据。</span></div>
      ) : (
        <ol className="interaction-timeline">
          {interactions.map((interaction) => (
            <li key={interaction.id}>
              <span className="interaction-timeline-icon">{interactionIcon(interaction.type)}</span>
              <article>
                <header><div><strong>{interaction.subject ?? typeLabels[interaction.type]}</strong><span>{typeLabels[interaction.type]} · {sourceLabels[interaction.source] ?? interaction.source}</span></div><time>{formatOccurredAt(interaction.occurredAt)}</time></header>
                {interaction.participants.length ? <div className="interaction-participants"><UsersThree size={13} />{interaction.participants.join('、')}</div> : null}
                <p>{interaction.bodyPreview}</p>
                {interaction.bodyText.length > interaction.bodyPreview.length ? <details><summary>查看完整原文</summary><pre>{interaction.bodyText}</pre></details> : null}
              </article>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
