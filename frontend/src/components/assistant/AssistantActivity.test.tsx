import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { AssistantActivity } from './AssistantActivity'

afterEach(cleanup)

describe('AssistantActivity', () => {
  it('将历史 ANSWER 规划说明转换成普通用户能理解的说明，原始信息默认折叠', async () => {
    const user = userEvent.setup()
    const summary = 'LangGraph 将本次请求识别为 ANSWER，未提出业务工具调用。'
    render(<AssistantActivity summary={summary} traces={[{ name: 'assistant.langgraph.plan', status: 'SUCCEEDED', summary: '已识别意图：ANSWER' }]} />)
    expect(screen.getByText('理解你的需求', { selector: 'b' })).toBeVisible()
    expect(screen.getByText('已分析')).toBeVisible()
    expect(screen.getAllByText('本次直接回答你的问题，没有执行客户、审批等业务操作。')[0]).toBeVisible()
    expect(screen.getByText(summary)).not.toBeVisible()
    expect(screen.getByText('assistant.langgraph.plan')).not.toBeVisible()
    await user.click(screen.getByText('技术详情'))
    expect(screen.getByText(summary)).toBeVisible()
    expect(screen.getByText('assistant.langgraph.plan')).toBeVisible()
    expect(screen.getByText('已识别意图：ANSWER')).toBeVisible()
  })

  it('不把准备待审批邮件描述为发送成功', () => {
    render(<AssistantActivity traces={[{ name: 'email.send.prepare', status: 'SUCCEEDED', summary: '已生成待审批邮件：approval-id' }]} />)
    const records = within(screen.getByRole('region', { name: '处理记录' }))
    expect(records.getByText('准备发送邮件')).toBeVisible()
    expect(records.getByText('已准备')).toBeVisible()
    expect(records.getByText('邮件已准备好，需审批通过后才能发送；本步骤不代表邮件已发送。')).toBeVisible()
  })

  it('保留真实失败原因，执行中和未执行不使用成功状态', () => {
    render(<AssistantActivity traces={[
      { name: 'email.send.prepare', status: 'FAILED', summary: '发送方案需要先选择附件' },
      { name: 'customer.search', status: 'RUNNING', summary: '正在查找客户' },
      { name: 'approval.approve', status: 'SKIPPED', summary: '等待你明确确认后再执行' },
    ]} />)
    const records = within(screen.getByRole('region', { name: '处理记录' }))
    expect(records.getByText('发送方案需要先选择附件')).toBeVisible()
    for (const label of ['未完成', '处理中', '未执行']) expect(records.getByText(label)).toBeVisible()
    expect(records.queryByText('已完成')).not.toBeInTheDocument()
  })

  it('未知操作和状态不伪装成成功，同时保留排查信息', async () => {
    const user = userEvent.setup()
    render(<AssistantActivity traces={[{ name: 'future.integration.action', status: 'QUEUED', summary: 'action queued' }]} />)
    expect(screen.getByText('状态待确认')).toBeVisible()
    expect(screen.getByText('future.integration.action')).not.toBeVisible()
    expect(screen.queryByText('已完成')).not.toBeInTheDocument()
    await user.click(screen.getByText('技术详情'))
    expect(screen.getByText('future.integration.action')).toBeVisible()
    expect(screen.getByText('QUEUED')).toBeVisible()
  })

  it('不把发起分析描述成分析结果已经成功', () => {
    render(<AssistantActivity traces={[{ name: 'agent.sales_follow_up.run', status: 'SUCCEEDED', summary: '已触发客户跟进建议 Agent：run-id' }]} />)
    expect(screen.getByText('已发起跟进分析，可在运行记录中核对结果与建议。')).toBeVisible()
    expect(screen.getByText('已发起')).toBeVisible()
  })

  it('有业务步骤时不根据 ANSWER 误报未修改数据', () => {
    render(<AssistantActivity summary="LangGraph 将本次请求识别为 ANSWER。" data={{ assistantPlanIntent: 'ANSWER' }} traces={[
      { name: 'assistant.langgraph.plan', status: 'SUCCEEDED', summary: '已识别意图：ANSWER' },
      { name: 'customer.create', status: 'SUCCEEDED', summary: '已新增客户：和成科技' },
    ]} />)
    expect(screen.queryByText('本次直接回答你的问题，没有执行客户、审批等业务操作。')).not.toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: '处理记录' })).getByText('已新增客户：和成科技')).toBeVisible()
  })

  it('执行前检查不通过时明确需要补充信息', () => {
    render(<AssistantActivity summary="LangGraph 已生成候选计划，但 Java 执行前校验未通过。" data={{ assistantPlanIntent: 'BUSINESS_ACTION', assistantPlanValidationErrors: ['缺少客户名称'] }} traces={[
      { name: 'assistant.langgraph.plan', status: 'SUCCEEDED', summary: '已识别意图：BUSINESS_ACTION' },
    ]} />)
    expect(screen.getAllByText('部分信息未通过执行前检查，请按回复补充或确认后再继续。')[0]).toBeVisible()
    expect(screen.queryByText('已完成')).not.toBeInTheDocument()
  })

  it('规划失败不声称已直接回答问题', () => {
    render(<AssistantActivity summary="LangGraph ANSWER 请求失败" data={{ assistantPlanIntent: 'ANSWER' }} traces={[
      { name: 'assistant.langgraph.plan', status: 'FAILED', summary: 'LangGraph request failed' },
    ]} />)
    expect(screen.getByText('需求分析未完成，请查看回复或技术详情了解原因。')).toBeVisible()
    expect(screen.queryByText('本次直接回答你的问题，没有执行客户、审批等业务操作。')).not.toBeInTheDocument()
  })

  it('流式步骤完成后更新展示，不保留正在处理状态', () => {
    const { rerender } = render(<AssistantActivity traces={[{ name: 'knowledge.query', status: 'RUNNING', summary: '正在检索当前可访问的企业资料' }]} />)
    expect(screen.getByText('处理中')).toBeVisible()
    rerender(<AssistantActivity traces={[{ name: 'knowledge.query', status: 'SUCCEEDED', summary: '引用 3 个知识片段' }]} />)
    expect(screen.getByText('已完成')).toBeVisible()
    expect(screen.queryByText('处理中')).not.toBeInTheDocument()
  })
})
