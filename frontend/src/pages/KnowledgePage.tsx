import { ArrowClockwise, BookOpen, CheckCircle, DownloadSimple, FileText, MagnifyingGlass, PaperPlaneRight, PencilSimple, Plus, Power, Trash, UploadSimple, WarningCircle, X } from '@phosphor-icons/react'
import { useDeferredValue, useEffect, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'
import { SelectField } from '../components/forms/SelectField'
import { useAuth, useIsGuest } from '../auth/use-auth'
import { getGuestData, guestKnowledgeAnswer } from '../api/guest-data'
import { askKnowledge, createKnowledgeText, deleteKnowledgeDocument, downloadKnowledgeDocument, editKnowledgeDocument, getKnowledgeChunks, getKnowledgeDocument, getKnowledgeDocuments, knowledgeCategories, reprocessKnowledgeDocument, replaceKnowledgeFile, setKnowledgeState, uploadKnowledgeDocument, type KnowledgeAnswer, type KnowledgeCategory, type KnowledgeDocument, type KnowledgeMetadata, type KnowledgeSource } from '../api/knowledge-api'

const categoryOptions = Object.entries(knowledgeCategories).map(([value, label]) => ({ value, label }))
const acceptedFiles = '.pdf,.docx,.txt,.md,.markdown'
const statusLabels = { PROCESSING: '解析中', READY: '已入库', FAILED: '解析失败', DELETED: '已删除' }
function errorText(error: unknown) { return error instanceof Error ? error.message : '操作失败，请重试' }
function fileSize(size: number) { return size >= 1048576 ? `${(size / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(size / 1024))} KB` }
function fileError(file: File) {
  if (!/\.(pdf|docx|txt|md|markdown)$/i.test(file.name)) return '支持 PDF、DOCX、TXT 和 Markdown 文件'
  if (!file.size || file.size > 10485760) return '文件不能为空且不能超过 10MB'
  return ''
}

export function KnowledgePage() {
  const guest = useIsGuest()
  const { role } = useAuth()
  const readOnly = guest || role === 'VIEWER'
  const cache = useQueryClient()
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('document')
  const [tab, setTab] = useState<'DOCUMENTS' | 'ASK'>('DOCUMENTS')
  const [search, setSearch] = useState('')
  const query = useDeferredValue(search.trim())
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<KnowledgeDocument | 'new' | null>(null)
  const [deleting, setDeleting] = useState<KnowledgeDocument | null>(null)
  const [notice, setNotice] = useState('')
  const [question, setQuestion] = useState('')
  const [scope, setScope] = useState('')
  const [answer, setAnswer] = useState<KnowledgeAnswer | null>(null)
  const [answeredQuestion, setAnsweredQuestion] = useState('')
  const documents = useQuery({
    queryKey: ['knowledge', 'documents', query, category, page], queryFn: () => guest ? Promise.resolve(getGuestData(`/api/v1/knowledge/documents?${new URLSearchParams({ query, category, page: String(page), size: '20' })}`) as Awaited<ReturnType<typeof getKnowledgeDocuments>>) : getKnowledgeDocuments(query, category, page),
    refetchInterval: (result) => result.state.data?.content.some((doc) => doc.status === 'PROCESSING') ? 3000 : false,
  })
  function select(id: string | null) { setParams((current) => { const next = new URLSearchParams(current); if (id) next.set('document', id); else next.delete('document'); return next }) }
  async function refresh(doc?: KnowledgeDocument) {
    setAnswer(null)
    if (doc) { cache.setQueryData(['knowledge', 'document', doc.id], doc); select(doc.id) }
    await cache.invalidateQueries({ queryKey: ['knowledge'] })
  }
  const save = useMutation({
    mutationFn: (input: { metadata: KnowledgeMetadata; file: File | null; content: string }) => editor && editor !== 'new'
      ? editKnowledgeDocument(editor.id, input.metadata)
      : input.file ? uploadKnowledgeDocument(input.metadata, input.file) : createKnowledgeText(input.metadata, input.content),
    onSuccess: async (doc) => { setEditor(null); setNotice(doc.status === 'FAILED' ? '资料已保存，解析失败，可在详情中重试。' : '资料已保存'); await refresh(doc) },
  })
  const remove = useMutation({ mutationFn: deleteKnowledgeDocument, onSuccess: async () => { setDeleting(null); select(null); setScope(''); setNotice('资料已删除'); await refresh() } })
  const ask = useMutation({
    mutationFn: (input: { question: string; documentId?: string }) => guest ? Promise.resolve(guestKnowledgeAnswer(input.question, input.documentId)) : askKnowledge(input.question, input.documentId),
    onSuccess: (result, input) => { setAnswer(result); setAnsweredQuestion(input.question); void cache.invalidateQueries({ queryKey: ['ai-model-status'] }) },
  })
  const docs = documents.data?.content ?? []

  return <section className="module-page knowledge-page">
    <DemoPageHeader demo={guest} title="企业知识库" description="汇集产品资料、销售方法与团队经验。" actions={<button className="button button-primary" disabled={readOnly} onClick={() => { save.reset(); setEditor('new') }}><Plus size={16} />新增资料</button>} />
    <div className="knowledge-tabs" role="tablist" aria-label="知识库视图"><button role="tab" aria-selected={tab === 'DOCUMENTS'} onClick={() => setTab('DOCUMENTS')}><BookOpen size={17} />资料库</button><button role="tab" aria-selected={tab === 'ASK'} onClick={() => setTab('ASK')}><MagnifyingGlass size={17} />知识问答</button></div>
    {notice && <div className="crm-inline-notice" role="status">{notice}<button onClick={() => setNotice('')}>关闭</button></div>}
    {tab === 'DOCUMENTS' ? <section className="knowledge-library" aria-label="资料库">
      <div className="module-toolbar"><div><h2>团队资料</h2><span>{documents.data?.totalElements ?? 0} 份资料</span></div><div className="toolbar-controls"><label className="compact-search"><MagnifyingGlass size={15} /><input aria-label="搜索资料" placeholder="搜索标题或文件名" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></label><SelectField className="is-compact" ariaLabel="筛选资料分类" value={category} onChange={(value) => { setCategory(value); setPage(0) }} options={[{ value: '', label: '全部分类' }, ...categoryOptions]} /><button className="icon-button" title="刷新资料" aria-label="刷新资料" onClick={() => void documents.refetch()} disabled={documents.isFetching || guest}><ArrowClockwise size={17} /></button></div></div>
      {documents.isPending ? <Empty>正在读取资料…</Empty> : documents.isError ? <Empty error>{errorText(documents.error)}</Empty> : !docs.length ? <Empty>{query || category ? '没有符合条件的资料' : '暂无资料'}</Empty> : <div className="workspace-table-wrap"><table className="workspace-table knowledge-table"><thead><tr><th>资料</th><th>分类</th><th>可见范围</th><th>状态</th><th>片段</th><th>上传者</th><th>更新时间</th></tr></thead><tbody>{docs.map((doc) => <tr key={doc.id}><td><button className="knowledge-document-link" onClick={() => select(doc.id)}><FileText size={19} /><span><strong>{doc.title}</strong><small>{doc.filename} · {fileSize(doc.sizeBytes)}</small></span></button></td><td>{knowledgeCategories[doc.category]}</td><td>{doc.visibility === 'TEAM' ? '团队共享' : '个人私有'}</td><td><DocumentStatus doc={doc} /></td><td>{doc.chunkCount}</td><td>{doc.uploadedByName}</td><td>{new Date(doc.updatedAt).toLocaleDateString('zh-CN')}</td></tr>)}</tbody></table></div>}
      {documents.data && documents.data.totalPages > 1 && <Pagination page={page} totalPages={documents.data.totalPages} onChange={setPage} />}
    </section> : <section className="knowledge-ask" aria-label="知识问答">
      <form className="knowledge-question" onSubmit={(event) => { event.preventDefault(); setAnswer(null); ask.mutate({ question: question.trim(), documentId: scope || undefined }) }}>
        <label htmlFor="knowledge-question">向知识库提问</label><textarea id="knowledge-question" required maxLength={2000} rows={3} value={question} disabled={ask.isPending} onChange={(event) => setQuestion(event.target.value)} placeholder="例如：发送报价前需要哪些审批？" />
        <div><div className="knowledge-scope"><span>检索范围</span><SelectField ariaLabel="检索范围" value={scope} onChange={setScope} disabled={ask.isPending} options={[{ value: '', label: '全部可访问资料' }, ...docs.filter((doc) => doc.status === 'READY' && doc.enabled).map((doc) => ({ value: doc.id, label: doc.title }))]} /></div><button className="button button-primary" disabled={ask.isPending || !question.trim()}><PaperPlaneRight size={16} />{ask.isPending ? '正在检索与回答…' : '提问'}</button></div>
      </form>
      {ask.isError && <p className="form-error" role="alert">{errorText(ask.error)}</p>}
      {answer && <div className="knowledge-answer"><header><CheckCircle size={18} /><h2>{answeredQuestion}</h2></header><p>{answer.content}</p>{answer.sources.length > 0 && <section className="knowledge-sources"><h3>参考资料</h3>{answer.sources.map((source, index) => <Source key={source.id} source={source} index={index} onOpen={() => select(source.documentId)} />)}</section>}{answer.usage?.totalTokens != null && <small className="knowledge-usage">本次用量 {answer.usage.totalTokens.toLocaleString()} Token</small>}</div>}
    </section>}
    {selectedId && <DocumentDrawer key={selectedId} id={selectedId} guest={guest} onClose={() => select(null)} onRefresh={refresh} onEdit={(doc) => { save.reset(); setEditor(doc) }} onDelete={(doc) => { remove.reset(); setDeleting(doc) }} onAsk={(doc) => { setScope(doc.id); setTab('ASK'); select(null) }} />}
    {editor && <DocumentEditor key={editor === 'new' ? 'new' : editor.id} doc={editor === 'new' ? null : editor} busy={save.isPending} error={save.error} onClose={() => !save.isPending && setEditor(null)} onSubmit={(input) => save.mutate(input)} />}
    {deleting && <Modal title="删除资料" busy={remove.isPending} onClose={() => !remove.isPending && setDeleting(null)}><p>确定删除「{deleting.title}」？</p>{remove.isError && <p className="form-error" role="alert">{errorText(remove.error)}</p>}<footer><button className="button button-secondary" disabled={remove.isPending} onClick={() => setDeleting(null)}>取消</button><button className="button button-primary" disabled={remove.isPending} onClick={() => remove.mutate(deleting)}><Trash size={16} />确认删除</button></footer></Modal>}
  </section>
}

function DocumentStatus({ doc }: { doc: KnowledgeDocument }) { return <span className={`knowledge-status is-${doc.enabled ? doc.status.toLowerCase() : 'disabled'}`}>{doc.enabled ? statusLabels[doc.status] : '已停用'}</span> }
function Empty({ children, error = false }: { children: ReactNode; error?: boolean }) { return <div className="knowledge-empty" role={error ? 'alert' : 'status'}>{error ? <WarningCircle size={26} /> : <BookOpen size={30} />}<p>{children}</p></div> }
function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) { return <footer className="table-pagination"><span>第 {page + 1} / {Math.max(1, totalPages)} 页</span><div><button disabled={!page} onClick={() => onChange(page - 1)}>上一页</button><button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>下一页</button></div></footer> }
function Source({ source, index, onOpen }: { source: KnowledgeSource; index: number; onOpen: () => void }) { return <article className="knowledge-source"><button onClick={onOpen}>[资料 {index + 1}] {source.title}<span>{source.pageNumber ? `第 ${source.pageNumber} 页` : `片段 ${source.chunkIndex + 1}`} · 第 {source.revision} 版</span></button><details><summary>查看引用原文</summary><p>{source.excerpt}</p></details></article> }

