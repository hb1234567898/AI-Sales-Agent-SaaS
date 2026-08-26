import {
  CaretLeft,
  CaretRight,
  MagnifyingGlass,
  PencilSimple,
  Plus,
  SpinnerGap,
  UploadSimple,
  UsersThree,
  WarningCircle,
} from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import {
  createCustomer,
  getCustomer,
  getCustomerMetrics,
  getCustomerOwners,
  getCustomers,
  type Customer,
  type CustomerStage,
  type CustomerUpsertInput,
  updateCustomer,
} from '../api/customers-api'
import { CustomerDrawer } from '../components/customers/CustomerDrawer'
import { SelectField } from '../components/forms/SelectField'
import { DemoPageHeader } from '../components/layout/DemoPageHeader'

const stageLabels: Record<CustomerStage, string> = {
  LEAD: '初步接触',
  QUALIFIED: '需求确认',
  DISCOVERY: '需求调研',
  DEMO: '产品试用',
  PROPOSAL: '方案确认',
  NEGOTIATION: '商务谈判',
  WON: '已成交',
  LOST: '已流失',
}

const stageOptions = [
  { value: '', label: '全部阶段' },
  ...Object.entries(stageLabels).map(([value, label]) => ({ value, label })),
]

const currencyFormatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  maximumFractionDigits: 0,
})

function formatCurrency(value: number | null) {
  if (value === null) return '—'
  if (value >= 10_000) return `¥ ${(value / 10_000).toFixed(1)} 万`
  return currencyFormatter.format(value)
}

