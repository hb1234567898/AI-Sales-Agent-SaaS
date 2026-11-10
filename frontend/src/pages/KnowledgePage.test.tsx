import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { KnowledgePage } from './KnowledgePage'
import * as api from '../api/knowledge-api'

const auth = vi.hoisted(() => ({ guest: false, role: 'OWNER' }))
vi.mock('../auth/use-auth', () => ({ useIsGuest: () => auth.guest, useAuth: () => ({ role: auth.role }) }))
vi.mock('../api/knowledge-api', async (original) => ({
  ...await original<typeof import('../api/knowledge-api')>(),
  getKnowledgeDocuments: vi.fn(), getKnowledgeDocument: vi.fn(), getKnowledgeChunks: vi.fn(),
  uploadKnowledgeDocument: vi.fn(), createKnowledgeText: vi.fn(), askKnowledge: vi.fn(),
  setKnowledgeState: vi.fn(), deleteKnowledgeDocument: vi.fn(),
}))
const doc: api.KnowledgeDocument = {
  id: 'document-1', title: '报价审批流程', category: 'POLICY', visibility: 'TEAM', status: 'READY', enabled: true,
  chunkCount: 1, characterCount: 20, errorMessage: null, revision: 1, version: 2,
  filename: '报价审批流程.txt', sizeBytes: 60, uploadedByName: '销售甲', createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z', canManage: true,
}
function page<T>(content: T[]): api.KnowledgePage<T> { return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true } }
function mount() {
  return render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><KnowledgePage /></QueryClientProvider></MemoryRouter>)
}
describe('KnowledgePage', () => {
  beforeEach(() => {
    vi.resetAllMocks(); auth.guest = false; auth.role = 'OWNER'
    vi.mocked(api.getKnowledgeDocuments).mockResolvedValue(page([doc]))
    vi.mocked(api.getKnowledgeDocument).mockResolvedValue(doc)
    vi.mocked(api.getKnowledgeChunks).mockResolvedValue(page([{ id: 'chunk-1', documentId: doc.id, chunkIndex: 0, pageNumber: null, title: doc.title, content: '发送报价前需销售经理审批。', revision: 1 }]))
  })
  afterEach(cleanup)
  it('never invents company documents or answers when the library is empty', async () => {
    vi.mocked(api.getKnowledgeDocuments).mockResolvedValue(page([])); mount()
    expect(await screen.findByText('暂无资料')).toBeInTheDocument()
    expect(api.askKnowledge).not.toHaveBeenCalled()
    expect(screen.queryByText('报价审批流程')).not.toBeInTheDocument()
  })
  it('does not upload a selected file until the user submits', async () => {
    vi.mocked(api.uploadKnowledgeDocument).mockResolvedValue(doc)
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('button', { name: '新增资料' }))
    const dialog = screen.getByRole('dialog', { name: '新增资料' })
    const file = new File(['审批正文'], '流程.txt', { type: 'text/plain' })
    await user.upload(within(dialog).getByLabelText('资料文件'), file)
    expect(api.uploadKnowledgeDocument).not.toHaveBeenCalled()
    expect(within(dialog).getByLabelText('资料标题')).toHaveValue('流程')
    await user.click(within(dialog).getByRole('button', { name: '保存并入库' }))
    await waitFor(() => expect(api.uploadKnowledgeDocument).toHaveBeenCalledWith({ title: '流程', category: 'PRODUCT', visibility: 'TEAM' }, file))
    expect(await screen.findByRole('dialog', { name: '资料详情' })).toBeInTheDocument()
  })
  it('supports manual text ingestion and displays a persisted parsing failure', async () => {
    const failed = { ...doc, status: 'FAILED' as const, errorMessage: '扫描件需要先进行 OCR' }
    vi.mocked(api.createKnowledgeText).mockResolvedValue(failed)
    vi.mocked(api.getKnowledgeDocument).mockResolvedValue(failed)
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('button', { name: '新增资料' }))
    const dialog = screen.getByRole('dialog', { name: '新增资料' })
    await user.click(within(dialog).getByRole('button', { name: '录入文本' }))
    await user.type(within(dialog).getByLabelText('资料标题'), '报价审批流程')
    await user.type(within(dialog).getByLabelText('资料正文'), '需要审批')
    await user.click(within(dialog).getByRole('button', { name: '保存并入库' }))
    expect(await screen.findByText('扫描件需要先进行 OCR')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重新解析' })).toBeEnabled()
    expect(api.uploadKnowledgeDocument).not.toHaveBeenCalled()
  })
  it('shows grounded answers, source text and token usage', async () => {
    vi.mocked(api.askKnowledge).mockResolvedValue({ content: '需要经理审批。[资料 1]', usage: { promptTokens: 20, completionTokens: 10, totalTokens: 30 }, sources: [{ id: 'chunk-1', documentId: doc.id, title: doc.title, chunkIndex: 0, pageNumber: 2, excerpt: '发送报价前需销售经理审批。', score: 0.8, revision: 1 }] })
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('tab', { name: '知识问答' }))
    await user.type(screen.getByLabelText('向知识库提问'), '报价需要谁审批？')
    await user.click(screen.getByRole('button', { name: '提问' }))
    expect(await screen.findByText('需要经理审批。[资料 1]')).toBeInTheDocument()
    expect(screen.getByText('本次用量 30 Token')).toBeInTheDocument()
    await user.click(screen.getByText('查看引用原文'))
    expect(screen.getByText('发送报价前需销售经理审批。')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /资料 1.*报价审批流程/ }))
    expect(await screen.findByRole('dialog', { name: '资料详情' })).toBeInTheDocument()
  })
  it('keeps service errors visible rather than substituting generated answers', async () => {
    vi.mocked(api.askKnowledge).mockRejectedValue(new Error('未分配 Token 额度'))
    const user = userEvent.setup(); mount()
    await user.click(screen.getByRole('tab', { name: '知识问答' }))
    await user.type(screen.getByLabelText('向知识库提问'), '报价')
    await user.click(screen.getByRole('button', { name: '提问' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('未分配 Token 额度')
    expect(screen.queryByText('参考资料')).not.toBeInTheDocument()
  })
  it('requires explicit confirmation before deleting', async () => {
    vi.mocked(api.deleteKnowledgeDocument).mockResolvedValue(undefined)
    const user = userEvent.setup(); mount()
    await user.click(await screen.findByRole('button', { name: /报价审批流程.*报价审批流程.txt/ }))
    await user.click(await screen.findByRole('button', { name: '删除资料' }))
    expect(api.deleteKnowledgeDocument).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '确认删除' }))
    await waitFor(() => expect(vi.mocked(api.deleteKnowledgeDocument).mock.calls[0]?.[0]).toEqual(doc))
  })
  it('lets guests browse and ask about local demo knowledge without team API calls', async () => {
    auth.guest = true; const user = userEvent.setup(); mount()
    expect(await screen.findByText('销售报价与折扣审批')).toBeInTheDocument()
    expect(api.getKnowledgeDocuments).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '新增资料' })).toBeDisabled()
    await user.click(screen.getByRole('tab', { name: '知识问答' }))
    await user.type(screen.getByLabelText('向知识库提问'), '报价审批')
    await user.click(screen.getByRole('button', { name: '提问' }))
    expect(await screen.findByText(/演示回答/)).toBeInTheDocument()
    expect(api.askKnowledge).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: /资料 1.*销售报价/ }))
    expect(await screen.findByRole('dialog', { name: '资料详情' })).toBeInTheDocument()
    expect(api.getKnowledgeDocument).not.toHaveBeenCalled()
  })
})