function DocumentDrawer({ id, guest, onClose, onRefresh, onEdit, onDelete, onAsk }: { id: string; guest: boolean; onClose: () => void; onRefresh: (doc?: KnowledgeDocument) => Promise<void>; onEdit: (doc: KnowledgeDocument) => void; onDelete: (doc: KnowledgeDocument) => void; onAsk: (doc: KnowledgeDocument) => void }) {
  const [page, setPage] = useState(0)
  const [file, setFile] = useState<File | null>(null)
  const [localError, setLocalError] = useState('')
  const detail = useQuery({ queryKey: ['knowledge', 'document', id], queryFn: () => guest ? Promise.resolve(getGuestData(`/api/v1/knowledge/documents/${id}`) as KnowledgeDocument) : getKnowledgeDocument(id), refetchInterval: (q) => q.state.data?.status === 'PROCESSING' ? 3000 : false })
  const chunks = useQuery({ queryKey: ['knowledge', 'chunks', id, page, detail.data?.revision], queryFn: () => guest ? Promise.resolve(getGuestData(`/api/v1/knowledge/documents/${id}/chunks?page=${page}&size=20`) as Awaited<ReturnType<typeof getKnowledgeChunks>>) : getKnowledgeChunks(id, page), enabled: detail.isSuccess && detail.data.status === 'READY' })
  const action = useMutation({ mutationFn: (kind: 'state' | 'reprocess' | 'replace') => kind === 'state' ? setKnowledgeState(detail.data!) : kind === 'reprocess' ? reprocessKnowledgeDocument(detail.data!) : replaceKnowledgeFile(detail.data!, file!), onSuccess: async (doc) => { setFile(null); setPage(0); await onRefresh(doc) } })
  const download = useMutation({ mutationFn: () => downloadKnowledgeDocument(detail.data!) })
  useEffect(() => { const handle = (event: KeyboardEvent) => { if (event.key === 'Escape' && !action.isPending && !document.querySelector('.knowledge-modal-backdrop')) onClose() }; window.addEventListener('keydown', handle); return () => window.removeEventListener('keydown', handle) }, [onClose, action.isPending])
  const doc = detail.data
  return <div className="crm-detail-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !action.isPending && onClose()}><aside className="knowledge-drawer" role="dialog" aria-modal="true" aria-label="资料详情"><header><h2>{doc?.title ?? '资料详情'}</h2><button className="icon-button" aria-label="关闭资料详情" onClick={onClose}><X size={20} /></button></header>
    {detail.isPending ? <Empty>正在读取资料…</Empty> : detail.isError ? <Empty error>{errorText(detail.error)}</Empty> : doc && <>
      <div className="knowledge-document-meta"><DocumentStatus doc={doc} /><span>{knowledgeCategories[doc.category]}</span><span>{doc.visibility === 'TEAM' ? '团队共享' : '个人私有'}</span><span>第 {doc.revision} 版 · {doc.chunkCount} 个片段</span></div>
      {doc.errorMessage && <p className="form-error" role="alert">{doc.errorMessage}</p>}
      <div className="knowledge-document-actions"><button className="button button-secondary" disabled={guest || download.isPending} onClick={() => download.mutate()}><DownloadSimple size={16} />下载原文件</button><button className="button button-primary" disabled={doc.status !== 'READY' || !doc.enabled} onClick={() => onAsk(doc)}><MagnifyingGlass size={16} />基于此资料提问</button></div>
      {doc.canManage && <section className="knowledge-management"><div><button className="icon-button" title="编辑资料" aria-label="编辑资料" disabled={action.isPending || doc.status === 'PROCESSING'} onClick={() => onEdit(doc)}><PencilSimple size={18} /></button><button className="icon-button" title={doc.enabled ? '停用资料' : '启用资料'} aria-label={doc.enabled ? '停用资料' : '启用资料'} disabled={action.isPending} onClick={() => action.mutate('state')}><Power size={18} /></button><button className="icon-button" title="重新解析" aria-label="重新解析" disabled={action.isPending} onClick={() => action.mutate('reprocess')}><ArrowClockwise size={18} /></button><button className="icon-button" title="删除资料" aria-label="删除资料" disabled={action.isPending} onClick={() => onDelete(doc)}><Trash size={18} /></button></div><label className="knowledge-replace"><span>更新原文件</span><input aria-label="更新原文件" type="file" accept={acceptedFiles} disabled={action.isPending || doc.status === 'PROCESSING'} onChange={(event) => { const next = event.target.files?.[0]; event.target.value = ''; if (next) { const error = fileError(next); setLocalError(error); setFile(error ? null : next) } }} /></label>{file && <div className="knowledge-selected-file"><span>{file.name}</span><button className="button button-primary" disabled={action.isPending} onClick={() => action.mutate('replace')}><UploadSimple size={15} />上传并重新解析</button></div>}</section>}
      {(action.isError || download.isError || localError) && <p className="form-error" role="alert">{localError || errorText(action.error ?? download.error)}</p>}
      {action.isPending && <p role="status">正在保存并解析资料…</p>}
      <section className="knowledge-chunks"><h3>资料原文</h3>{doc.status !== 'READY' ? <p>资料尚未完成解析</p> : chunks.isPending ? <p>正在读取片段…</p> : chunks.isError ? <p className="form-error" role="alert">{errorText(chunks.error)}</p> : <>{chunks.data?.content.map((chunk) => <article key={chunk.id}><header>片段 {chunk.chunkIndex + 1}{chunk.pageNumber ? ` · 第 ${chunk.pageNumber} 页` : ''}</header><p>{chunk.content}</p></article>)}{chunks.data && chunks.data.totalPages > 1 && <Pagination page={page} totalPages={chunks.data.totalPages} onChange={setPage} />}</>}</section>
    </>}
  </aside></div>
}

