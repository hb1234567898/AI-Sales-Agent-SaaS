import type { ReactNode } from 'react'

interface DemoPageHeaderProps {
  title: string
  description: string
  actions?: ReactNode
}

export function DemoPageHeader({ title, description, actions }: DemoPageHeaderProps) {
  return (
    <header className="page-heading module-heading">
      <div>
        <div className="eyebrow-row">
          <p className="eyebrow">销售运营中心</p>
          <span className="demo-badge">演示数据</span>
        </div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </header>
  )
}
