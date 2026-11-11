import { getJson, requestJson } from './axios-client'

export const knowledgeCategories = { PRODUCT: '产品资料', PRICING: '报价与定价', PLAYBOOK: '销售方法', CASE_STUDY: '客户案例', POLICY: '流程制度', OTHER: '其他资料' }
export type KnowledgeCategory = keyof typeof knowledgeCategories
export interface KnowledgeDocument {
  id: string; title: string; category: KnowledgeCategory; visibility: 'TEAM' | 'PRIVATE'
  status: 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED'; enabled: boolean
  chunkCount: number; characterCount: number; errorMessage: string | null; revision: number; version: number
  filename: string; sizeBytes: number; uploadedByName: string; createdAt: string; updatedAt: string; canManage: boolean
}
export interface KnowledgeMetadata { title: string; category: KnowledgeCategory; visibility: 'TEAM' | 'PRIVATE'; version?: number }
export interface KnowledgePage<T> { content: T[]; totalElements: number; totalPages: number; page: number; size: number; first: boolean; last: boolean }
export interface KnowledgeChunk { id: string; documentId: string; chunkIndex: number; pageNumber: number | null; title: string; content: string; revision: number }
export interface KnowledgeSource { id: string; documentId: string; title: string; chunkIndex: number; pageNumber: number | null; excerpt: string; score: number; revision: number }
export interface KnowledgeAnswer { content: string; sources: KnowledgeSource[]; usage: { promptTokens: number | null; completionTokens: number | null; totalTokens: number | null } | null }
const base = '/api/v1/knowledge'
export function getKnowledgeDocuments(query = '', category = '', page = 0) {
  return getJson<KnowledgePage<KnowledgeDocument>>(`${base}/documents?${new URLSearchParams({ query, category, page: String(page), size: '20' })}`)
}
export function getKnowledgeDocument(id: string) { return getJson<KnowledgeDocument>(`${base}/documents/${id}`) }
export function getKnowledgeChunks(id: string, page = 0) { return getJson<KnowledgePage<KnowledgeChunk>>(`${base}/documents/${id}/chunks?page=${page}&size=20`) }
export function uploadKnowledgeDocument(metadata: KnowledgeMetadata, file: File) {
  const form = new FormData()
  form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  form.append('file', file)
  return requestJson<KnowledgeDocument>(`${base}/documents`, { method: 'POST', data: form, timeout: 180000 })
}
export function createKnowledgeText(metadata: KnowledgeMetadata, content: string) { return requestJson<KnowledgeDocument>(`${base}/documents/text`, { method: 'POST', data: { metadata, content }, timeout: 180000 }) }
export function editKnowledgeDocument(id: string, input: KnowledgeMetadata) { return requestJson<KnowledgeDocument>(`${base}/documents/${id}`, { method: 'PUT', data: input, timeout: 180000 }) }
export function setKnowledgeState(doc: KnowledgeDocument) { return requestJson<KnowledgeDocument>(`${base}/documents/${doc.id}/state`, { method: 'PUT', data: { enabled: !doc.enabled, version: doc.version } }) }
export function reprocessKnowledgeDocument(doc: KnowledgeDocument) { return requestJson<KnowledgeDocument>(`${base}/documents/${doc.id}/reprocess`, { method: 'POST', data: { version: doc.version }, timeout: 180000 }) }
export function replaceKnowledgeFile(doc: KnowledgeDocument, file: File) {
  const form = new FormData(); form.append('file', file); form.append('version', String(doc.version))
  return requestJson<KnowledgeDocument>(`${base}/documents/${doc.id}/replace`, { method: 'POST', data: form, timeout: 180000 })
}
export function deleteKnowledgeDocument(doc: KnowledgeDocument) { return requestJson<void>(`${base}/documents/${doc.id}`, { method: 'DELETE', params: { version: doc.version } }) }
export function askKnowledge(question: string, documentId?: string) { return requestJson<KnowledgeAnswer>(`${base}/query`, { method: 'POST', data: { question, documentId }, timeout: 180000 }) }
export async function downloadKnowledgeDocument(doc: KnowledgeDocument) {
  const blob = await requestJson<Blob>(`${base}/documents/${doc.id}/file`, { method: 'GET', responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = doc.filename; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
