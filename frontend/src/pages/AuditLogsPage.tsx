import { ClockCounterClockwise, MagnifyingGlass, ShieldCheck, WarningCircle } from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getAuditEvents, type AuditEvent } from '../api/audit-logs-api'
import { SelectField } from '../components/forms/SelectField'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

const actionOptions = [
  { value: '', label: '全部动作' },
  { value: 'HTTP_POST', label: '新增 / 执行' },
  { value: 'HTTP_PUT', label: '修改' },
  { value: 'HTTP_PATCH', label: '局部修改' },
  { value: 'HTTP_DELETE', label: '删除' },
]

const targetOptions = [
  { value: '', label: '全部资源' },
  { value: 'CUSTOMER', label: '客户' },
  { value: 'INTERACTION', label: '互动记录' },
  { value: 'CHAT_IMPORT', label: '聊天导入' },
  { value: 'CHAT_ANALYSIS', label: '聊天分析' },
  { value: 'AI_MODEL', label: 'AI 模型配置' },
  { value: 'AI_MODEL_TEST', label: '模型连接测试' },
  { value: 'TEAM_MEMBER', label: '成员管理' },
  { value: 'TEAM', label: '团队管理' },
  { value: 'AGENT_RUN', label: 'Agent 运行' },
  { value: 'APPROVAL', label: '审批' },
  { value: 'FOLLOW_UP', label: '跟进任务' },
  { value: 'AUTH_SESSION', label: '登录会话' },
  { value: 'API', label: '其他接口' },
]

const resultOptions = [
  { value: '', label: '全部结果' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'DENIED', label: '拒绝' },
]

const resultLabels = {
  SUCCEEDED: '成功',
  FAILED: '失败',
  DENIED: '拒绝',
} as const

const targetLabels: Record<string, string> = {
  CUSTOMER: '客户',
  INTERACTION: '互动记录',
  CHAT_IMPORT: '聊天导入',
  CHAT_ANALYSIS: '聊天分析',
  AI_MODEL: 'AI 模型配置',
  AI_MODEL_TEST: '模型连接测试',
  TEAM_MEMBER: '成员管理',
  TEAM: '团队管理',
  AGENT_RUN: 'Agent 运行',
  APPROVAL: '审批',
  FOLLOW_UP: '跟进任务',
  AUTH_SESSION: '登录会话',
  API: '其他接口',
}

export function AuditLogsPage() {
  const [keyword, setKeyword] = useState('')
  const [action, setAction] = useState('')
  const [targetType, setTargetType] = useState('')
  const [result, setResult] = useState('')
  const [page, setPage] = useState(0)
  const query = useQuery({
    queryKey: ['audit-events', keyword, action, targetType, result, page],
    queryFn: () => getAuditEvents({ keyword: keyword.trim(), action, targetType, result, page, size: 20 }),
    placeholderData: (previous) => previous,
  })
  const data = query.data

  function resetPage(next: () => void) {
    next()
    setPage(0)
  }

  return (
    <section className="module-page audit-page">
      <DemoPageHeader
        title="日志管理"
        description="追踪成员在系统中的新增、修改、导入、审批和配置操作。"
        actions={<button className="button button-secondary" type="button" onClick={() => void query.refetch()}><ClockCounterClockwise size={16} />刷新</button>}
      />

      <section className="surface audit-panel">
        <div className="audit-filters">
          <label className="audit-search">
            <MagnifyingGlass size={16} aria-hidden />
            <input
              type="search"
              value={keyword}
              onChange={(event) => resetPage(() => setKeyword(event.target.value))}
              placeholder="搜索操作人、动作、资源或请求 ID"
              aria-label="搜索日志"
            />
          </label>
          <SelectField value={action} onChange={(value) => resetPage(() => setAction(value))} ariaLabel="动作类型" options={actionOptions} />
          <SelectField value={targetType} onChange={(value) => resetPage(() => setTargetType(value))} ariaLabel="资源类型" options={targetOptions} />
          <SelectField value={result} onChange={(value) => resetPage(() => setResult(value))} ariaLabel="执行结果" options={resultOptions} />
        </div>

        <div className="audit-table-wrap">
          <table className="audit-table">
            <thead>
              <tr>
                <th>操作</th>
                <th>资源</th>
                <th>操作人</th>
                <th>结果</th>
                <th>耗时</th>
                <th>来源</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((event) => <AuditRow key={event.id} event={event} />)}
            </tbody>
          </table>
          {query.isPending ? <AuditState text="正在读取操作日志…" /> : null}
          {query.isError ? <AuditState tone="error" text="日志读取失败，请稍后重试。" /> : null}
          {data && data.content.length === 0 ? <AuditState text="暂无符合条件的操作日志。" /> : null}
        </div>

        <div className="table-pagination">
          <div>
            <strong>{data?.totalElements ?? 0}</strong>
            <span>条日志</span>
          </div>
          <div>
            <button type="button" disabled={!data || data.first || query.isFetching} onClick={() => setPage((current) => Math.max(0, current - 1))}>上一页</button>
            <span>第 {(data?.page ?? page) + 1} 页 / 共 {Math.max(data?.totalPages ?? 1, 1)} 页</span>
            <button type="button" disabled={!data || data.last || query.isFetching} onClick={() => setPage((current) => current + 1)}>下一页</button>
          </div>
        </div>
      </section>
    </section>
  )
}

