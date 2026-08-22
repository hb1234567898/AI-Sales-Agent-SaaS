import { EmptyState } from '../components/feedback/EmptyState'

interface WorkspacePageProps {
  title: string
  description: string
  emptyTitle: string
  emptyDescription: string
}

export function WorkspacePage({ title, description, emptyTitle, emptyDescription }: WorkspacePageProps) {
  return (
    <section className="workspace-page">
      <header className="page-heading">
        <div><p className="eyebrow">Sales Agent</p><h1>{title}</h1><p>{description}</p></div>
      </header>
      <section className="surface workspace-empty-panel">
        <EmptyState title={emptyTitle} description={emptyDescription} />
      </section>
    </section>
  )
}
