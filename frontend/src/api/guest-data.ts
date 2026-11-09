import type { Customer } from './customers-api'
import type { Lead } from './leads-api'
import type { KnowledgeAnswer, KnowledgeDocument } from './knowledge-api'

const now = new Date().toISOString()
const later = new Date(Date.now() + 86400000).toISOString()
const owners = [{ id: 'demo-owner', name: '陈默' }, { id: 'demo-owner-2', name: '李昕' }]
const customers: Customer[] = [
  { id: 'demo-customer-1', name: '云岚科技', industry: '软件服务', website: null, employeeRange: '200-500', stage: 'PROPOSAL', status: 'ACTIVE', source: 'CHAT', ownerMemberId: owners[0].id, ownerName: owners[0].name, score: 92, estimatedValue: 180000, nextAction: '确认方案范围与评审时间', lastInteractionAt: now, nextFollowUpAt: later, primaryContact: { name: '林婉清', email: 'lin@example.com', phone: '13800000001' }, createdAt: now, updatedAt: now, version: 0 },
  { id: 'demo-customer-2', name: '恒川智造', industry: '机械制造', website: null, employeeRange: '500-1000', stage: 'DISCOVERY', status: 'ACTIVE', source: 'IMPORT', ownerMemberId: owners[1].id, ownerName: owners[1].name, score: 86, estimatedValue: 260000, nextAction: '安排技术交流', lastInteractionAt: now, nextFollowUpAt: later, primaryContact: { name: '周启明', email: 'zhou@example.com', phone: '13800000002' }, createdAt: now, updatedAt: now, version: 0 },
]
const leads: Lead[] = [
  { id: 'demo-lead-1', company: '星河数字', industry: '软件服务', contactName: '张宁', contactEmail: 'zhang@example.com', contactPhone: '13800000003', contactTitle: '销售经理', source: 'IMPORT', status: 'NEW', ownerMemberId: null, ownerName: null, score: 72, nextAction: '了解客户团队规模', nextFollowUpAt: later, lastActivityAt: null, createdAt: now, updatedAt: now },
  { id: 'demo-lead-2', company: '远航制造', industry: '机械制造', contactName: '王怡', contactEmail: 'wang@example.com', contactPhone: '13800000004', contactTitle: '业务负责人', source: 'MANUAL', status: 'CONTACTED', ownerMemberId: owners[0].id, ownerName: owners[0].name, score: 83, nextAction: '预约产品演示', nextFollowUpAt: later, lastActivityAt: now, createdAt: now, updatedAt: now },
]
export const guestDocuments: KnowledgeDocument[] = [
  { id: 'demo-knowledge-1', title: '销售报价与折扣审批', category: 'POLICY', visibility: 'TEAM', status: 'READY', enabled: true, chunkCount: 1, characterCount: 49, errorMessage: null, revision: 1, version: 0, filename: '销售报价与折扣审批.txt', sizeBytes: 147, uploadedByName: '陈默', createdAt: now, updatedAt: now, canManage: false },
  { id: 'demo-knowledge-2', title: '产品介绍与常见问题', category: 'PRODUCT', visibility: 'TEAM', status: 'READY', enabled: true, chunkCount: 1, characterCount: 43, errorMessage: null, revision: 1, version: 0, filename: '产品介绍.txt', sizeBytes: 129, uploadedByName: '李昕', createdAt: now, updatedAt: now, canManage: false },
]
const guestTexts: Record<string, string> = {
  'demo-knowledge-1': '发送正式报价前需销售经理审批。低于标准价格九折时，还需销售总监审批。以上为模拟资料。',
  'demo-knowledge-2': '产品支持客户管理、跟进任务和销售协作，可根据团队需求安排产品演示。以上为模拟资料。',
}
function page<T>(items: T[], params: URLSearchParams) {
  const index = Math.max(0, Number(params.get('page')) || 0)
  const size = Math.max(1, Number(params.get('size')) || 20)
  const totalPages = Math.ceil(items.length / size)
  return { content: items.slice(index * size, (index + 1) * size), page: index, size, totalElements: items.length, totalPages, first: index === 0, last: index + 1 >= totalPages }
}
function filtered<T>(items: T[], params: URLSearchParams, text: (item: T) => string, field: (item: T) => string, filterKey: string) {
  const query = (params.get('query') ?? '').trim().toLowerCase()
  const selected = params.get(filterKey) || ''
  return items.filter(item => (!query || text(item).toLowerCase().includes(query)) && (!selected || field(item) === selected))
}
export function getGuestData(path: string, extraParams?: unknown): unknown {
  const url = new URL(path, 'http://guest.local')
  const p = url.searchParams
  if (extraParams && typeof extraParams === 'object') Object.entries(extraParams).forEach(([key, value]) => { if (value != null) p.set(key, String(value)) })
  const route = url.pathname
  if (route === '/api/v1/system/health') return { service: '模拟服务', status: 'UP', timestamp: now }
  if (route === '/api/v1/customers/metrics') return { total: 2, highIntent: 2, activeOpportunities: 2, averageScore: 89 }
  if (route === '/api/v1/customers/owners' || route === '/api/v1/leads/owners') return owners
  if (route === '/api/v1/customers') return page(filtered(customers, p, c => `${c.name} ${c.industry ?? ''} ${c.website ?? ''} ${c.primaryContact?.name ?? ''}`, c => c.stage, 'stage'), p)
  if (route === '/api/v1/leads/metrics') return { pending: 2, qualified: 0, unassigned: 1, averageScore: 78 }
  if (route === '/api/v1/leads') return page(filtered(leads, p, l => `${l.company} ${l.contactName} ${l.contactEmail ?? ''} ${l.contactPhone ?? ''}`, l => l.status, 'status'), p)
  if (route.startsWith('/api/v1/leads/')) return leads.find(l => l.id === route.split('/').at(-1)) ?? null
  if (route === '/api/v1/files') return []
  if (/\/customers\/[^/]+\/interactions\/analyses$/.test(route)) return []
  if (/\/customers\/[^/]+\/interactions$/.test(route)) {
    const customerId = route.split('/')[4]
    return page([{ id: 'demo-interaction', customerId, type: 'CHAT_IMPORT', direction: 'INBOUND', occurredAt: now, subject: '需求沟通（演示）', bodyText: '客户希望了解销售跟进和客户管理功能，并预约产品演示。', bodyPreview: '客户希望了解销售跟进和客户管理功能，并预约产品演示。', participants: ['客户', '陈默'], source: 'DEMO', createdAt: now }], p)
  }
  if (route.startsWith('/api/v1/customers/')) return customers.find(c => c.id === route.split('/').at(-1)) ?? null
  if (route === '/api/v1/follow-ups') {
    const tasks = [{ id: 'demo-task-1', customerId: customers[0].id, customerName: customers[0].name, ownerMemberId: owners[0].id, ownerName: owners[0].name, status: 'OPEN', dueAt: later, priority: 90, intentLevel: 'HIGH', riskLevel: 'LOW', reason: '客户希望确认方案', recommendedActionType: 'CALL', recommendedAction: { title: '确认方案评审时间', source: 'AI_AGENT' } }]
    return page(p.get('filter') === 'ALL' ? tasks : [], p)
  }
  if (route === '/api/v1/approvals') return page([{ id: 'demo-approval', actionRequestId: 'demo-action', runId: 'demo-run', customerId: customers[0].id, customerName: customers[0].name, actionType: 'SEND_EMAIL', riskLevel: 'HIGH', actionStatus: 'PENDING', failureCode: null, failureMessage: null, status: 'PENDING', reason: '向客户发送方案需要人工确认。', preview: { action: '发送方案确认邮件', to: 'lin@example.com', subject: '方案确认', body: '您好，感谢沟通，期待与您确认方案范围。', priority: '高' }, requester: 'AI 助手', version: 0, requestedAt: now, expiresAt: null, actionCompletedAt: null }], p)
  if (route === '/api/v1/agent-runs') return page([{ id: 'demo-run', name: '客户跟进分析', triggerType: 'MCP_ASSISTANT', status: 'WAITING_APPROVAL', businessDate: now.slice(0, 10), scope: { recentDays: 30, maxCustomers: 5 }, outputSummary: { message: '已生成 1 条待审批建议。' }, totalCandidates: 2, processedCount: 2, succeededCount: 2, skippedCount: 0, failedCount: 0, pendingApprovalCount: 1, errorMessage: null, queuedAt: now, startedAt: now, completedAt: now, createdAt: now }], p)
  if (/\/agent-runs\/[^/]+\/steps$/.test(route)) return page([{ id: 'demo-step', customerId: customers[0].id, sequenceNo: 1, stepType: 'ANALYSIS', name: '分析客户需求', status: 'SUCCEEDED', inputSnapshot: {}, outputSnapshot: {}, errorMessage: null, startedAt: now, completedAt: now, durationMs: 3200 }], p)
  if (route === '/api/v1/ai/model') return { provider: 'QWEN', model: 'qwen-plus（演示）', baseUrl: '', apiKeyConfigured: false, ready: false, status: 'MISSING_API_KEY', usage: { inputTokens: 1200, outputTokens: 500, cachedInputTokens: 0, totalTokens: 1700, successfulCalls: 4, lastCalledAt: now, remainingTokens: null, remainingStatus: '演示数据', memberAllocatedTokens: 10000, memberUsedTokens: 1700, memberRemainingTokens: 8300 } }
  if (route === '/api/v1/email/settings') return { host: null, port: null, username: null, fromAddress: null, smtpAuth: true, starttlsEnabled: true, starttlsRequired: false, passwordConfigured: false, ready: false, status: 'MISSING_CONFIGURATION' }
  if (route === '/api/v1/knowledge/documents') return page(filtered(guestDocuments, p, d => `${d.title} ${d.filename}`, d => d.category, 'category'), p)
  const knowledge = route.match(/^\/api\/v1\/knowledge\/documents\/([^/]+)(?:\/(chunks|file))?$/)
  if (knowledge) {
    const doc = guestDocuments.find(d => d.id === knowledge[1])
    if (!doc) throw new Error('演示资料不存在')
    if (knowledge[2] === 'file') return new Blob([guestTexts[doc.id]], { type: 'text/plain;charset=utf-8' })
    if (knowledge[2] === 'chunks') return page([{ id: `chunk-${doc.id}`, documentId: doc.id, chunkIndex: 0, pageNumber: null, title: doc.title, content: guestTexts[doc.id], revision: 1 }], p)
    return doc
  }
  throw new Error('游客模式暂不提供此演示数据')
}

export function guestKnowledgeAnswer(question: string, documentId?: string): KnowledgeAnswer {
  const eligible = guestDocuments.filter(doc => !documentId || doc.id === documentId)
  const match = eligible.find(doc => /报价|折扣|审批/.test(question) && doc.category === 'POLICY')
    ?? eligible.find(doc => /产品|功能|演示/.test(question) && doc.category === 'PRODUCT')
  if (!match) return { content: '演示资料中没有找到相关内容，请尝试询问报价审批或产品功能。', sources: [], usage: null }
  const excerpt = guestTexts[match.id]
  return { content: `演示回答：${excerpt} [资料 1]`, usage: null, sources: [{ id: `chunk-${match.id}`, documentId: match.id, title: match.title, chunkIndex: 0, pageNumber: null, excerpt, score: 1, revision: 1 }] }
}