function AuditRow({ event }: { event: AuditEvent }) {
  const duration = typeof event.metadata.durationMs === 'number' ? `${event.metadata.durationMs} ms` : '-'
  const route = typeof event.metadata.routePattern === 'string' ? event.metadata.routePattern : event.metadata.path
  const status = typeof event.metadata.status === 'number' ? event.metadata.status : null
  const resource = resourceLabel(event)

  return (
    <tr>
      <td>
        <strong>{actionLabel(event)}</strong>
        <small>{typeof route === 'string' ? route : event.action}</small>
      </td>
      <td>
        <span>{resource.label}</span>
        <small title={resource.detail}>{resource.detail}</small>
      </td>
      <td>
        <span>{event.actorIdentifier ?? '未知用户'}</span>
        <small>{event.requestId ?? '无请求 ID'}</small>
      </td>
      <td>
        <span className={`audit-result result-${event.result.toLowerCase()}`}>
          {event.result === 'SUCCEEDED' ? <ShieldCheck size={13} /> : <WarningCircle size={13} />}
          {resultLabels[event.result]}
          {status ? ` · ${status}` : ''}
        </span>
      </td>
      <td>{duration}</td>
      <td>
        <span>{event.ipAddress ?? '-'}</span>
        <small title={event.userAgent ?? ''}>{event.userAgent ? userAgentLabel(event.userAgent) : '无 User-Agent'}</small>
      </td>
      <td>{formatDateTime(event.occurredAt)}</td>
    </tr>
  )
}

function AuditState({ text, tone }: { text: string; tone?: 'error' }) {
  return (
    <div className={`audit-state${tone === 'error' ? ' is-error' : ''}`}>
      <strong>{text}</strong>
    </div>
  )
}

function actionLabel(event: AuditEvent) {
  const route = auditRoute(event)
  if (route.endsWith('/chat-import')) return '导入聊天'
  if (route.endsWith('/analysis')) return 'AI 分析'
  if (route.includes('/analysis/') && route.endsWith('/apply')) return '确认分析'
  if (route.endsWith('/ai/model/test')) return '测试连接'

  const action = event.action
  if (action === 'HTTP_POST') return '新增 / 执行'
  if (action === 'HTTP_PUT') return '修改'
  if (action === 'HTTP_PATCH') return '局部修改'
  if (action === 'HTTP_DELETE') return '删除'
  return action
}

function resourceLabel(event: AuditEvent) {
  const inferredType = event.targetType === 'API' ? inferTargetTypeFromRoute(auditRoute(event)) : event.targetType
  const label = targetLabels[inferredType] ?? inferredType
  const route = auditRoute(event)
  const detail = event.targetId && event.targetId !== 'N/A'
    ? shortId(event.targetId)
    : route || '未记录资源 ID'
  return { label, detail }
}

function auditRoute(event: AuditEvent) {
  const route = typeof event.metadata.routePattern === 'string' ? event.metadata.routePattern : event.metadata.path
  return typeof route === 'string' ? route : ''
}

function inferTargetTypeFromRoute(route: string) {
  if (route.startsWith('/api/v1/admin/members')) return 'TEAM_MEMBER'
  if (route.startsWith('/api/v1/admin/team')) return 'TEAM'
  if (route.startsWith('/api/v1/agent-runs')) return 'AGENT_RUN'
  if (route.startsWith('/api/v1/approvals')) return 'APPROVAL'
  if (route.startsWith('/api/v1/follow-ups')) return 'FOLLOW_UP'
  if (route.startsWith('/api/v1/ai/model/test')) return 'AI_MODEL_TEST'
  if (route.startsWith('/api/v1/ai/model')) return 'AI_MODEL'
  if (route === '/api/v1/auth/logout') return 'AUTH_SESSION'
  if (route.endsWith('/chat-import')) return 'CHAT_IMPORT'
  if (route.includes('/analysis/')) return 'CHAT_ANALYSIS'
  if (route.endsWith('/analysis')) return 'CHAT_ANALYSIS'
  if (route.includes('/interactions')) return 'INTERACTION'
  if (route.startsWith('/api/v1/customers')) return 'CUSTOMER'
  return 'API'
}

function shortId(value: string) {
  return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value
}

function userAgentLabel(value: string) {
  if (value.includes('Edg/')) return 'Microsoft Edge'
  if (value.includes('Chrome/')) return 'Chrome'
  if (value.includes('Firefox/')) return 'Firefox'
  return value.slice(0, 28)
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
