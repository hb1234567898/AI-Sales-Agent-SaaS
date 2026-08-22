import { Robot, Tray } from '@phosphor-icons/react'

interface EmptyStateProps {
  title: string
  description: string
  actionLabel?: string
  onAction?: () => void
  compact?: boolean
  icon?: 'tray' | 'robot'
}

export function EmptyState({
  title,
  description,
  actionLabel,
  onAction,
  compact = false,
  icon = 'tray',
}: EmptyStateProps) {
  const Icon = icon === 'robot' ? Robot : Tray

  return (
    <div className={`empty-state${compact ? ' is-compact' : ''}`}>
      <span className="empty-state-icon"><Icon size={22} aria-hidden /></span>
      <strong>{title}</strong>
      <p>{description}</p>
      {actionLabel && onAction ? (
        <button className="button button-primary" type="button" onClick={onAction}>{actionLabel}</button>
      ) : null}
    </div>
  )
}