function formatRelativeTime(value: string | null) {
  if (!value) return '暂无互动'
  const elapsed = Date.now() - new Date(value).getTime()
  const minutes = Math.floor(elapsed / 60_000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  return days < 30 ? `${days} 天前` : new Date(value).toLocaleDateString('zh-CN')
}

type DrawerState = { mode: 'create' } | { mode: 'edit'; customerId: string }

export function CustomersPage() {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query.trim())
  const [stage, setStage] = useState('')
  const [page, setPage] = useState(0)
  const [drawer, setDrawer] = useState<DrawerState | null>(null)

  const customersQuery = useQuery({
    queryKey: ['customers', deferredQuery, stage, page],
    queryFn: () => getCustomers({
      query: deferredQuery,
      stage: stage ? stage as CustomerStage : undefined,
      page,
      size: 10,
    }),
  })
  const metricsQuery = useQuery({ queryKey: ['customer-metrics'], queryFn: getCustomerMetrics })
  const ownersQuery = useQuery({ queryKey: ['customer-owners'], queryFn: getCustomerOwners })
  const selectedCustomerQuery = useQuery({
    queryKey: ['customer', drawer?.mode === 'edit' ? drawer.customerId : null],
    queryFn: () => getCustomer((drawer as Extract<DrawerState, { mode: 'edit' }>).customerId),
    enabled: drawer?.mode === 'edit',
  })

  const saveMutation = useMutation({
    mutationFn: ({ currentDrawer, input }: { currentDrawer: DrawerState; input: CustomerUpsertInput }) => (
      currentDrawer.mode === 'create'
        ? createCustomer(input)
        : updateCustomer(currentDrawer.customerId, input)
    ),
    onSuccess: async (customer) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['customers'] }),
        queryClient.invalidateQueries({ queryKey: ['customer-metrics'] }),
      ])
      queryClient.setQueryData(['customer', customer.id], customer)
      setDrawer(null)
    },
  })

  const customers = customersQuery.data?.content ?? []
  const metrics = metricsQuery.data

  async function saveCustomer(input: CustomerUpsertInput) {
    if (!drawer) return
    await saveMutation.mutateAsync({ currentDrawer: drawer, input })
  }

  function openEditor(customer: Customer) {
    saveMutation.reset()
    setDrawer({ mode: 'edit', customerId: customer.id })
  }

  return (
    <section className="module-page">
      <DemoPageHeader
        title="客户"
        description="统一查看企业、联系人、商机阶段与下一步动作。"
        actions={(
          <>
            <button className="button button-secondary" type="button" disabled title="将在客户导入迭代开放"><UploadSimple size={16} />导入客户</button>
            <button className="button button-primary" type="button" onClick={() => { saveMutation.reset(); setDrawer({ mode: 'create' }) }}><Plus size={16} />添加客户</button>
          </>
        )}
      />

      <div className="module-stat-grid" aria-label="客户指标">
        <div><span>客户总数</span><strong>{metrics?.total ?? '—'}</strong><small>来自当前组织的实时数据</small></div>
        <div><span>高意向客户</span><strong>{metrics?.highIntent ?? '—'}</strong><small>意向评分达到 80 分</small></div>
        <div><span>活跃商机</span><strong>{metrics?.activeOpportunities ?? '—'}</strong><small>需求确认至商务谈判</small></div>
        <div><span>平均客户评分</span><strong>{metrics?.averageScore?.toFixed(1) ?? '—'}</strong><small>基于当前有效客户</small></div>
      </div>

      <section className="surface module-panel">
        <div className="module-toolbar">
          <div>
            <h2>客户列表</h2>
            <span>{customersQuery.data ? `共 ${customersQuery.data.totalElements} 条真实记录` : '正在读取数据库'}</span>
          </div>
          <div className="toolbar-controls">
            <label className="compact-search">
              <MagnifyingGlass size={15} aria-hidden />
              <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="搜索企业、行业或网站" aria-label="搜索客户" />
            </label>
            <SelectField className="is-compact" value={stage} onChange={(value) => { setStage(value); setPage(0) }} ariaLabel="筛选商机阶段" options={stageOptions} />
          </div>
        </div>

        {customersQuery.isPending ? (
          <div className="customer-query-state"><SpinnerGap className="is-spinning" size={22} /><strong>正在加载客户</strong><span>从数据库获取最新客户资料…</span></div>
        ) : customersQuery.isError ? (
          <div className="customer-query-state is-error"><WarningCircle size={22} /><strong>客户数据加载失败</strong><span>{customersQuery.error.message}</span><button className="compact-button is-primary" type="button" onClick={() => void customersQuery.refetch()}>重新加载</button></div>
        ) : (
          <>
            <div className="workspace-table-wrap">
              <table className="workspace-table customers-table">
                <thead><tr><th>客户</th><th>商机阶段</th><th>意向评分</th><th>预计金额</th><th>最近互动</th><th>负责人</th><th>下一步动作</th><th aria-label="操作" /></tr></thead>
                <tbody>
                  {customers.map((customer) => (
                    <tr key={customer.id}>
                      <td><button className="customer-cell customer-link" type="button" onClick={() => openEditor(customer)}><span className="company-avatar">{customer.name.slice(0, 1)}</span><span><strong>{customer.name}</strong><small>{customer.primaryContact?.name ?? '暂无联系人'} · {customer.industry ?? '未设置行业'}</small></span></button></td>
                      <td><span className="stage-label">{stageLabels[customer.stage]}</span></td>
                      <td><strong className="mono-value">{customer.score ?? '—'}</strong></td>
                      <td>{formatCurrency(customer.estimatedValue)}</td>
                      <td className="muted-cell">{formatRelativeTime(customer.lastInteractionAt)}</td>
                      <td>{customer.ownerName ?? '暂未分配'}</td>
                      <td><span className="action-copy">{customer.nextAction ?? '尚未设置'}</span></td>
                      <td><button className="table-action-button" type="button" onClick={() => openEditor(customer)} aria-label={`编辑 ${customer.name}`}><PencilSimple size={15} /></button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {customers.length === 0 ? <div className="filter-empty"><UsersThree size={22} /><strong>没有匹配的客户</strong><span>请调整搜索词或商机阶段，或者添加第一位客户。</span></div> : null}
            {customersQuery.data && customersQuery.data.totalPages > 0 ? (
              <footer className="table-pagination">
                <span>第 {customersQuery.data.page + 1} / {customersQuery.data.totalPages} 页</span>
                <div>
                  <button type="button" disabled={customersQuery.data.first} onClick={() => setPage((current) => Math.max(0, current - 1))}><CaretLeft size={14} />上一页</button>
                  <button type="button" disabled={customersQuery.data.last} onClick={() => setPage((current) => current + 1)}>下一页<CaretRight size={14} /></button>
                </div>
              </footer>
            ) : null}
          </>
        )}
      </section>

      {drawer ? (
        <CustomerDrawer
          key={drawer.mode === 'create' ? 'create' : `${drawer.customerId}-${selectedCustomerQuery.data?.version ?? 'loading'}`}
          mode={drawer.mode}
          customer={drawer.mode === 'edit' ? selectedCustomerQuery.data : null}
          owners={ownersQuery.data ?? []}
          pending={saveMutation.isPending}
          loading={drawer.mode === 'edit' && selectedCustomerQuery.isPending}
          error={saveMutation.error?.message ?? selectedCustomerQuery.error?.message}
          onClose={() => setDrawer(null)}
          onSubmit={saveCustomer}
        />
      ) : null}
    </section>
  )
}
