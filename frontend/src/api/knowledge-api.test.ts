import { beforeEach, describe, expect, it, vi } from 'vitest'
import { requestJson } from './axios-client'
import { getKnowledgeDocumentText } from './knowledge-api'

vi.mock('./axios-client', () => ({ getJson: vi.fn(), requestJson: vi.fn() }))

describe('knowledge text preview', () => {
  beforeEach(() => vi.resetAllMocks())

  it('reads UTF-8 Markdown without its BOM through the authenticated file API', async () => {
    vi.mocked(requestJson).mockResolvedValue(new TextEncoder().encode('\uFEFF# 企业资料').buffer)
    expect(await getKnowledgeDocumentText('doc-1')).toBe('# 企业资料')
    expect(requestJson).toHaveBeenCalledWith('/api/v1/knowledge/documents/doc-1/file', { method: 'GET', responseType: 'arraybuffer' })
  })

  it('decodes legacy Chinese files in the same encoding as ingestion', async () => {
    vi.mocked(requestJson).mockResolvedValue(new Uint8Array([0x23, 0x20, 0xd6, 0xd0, 0xce, 0xc4]).buffer)
    expect(await getKnowledgeDocumentText('doc-1')).toBe('# 中文')
  })

  it('preserves file access failures instead of displaying an empty document', async () => {
    vi.mocked(requestJson).mockRejectedValue(new Error('无权访问此资料'))
    await expect(getKnowledgeDocumentText('doc-1')).rejects.toThrow('无权访问此资料')
  })
})
