import type { ReactNode } from 'react'

interface DemoPageHeaderProps {
  title: string
  description: string
  actions?: ReactNode
  demo?: boolean
}

export function DemoPageHeader({ title, description, actions, demo = true }: DemoPageHeaderProps) {
  return (
    <header className="page-heading module-heading">
      <div>
        <div className="eyebrow-row">
          <p className="eyebrow">销售运营中心</p>
          {demo ? <span className="demo-badge">演示数据</span> : null}
        </div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
  )
}