function Modal({ title, busy, onClose, children }: { title: string; busy: boolean; onClose: () => void; children: ReactNode }) {
  useEffect(() => { const handle = (event: KeyboardEvent) => { if (event.key === 'Escape' && !busy) onClose() }; window.addEventListener('keydown', handle); return () => window.removeEventListener('keydown', handle) }, [busy, onClose])
  return <div className="admin-modal-backdrop knowledge-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}><section className="admin-modal knowledge-editor" role="dialog" aria-modal="true" aria-label={title}><header><h2>{title}</h2><button className="icon-button" aria-label="关闭" disabled={busy} onClick={onClose}><X size={18} /></button></header><div className="knowledge-modal-content">{children}</div></section></div>
}

function DocumentEditor({ doc, busy, error, onClose, onSubmit }: { doc: KnowledgeDocument | null; busy: boolean; error: unknown; onClose: () => void; onSubmit: (input: { metadata: KnowledgeMetadata; file: File | null; content: string }) => void }) {
  const [mode, setMode] = useState<'FILE' | 'TEXT'>('FILE')
  const [file, setFile] = useState<File | null>(null)
  const [localError, setLocalError] = useState('')
  const [title, setTitle] = useState(doc?.title ?? '')
  const [category, setCategory] = useState<KnowledgeCategory>(doc?.category ?? 'PRODUCT')
  const [visibility, setVisibility] = useState<'TEAM' | 'PRIVATE'>(doc?.visibility ?? 'TEAM')
  const [content, setContent] = useState('')
  return <Modal title={doc ? '编辑资料' : '新增资料'} busy={busy} onClose={onClose}><form onSubmit={(event) => { event.preventDefault(); onSubmit({ metadata: { title: title.trim(), category, visibility, ...(doc ? { version: doc.version } : {}) }, file: mode === 'FILE' ? file : null, content }) }}>
    {!doc && <div className="segmented-filter" aria-label="资料来源"><button type="button" className={mode === 'FILE' ? 'is-active' : ''} disabled={busy} onClick={() => { setMode('FILE'); setLocalError('') }}><UploadSimple size={15} />上传文件</button><button type="button" className={mode === 'TEXT' ? 'is-active' : ''} disabled={busy} onClick={() => { setMode('TEXT'); setLocalError('') }}><FileText size={15} />录入文本</button></div>}
    {!doc && mode === 'FILE' && <label><span>资料文件</span><input aria-label="资料文件" type="file" accept={acceptedFiles} disabled={busy} onChange={(event) => { const next = event.target.files?.[0]; if (next) { const problem = fileError(next); setLocalError(problem); setFile(problem ? null : next); if (!problem && !title) setTitle(next.name.replace(/\.[^.]+$/, '').slice(0, 255)) } }} />{file && <small>{file.name} · {fileSize(file.size)}</small>}</label>}
    <label><span>资料标题</span><input required maxLength={255} disabled={busy} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
    <div className="knowledge-form-grid"><div className="knowledge-field"><span>分类</span><SelectField ariaLabel="分类" value={category} disabled={busy} onChange={(value) => setCategory(value as KnowledgeCategory)} options={categoryOptions} /></div><div className="knowledge-field"><span>可见范围</span><SelectField ariaLabel="可见范围" value={visibility} disabled={busy} onChange={(value) => setVisibility(value as 'TEAM' | 'PRIVATE')} options={[{ value: 'TEAM', label: '团队共享' }, { value: 'PRIVATE', label: '个人私有' }]} /></div></div>
    {!doc && mode === 'TEXT' && <label><span>资料正文</span><textarea required rows={9} maxLength={500000} disabled={busy} value={content} onChange={(event) => setContent(event.target.value)} /></label>}
    {(Boolean(error) || localError) && <p className="form-error" role="alert">{localError || errorText(error)}</p>}
    <footer><button className="button button-secondary" type="button" disabled={busy} onClick={onClose}>取消</button><button className="button button-primary" disabled={busy || !title.trim() || (!doc && (mode === 'FILE' ? !file : !content.trim()))}>{busy ? '正在保存并解析…' : doc ? '保存修改' : '保存并入库'}</button></footer>
  </form></Modal>
}
