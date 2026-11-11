import { CaretRight, CheckCircle, CircleNotch, DotsThree, MinusCircle, Question, WarningCircle } from '@phosphor-icons/react'
import type { AssistantToolTrace } from '../../api/mcp-chat-api'
import './assistant-activity.css'

interface Props {
  summary?: string | null
  traces: AssistantToolTrace[]
  data?: Record<string, unknown>
}

const steps: Record<string, { title: string; result: string }> = {
  'assistant.langgraph.plan': { title: '理解你的需求', result: '已分析你的需求，后续操作请查看下方记录。' },
  'assistant.router': { title: '查看可用功能', result: '已提供可用功能与示例，本步骤没有修改业务资料。' },
  'assistant.history.parse': { title: '读取历史处理记录', result: '已读取历史处理记录。' },
  'customer.search': { title: '查找客户', result: '已完成客户查询，匹配情况请查看回复。' },
  'customer.create': { title: '新增客户档案', result: '已创建客户档案，可在客户列表中查看。' },
  'interaction.chat_import': { title: '保存客户聊天记录', result: '聊天记录已保存，可在客户的互动记录中查看。' },
  'agent.sales_follow_up.run': { title: '发起客户跟进分析', result: '已发起跟进分析，可在运行记录中核对结果与建议。' },
  'email.send.prepare': { title: '准备发送邮件', result: '邮件已准备好，需审批通过后才能发送；本步骤不代表邮件已发送。' },
  'approval.list': { title: '查询待审批事项', result: '已查询待审批事项，具体数量请查看回复。' },
  'approval.approve': { title: '审批并处理后续操作', result: '已完成审批处理，后续操作结果请查看回复或审批记录。' },
  'follow_up.list': { title: '查询跟进任务', result: '已查询跟进任务，具体结果请查看回复。' },
  'knowledge.query': { title: '查阅企业知识库', result: '已完成资料查询，回答和引用来源请查看回复。' },
}

function isTechnical(text: string) {
  return /LangGraph|\bJava\b|\b(?:ANSWER|BUSINESS_ACTION|KNOWLEDGE_QUERY|CLARIFY)\b|[a-z_]+\.[a-z_]+|\b[0-9a-f]{8}-[0-9a-f-]{27,}\b/i.test(text)
}

function intentOf(data: Props['data'], traces: AssistantToolTrace[]) {
  if (typeof data?.assistantPlanIntent === 'string') return data.assistantPlanIntent
  if (typeof data?.intent === 'string') return data.intent
  // Older conversation records only include the intent in the planner trace.
  return traces.find(trace => trace.name === 'assistant.langgraph.plan')?.summary
    .match(/(?:已识别意图：|识别为\s+)(ANSWER|BUSINESS_ACTION|KNOWLEDGE_QUERY|CLARIFY)\b/)?.[1]
}

function plannerDescription(intent: string | undefined, hasOtherSteps: boolean, blocked: boolean) {
  if (blocked) return '部分信息未通过执行前检查，请按回复补充或确认后再继续。'
  if (hasOtherSteps) return '已分析你的需求，具体操作及结果见下方处理记录。'
  if (intent === 'ANSWER') return '本次直接回答你的问题，没有执行客户、审批等业务操作。'
  if (intent === 'CLARIFY') return '还需要你补充信息，暂未执行后续操作。'
  if (intent === 'KNOWLEDGE_QUERY') return '已识别为资料查询，资料检索结果以下方记录为准。'
  return '已分析你的需求；是否执行了业务操作，请以下方记录和回复为准。'
}

function traceStatus(trace: AssistantToolTrace) {
  const status = trace.status.toUpperCase()
  if (status === 'RUNNING') return { tone: 'running', label: '处理中', Icon: CircleNotch }
  if (status === 'FAILED') return { tone: 'failed', label: '未完成', Icon: WarningCircle }
  if (status === 'SKIPPED') return { tone: 'skipped', label: '未执行', Icon: MinusCircle }
  if (status === 'SUCCEEDED') {
    const label = trace.name === 'assistant.langgraph.plan' ? '已分析'
      : trace.name === 'email.send.prepare' ? '已准备'
        : trace.name === 'agent.sales_follow_up.run' ? '已发起' : '已完成'
    return { tone: 'succeeded', label, Icon: CheckCircle }
  }
  return { tone: 'unknown', label: '状态待确认', Icon: Question }
}

