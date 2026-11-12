import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getGuestData } from '../api/guest-data'
import { getKnowledgeChunks, getKnowledgeDocumentText, type KnowledgeDocument } from '../api/knowledge-api'
import { MarkdownContent } from '../components/MarkdownContent'

export function KnowledgeDocumentPreview({ doc, guest }: { doc: KnowledgeDocument; guest: boolean }) {
  const [page, setPage] = useState(0)
  const [source, setSource] = useState(false)
  // Retrieval chunks overlap and may split tables or fenced code. Read text files whole.
  const fullText = !guest && /\.(txt|md|markdown)$/i.test(doc.filename)
  const text = useQuery({
    queryKey: ['knowledge', 'text', doc.id, doc.revision],
    queryFn: () => getKnowledgeDocumentText(doc.id),
    enabled: doc.status === 'READY' && fullText,
  })
  const chunks = useQuery({
    queryKey: ['knowledge', 'chunks', doc.id, page, doc.revision],
    queryFn: () => guest
      ? Promise.resolve(getGuestData(`/api/v1/knowledge/documents/${doc.id}/chunks?page=${page}&size=20`) as Awaited<ReturnType<typeof getKnowledgeChunks>>)
      : getKnowledgeChunks(doc.id, page),
    enabled: doc.status === 'READY' && !fullText,
  })
  const result = fullText ? text : chunks
  const renderContent = (content: string) => source ? <pre className="knowledge-markdown-source">{content}</pre> : <MarkdownContent content={content} />

  return <section className="knowledge-chunks knowledge-preview" aria-label="资料原文">
    <header className="knowledge-preview-toolbar"><h3>资料原文</h3><div className="segmented-filter" role="group" aria-label="原文显示模式">
      <button type="button" className={!source ? 'is-active' : ''} aria-pressed={!source} onClick={() => setSource(false)}>预览</button>
      <button type="button" className={source ? 'is-active' : ''} aria-pressed={source} onClick={() => setSource(true)}>源码</button>
    </div></header>
    {doc.status !== 'READY' ? <p>资料尚未完成解析</p> : result.isPending ? <p role="status">正在读取原文…</p> : result.isError ? <div><p className="form-error" role="alert">{result.error instanceof Error ? result.error.message : '读取原文失败'}</p><button className="button button-secondary" onClick={() => void result.refetch()}>重试读取原文</button></div> : fullText
      ? renderContent(text.data ?? '')
      : <>{chunks.data?.content.map((chunk) => <article key={chunk.id}><header>片段 {chunk.chunkIndex + 1}{chunk.pageNumber ? ` · 第 ${chunk.pageNumber} 页` : ''}</header>{renderContent(chunk.content)}</article>)}
        {chunks.data && chunks.data.totalPages > 1 && <footer className="table-pagination"><span>第 {page + 1} / {chunks.data.totalPages} 页</span><div><button disabled={!page} onClick={() => setPage(page - 1)}>上一页</button><button disabled={page + 1 >= chunks.data.totalPages} onClick={() => setPage(page + 1)}>下一页</button></div></footer>}
      </>}
  </section>
}
