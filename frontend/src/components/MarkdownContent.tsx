import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import './MarkdownContent.css'

export function MarkdownContent({ content }: { content: string }) {
  return <div className="markdown-content"><ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml components={{
    a: ({ href, children }) => href ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>,
    img: ({ src, alt, title }) => src ? <img src={src} alt={alt ?? ''} title={title} loading="lazy" referrerPolicy="no-referrer" /> : null,
    table: ({ children }) => <div className="markdown-table-scroll" role="region" aria-label="表格" tabIndex={0}><table>{children}</table></div>,
  }}>{content}</ReactMarkdown></div>
}