export function AssistantActivity({ summary, traces, data }: Props) {
  const intent = intentOf(data, traces)
  const hasOtherSteps = traces.some(trace => trace.name !== 'assistant.langgraph.plan')
  const blocked = (Array.isArray(data?.assistantPlanValidationErrors) && data.assistantPlanValidationErrors.length > 0)
    || /阻止执行|校验未通过|校验.*失败/.test(summary ?? '')
  const planner = traces.find(trace => trace.name === 'assistant.langgraph.plan')
  const plannerState = planner?.status.toUpperCase()
  const planning = plannerState === 'FAILED' ? '需求分析未完成，请查看回复或技术详情了解原因。'
    : plannerState === 'RUNNING' ? '正在理解你的需求，后续操作尚未确认。'
      : plannerState === 'SKIPPED' ? '本次没有执行需求分析，请查看回复了解原因。'
        : plannerState && plannerState !== 'SUCCEEDED' ? '需求分析状态尚未确认，请核对回复或技术详情。'
          : plannerDescription(intent, hasOtherSteps, blocked)
  const readableSummary = summary && isTechnical(summary) ? planning : summary
  if (!summary && !traces.length) return null

  function description(trace: AssistantToolTrace) {
    const status = trace.status.toUpperCase()
    if (!['SUCCEEDED', 'RUNNING', 'FAILED', 'SKIPPED'].includes(status)) return '暂时无法确认本步骤的结果，请核对回复或业务记录。'
    if (trace.name === 'assistant.langgraph.plan' && (status === 'SUCCEEDED' || !trace.summary || isTechnical(trace.summary))) return planning
    if (status === 'SUCCEEDED' && ['interaction.chat_import', 'agent.sales_follow_up.run', 'email.send.prepare'].includes(trace.name)) return steps[trace.name].result
    if (trace.summary && !isTechnical(trace.summary)) return trace.summary.replace(/\bAgent\b/g, '销售助手')
      .replace(/引用\s*(\d+)\s*个知识片段/g, '已找到并引用 $1 段相关资料').replace(/知识片段/g, '相关资料')
    if (status === 'FAILED') return '本步骤未完成，请核对回复和业务记录；具体原因可展开技术详情查看。'
    if (status === 'RUNNING') return '正在处理，结果确认前请勿重复提交。'
    if (status === 'SKIPPED') return '本步骤未执行。'
    return steps[trace.name]?.result ?? '本步骤处理已结束，具体业务结果请查看回复；操作标识保留在技术详情中。'
  }

  return <div className="mcp-activity">
    {readableSummary && !(planner && summary && isTechnical(summary)) && <section className="mcp-reasoning">
      <strong><DotsThree size={15} />执行过程</strong>
      <span>{readableSummary}</span>
    </section>}
    {traces.length > 0 && <section className="mcp-activity-records" aria-label="处理记录">
      <strong>处理记录</strong>
      <ol>{traces.map((trace, index) => {
        const { tone, label, Icon } = traceStatus(trace)
        return <li key={`${trace.name}-${index}`} className={`mcp-activity-row is-${tone}`}>
          <Icon size={17} className={tone === 'running' ? 'mcp-spin' : undefined} aria-hidden />
          <div><div className="mcp-activity-head"><b>{steps[trace.name]?.title ?? '处理请求'}</b><em>{label}</em></div>
            <p>{description(trace)}</p>
          </div>
        </li>
      })}</ol>
    </section>}
    {(traces.length > 0 || (summary && isTechnical(summary))) && <details className="mcp-activity-details">
      <summary><CaretRight size={13} />技术详情</summary>
      <div>
        {summary && <section><strong>原始处理说明</strong><p>{summary}</p></section>}
        {traces.map((trace, index) => <section key={`${trace.name}-${index}`}>
          <strong>{steps[trace.name]?.title ?? '处理请求'}</strong>
          <dl><dt>工具名称</dt><dd><code>{trace.name}</code></dd><dt>原始状态</dt><dd><code>{trace.status}</code></dd><dt>原始记录</dt><dd>{trace.summary || '无'}</dd></dl>
        </section>)}
      </div>
    </details>}
  </div>
}
