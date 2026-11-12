import { useAuth } from '../../auth/use-auth'
import type { Money } from '../../api/commercial-api'

export const money = (value: Money | null | undefined) => value == null ? '—' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value))
export const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
export const dateTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }) : '—'
export const message = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'
export function useCommercialRole() {
  const { role, memberId } = useAuth()
  const manager = ['OWNER', 'ADMIN', 'MANAGER'].includes(role)
  return { manager, writer: manager || role === 'SALES', guest: role === 'GUEST', memberId }
}
const labels: Record<string, string> = { DRAFT: '草稿', ISSUED: '已定稿', ACCEPTED: '客户已确认', REJECTED: '客户已拒绝', CANCELLED: '已取消', SIGNED: '已签约', COMPLETED: '已结清', PAID: '已付清', OVERDUE: '已逾期', PARTIAL: '部分回款', UNPAID: '待回款', PENDING: '待审批', APPROVED: '已批准', SUCCEEDED: '已执行', FAILED: '执行失败', SENDING: '发送中', SENT: '已发送', UNKNOWN: '结果待核对', AWAITING_APPROVAL: '待审批', EXECUTING: '执行中' }
export const statusLabel = (status: string) => labels[status] ?? status
