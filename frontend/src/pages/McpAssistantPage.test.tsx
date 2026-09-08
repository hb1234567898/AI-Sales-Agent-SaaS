import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getMcpConversations,
  getMcpMessages,
  streamMcpChatMessage,
  type AssistantChatResponse,
} from '../api/mcp-chat-api'
import { McpAssistantPage } from './McpAssistantPage'

vi.mock('../auth/use-auth', () => ({
  useIsGuest: () => false,
}))

vi.mock('../api/mcp-chat-api', () => ({
  getMcpConversations: vi.fn(),
  getMcpMessages: vi.fn(),
  streamMcpChatMessage: vi.fn(),
}))

function renderMcpAssistantPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <McpAssistantPage />
    </QueryClientProvider>,
  )
}

describe('McpAssistantPage', () => {
  beforeEach(() => {
    vi.mocked(getMcpConversations).mockResolvedValue({
      content: [],
      page: 0,
      size: 30,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    })
    vi.mocked(getMcpMessages).mockResolvedValue({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    })
    vi.mocked(streamMcpChatMessage).mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('发送指令时展示 Agent 思考过程加载态', async () => {
    vi.mocked(streamMcpChatMessage).mockReturnValue(new Promise(() => undefined))
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.type(screen.getByPlaceholderText(/给云岚科技导入聊天/), '查看待审批')
    await user.click(screen.getByRole('button', { name: /发送指令/ }))

    expect(await screen.findByText('Agent 正在处理')).toBeInTheDocument()
    expect(screen.getByText('正在连接助手…')).toBeInTheDocument()
    expect(screen.queryByText('规划工具调用')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Agent 执行进度')).toBeInTheDocument()
  })

  it('点击工具卡片后把调用模板填入输入框', async () => {
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.click(screen.getByRole('button', { name: '套用 customer.create 工具模板' }))

    expect(screen.getByPlaceholderText(/给云岚科技导入聊天/)).toHaveValue(
      '新增客户：沐光医疗，行业：医疗科技，联系人：苏恬，电话：13800000007',
    )
  })

  it('返回后分区展示结果输出、思考摘要和工具轨迹', async () => {
    const response: AssistantChatResponse = {
      conversationId: 'conversation-1',
      messageId: 'message-1',
      role: 'assistant',
      content: '已查询到 2 条待审批建议。',
      reasoningSummary: '识别为审批查询意图，读取审批列表并按创建时间倒序返回。',
      toolTraces: [
        { name: 'approval.list', status: 'SUCCEEDED', summary: '返回 2 条待审批记录' },
      ],
      data: {},
      createdAt: '2026-09-05T08:00:00Z',
    }
    vi.mocked(streamMcpChatMessage).mockResolvedValue(response)
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.type(screen.getByPlaceholderText(/给云岚科技导入聊天/), '查看待审批')
    await user.click(screen.getByRole('button', { name: /发送指令/ }))

    expect(await screen.findByText('已查询到 2 条待审批建议。')).toBeInTheDocument()
    expect(screen.getAllByText('结果输出').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('执行过程').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('工具轨迹')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('approval.list')).toBeInTheDocument())
  })

  it('完成前显示真实文本增量，断线保留内容并重新启用发送', async () => {
    let rejectStream!: (error: Error) => void
    vi.mocked(streamMcpChatMessage).mockImplementation((_input, onEvent) => {
      onEvent({ type: 'delta', data: { text: '正在返回的第一段' } })
      return new Promise((_resolve, reject) => { rejectStream = reject })
    })
    const user = userEvent.setup()
    renderMcpAssistantPage()
    const composer = screen.getByPlaceholderText(/给云岚科技导入聊天/)
    await user.type(composer, '查看待审批')
    await user.click(screen.getByRole('button', { name: /发送指令/ }))
    expect(await screen.findByText(/正在返回的第一段/)).toBeInTheDocument()
    expect(screen.getByText('Agent 正在处理')).toBeInTheDocument()
    rejectStream(new Error('连接中断'))
    expect(await screen.findByRole('alert')).toHaveTextContent('连接中断')
    expect(screen.getByText('正在返回的第一段')).toBeInTheDocument()
    await user.type(composer, '查看跟进任务')
    expect(screen.getByRole('button', { name: /发送指令/ })).toBeEnabled()
  })
})
