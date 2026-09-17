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
import { uploadFile } from '../api/files-api'
import { approveApproval, getApproval, rejectApproval, type Approval } from '../api/approvals-api'
import { getAiModelStatus } from '../api/ai-settings-api'
import { getMembers, getTeamTokenBudget, updateMemberTokenQuota, updateTeamTokenBudget } from '../api/admin-api'
import { ApiError } from '../api/axios-client'
import { McpAssistantPage } from './McpAssistantPage'

vi.mock('../auth/use-auth', () => ({
  useIsGuest: () => false,
  useAuth: () => ({ role: 'OWNER', memberId: 'member-1' }),
}))

vi.mock('../api/mcp-chat-api', () => ({
  getMcpConversations: vi.fn(),
  getMcpMessages: vi.fn(),
  streamMcpChatMessage: vi.fn(),
}))

vi.mock('../api/files-api', () => ({
  uploadFile: vi.fn(),
}))

vi.mock('../api/approvals-api', () => ({
  getApproval: vi.fn(),
  approveApproval: vi.fn(),
  rejectApproval: vi.fn(),
}))

vi.mock('../api/ai-settings-api', () => ({
  getAiModelStatus: vi.fn(),
}))

vi.mock('../api/admin-api', () => ({
  getMembers: vi.fn(),
  getTeamTokenBudget: vi.fn(),
  updateMemberTokenQuota: vi.fn(),
  updateTeamTokenBudget: vi.fn(),
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
    vi.mocked(getAiModelStatus).mockResolvedValue({
      provider: 'QWEN', model: 'qwen3.8-max', baseUrl: 'https://example.test',
      apiKeyConfigured: true, ready: true, status: 'READY',
      usage: {
        inputTokens: 100, outputTokens: 50, cachedInputTokens: 0, totalTokens: 150,
        successfulCalls: 3, lastCalledAt: '2026-09-15T08:00:00Z',
        remainingTokens: null, remainingStatus: 'CHAT_API_DOES_NOT_RETURN_ACCOUNT_REMAINING',
        memberAllocatedTokens: 10_000, memberUsedTokens: 150, memberRemainingTokens: 9_850,
      },
    })
    vi.mocked(getMembers).mockResolvedValue({
      content: [{
        id: 'member-1', userId: 'user-1', email: 'sales@example.test', displayName: '销售成员',
        role: 'SALES', status: 'ACTIVE', joinedAt: '2026-09-01T08:00:00Z', lastLoginAt: null,
        createdAt: '2026-09-01T08:00:00Z', allocatedTokens: null, usedTokens: 1200, remainingTokens: null,
      }],
      page: 0, size: 100, totalElements: 1, totalPages: 1, first: true, last: true,
    })
    vi.mocked(updateMemberTokenQuota).mockReset()
    vi.mocked(getTeamTokenBudget).mockResolvedValue({ totalTokens: 100_000, allocatedTokens: 0, unallocatedTokens: 100_000 })
    vi.mocked(updateTeamTokenBudget).mockReset()
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
    vi.mocked(uploadFile).mockReset()
    vi.mocked(getApproval).mockReset()
    vi.mocked(approveApproval).mockReset()
    vi.mocked(rejectApproval).mockReset()
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

  it('未分配 Token 额度时禁用 MCP 输入和发送', async () => {
    vi.mocked(getAiModelStatus).mockResolvedValue({
      provider: 'QWEN', model: 'qwen3.8-max', baseUrl: 'https://example.test',
      apiKeyConfigured: true, ready: true, status: 'READY',
      usage: {
        inputTokens: 0, outputTokens: 0, cachedInputTokens: 0, totalTokens: 0,
        successfulCalls: 0, lastCalledAt: null, remainingTokens: null,
        remainingStatus: 'CHAT_API_DOES_NOT_RETURN_ACCOUNT_REMAINING',
        memberAllocatedTokens: null, memberUsedTokens: 0, memberRemainingTokens: null,
      },
    })
    renderMcpAssistantPage()

    const composer = await screen.findByPlaceholderText('尚未分配 Token 额度，请联系管理员')
    expect(composer).toBeDisabled()
    expect(screen.getByRole('button', { name: '发送指令' })).toBeDisabled()
    expect(screen.getByText('未分配额度')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'MCP 助手导航' })).not.toBeInTheDocument()
  })

  it('剩余额度从 100% 随用量递减到 0%', async () => {
    const status = await getAiModelStatus()
    const usage = status.usage!
    vi.mocked(getAiModelStatus).mockResolvedValue({
      ...status,
      usage: { ...usage, memberAllocatedTokens: 200_000, memberUsedTokens: 0, memberRemainingTokens: 200_000 },
    })
    renderMcpAssistantPage()
    expect(await screen.findByLabelText('剩余额度 100%')).toBeInTheDocument()
    expect(screen.getByText('已用 0 / 20万')).toBeInTheDocument()

    cleanup()
    vi.mocked(getAiModelStatus).mockResolvedValue({
      ...status,
      usage: { ...usage, memberAllocatedTokens: 200_000, memberUsedTokens: 200_000, memberRemainingTokens: 0 },
    })
    renderMcpAssistantPage()
    expect(await screen.findByLabelText('剩余额度 0%')).toBeInTheDocument()
  })

  it('管理员可以在用量统计中分配成员 Token', async () => {
    vi.mocked(updateMemberTokenQuota).mockResolvedValue({
      id: 'member-1', userId: 'user-1', email: 'sales@example.test', displayName: '销售成员',
      role: 'SALES', status: 'ACTIVE', joinedAt: '2026-09-01T08:00:00Z', lastLoginAt: null,
      createdAt: '2026-09-01T08:00:00Z', allocatedTokens: 50_000, usedTokens: 1200, remainingTokens: 48_800,
    })
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.click(await screen.findByRole('button', { name: 'Token 分配' }))
    const input = await screen.findByLabelText('销售成员 Token 额度')
    await user.type(input, '50000')
    await user.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(updateMemberTokenQuota).toHaveBeenCalledWith('member-1', 50_000))
    expect(await screen.findByText('已更新')).toBeInTheDocument()
  })

  it('先按团队总额配置，再限制成员分配额度', async () => {
    vi.mocked(getTeamTokenBudget).mockResolvedValue({ totalTokens: null, allocatedTokens: 0, unallocatedTokens: null })
    vi.mocked(updateTeamTokenBudget).mockResolvedValue({ totalTokens: 10_000, allocatedTokens: 0, unallocatedTokens: 10_000 })
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.click(await screen.findByRole('button', { name: 'Token 分配' }))
    const memberInput = await screen.findByLabelText('销售成员 Token 额度')
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    await user.type(screen.getByLabelText('团队 Token 总额'), '10000')
    await user.click(screen.getByRole('button', { name: '保存总额' }))
    await waitFor(() => expect(updateTeamTokenBudget).toHaveBeenCalledWith(10_000))
    await waitFor(() => expect(screen.getByRole('button', { name: '保存' })).toBeEnabled())

    await user.type(memberInput, '10001')
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    await user.clear(memberInput)
    await user.type(memberInput, '10000')
    expect(screen.getByRole('button', { name: '保存' })).toBeEnabled()
  })

  it('后端尚未部署总额接口时提示版本不匹配并禁止保存', async () => {
    vi.mocked(getTeamTokenBudget).mockRejectedValue(new ApiError('请求失败，状态码 404', 404))
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.click(await screen.findByRole('button', { name: 'Token 分配' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('当前后端版本尚未提供 Token 总额接口')
    await user.type(screen.getByLabelText('团队 Token 总额'), '950228')
    expect(screen.getByRole('button', { name: '保存总额' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    expect(updateTeamTokenBudget).not.toHaveBeenCalled()
  })

  it('选择附件时保留在本地，发送后才上传并携带附件 ID', async () => {
    const response: AssistantChatResponse = {
      conversationId: 'conversation-1',
      messageId: 'message-1',
      role: 'assistant',
      content: 'Agent 已运行完成。',
      reasoningSummary: null,
      toolTraces: [],
      data: {},
      createdAt: '2026-09-05T08:00:00Z',
    }
    vi.mocked(uploadFile).mockResolvedValue({
      id: 'file-1',
      customerId: null,
      filename: 'quote.pdf',
      contentType: 'application/pdf',
      sizeBytes: 2048,
      sha256: 'hash',
      createdAt: '2026-09-05T08:00:00Z',
    })
    vi.mocked(streamMcpChatMessage).mockResolvedValue(response)
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.upload(screen.getByLabelText('选择 MCP 聊天附件'), new File(['quote'], 'quote.pdf', { type: 'application/pdf' }))
    expect(await screen.findByText('quote.pdf')).toBeInTheDocument()
    expect(uploadFile).not.toHaveBeenCalled()
    expect(streamMcpChatMessage).not.toHaveBeenCalled()

    await user.type(screen.getByPlaceholderText(/给云岚科技导入聊天/), '运行 Agent 分析云岚科技')
    await user.click(screen.getByRole('button', { name: /发送指令/ }))

    await waitFor(() => expect(uploadFile).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(streamMcpChatMessage).toHaveBeenCalledWith(
      expect.objectContaining({ attachmentIds: ['file-1'] }),
      expect.any(Function),
      expect.any(AbortSignal),
    ))
  })

  it('附件上传失败时保留消息和文件且不调用 AI', async () => {
    vi.mocked(uploadFile).mockRejectedValue(new Error('附件上传失败'))
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.upload(screen.getByLabelText('选择 MCP 聊天附件'), new File(['quote'], 'quote.pdf', { type: 'application/pdf' }))
    const composer = screen.getByPlaceholderText(/给云岚科技导入聊天/)
    await user.type(composer, '请分析报价附件')
    await user.click(screen.getByRole('button', { name: /发送指令/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('附件上传失败')
    expect(composer).toHaveValue('请分析报价附件')
    expect(screen.getByText('quote.pdf')).toBeInTheDocument()
    expect(streamMcpChatMessage).not.toHaveBeenCalled()
  })

  it('在聊天结果中直接批准高风险动作并显示执行结果', async () => {
    const pendingApproval: Approval = {
      id: 'approval-1',
      actionRequestId: 'action-1',
      runId: 'run-1',
      customerId: 'customer-1',
      customerName: '和成科技',
      actionType: 'SEND_EMAIL',
      riskLevel: 'HIGH',
      actionStatus: 'AWAITING_APPROVAL',
      failureCode: null,
      failureMessage: null,
      status: 'PENDING',
      reason: '用户请求给客户发送方案，需人工核对后发送',
      preview: {
        to: '2564942830@qq.com',
        subject: '和成科技方案',
        attachments: [{ id: 'file-1', name: '和成科技方案.docx', sizeBytes: 2048 }],
      },
      requester: '销售',
      version: 1,
      requestedAt: '2026-09-15T08:00:00Z',
      expiresAt: null,
      actionCompletedAt: null,
    }
    const completedApproval: Approval = {
      ...pendingApproval,
      status: 'APPROVED',
      actionStatus: 'SUCCEEDED',
      version: 2,
      actionCompletedAt: '2026-09-15T08:01:00Z',
    }
    vi.mocked(getApproval).mockResolvedValue(pendingApproval)
    vi.mocked(approveApproval).mockResolvedValue(completedApproval)
    vi.mocked(streamMcpChatMessage).mockResolvedValue({
      conversationId: 'conversation-1',
      messageId: 'message-1',
      role: 'assistant',
      content: '已生成待审批方案邮件。',
      reasoningSummary: null,
      toolTraces: [],
      data: { approvalId: 'approval-1' },
      createdAt: '2026-09-15T08:00:00Z',
    })
    const user = userEvent.setup()
    renderMcpAssistantPage()

    await user.type(screen.getByPlaceholderText(/给云岚科技导入聊天/), '给和成科技发送方案')
    await user.click(screen.getByRole('button', { name: /发送指令/ }))

    expect(await screen.findByText('高风险操作待确认')).toBeInTheDocument()
    expect(screen.getByText('和成科技方案.docx')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '批准并发送' }))
    await waitFor(() => expect(approveApproval).toHaveBeenCalled())
    expect(vi.mocked(approveApproval).mock.calls[0]?.[0]).toEqual(pendingApproval)
    expect(await screen.findByText('审批已通过，邮件发送成功。')).toBeInTheDocument()
  })
})
