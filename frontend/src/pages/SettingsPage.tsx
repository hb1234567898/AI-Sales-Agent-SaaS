import { CheckCircle, Database, WarningCircle } from '@phosphor-icons/react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getAiModelStatus, testAiModelConnection } from '../api/ai-settings-api'
import { getSystemHealth } from '../api/system-api'
import { SelectField } from '../components/forms/SelectField'
import { useIsGuest } from '../auth/use-auth'

export function SettingsPage() {
  const isGuest = useIsGuest()
  const healthQuery = useQuery({ queryKey: ['system-health'], queryFn: getSystemHealth })
  const modelQuery = useQuery({ queryKey: ['ai-model-status'], queryFn: getAiModelStatus })
  const modelTest = useMutation({ mutationFn: testAiModelConnection })
  const [provider, setProvider] = useState('qwen')
  const modelStatus = modelQuery.data
  const modelStatusLabel = modelQuery.isPending
    ? '检查中'
    : modelStatus?.ready
      ? '已就绪'
      : modelStatus?.apiKeyConfigured
        ? '未启用'
        : '待配置'

  return (
    <section className="module-page settings-page">
      <header className="page-heading module-heading">
        <div><p className="eyebrow">工作区配置</p><h1>设置</h1><p>配置组织、销售规则、模型和外部系统连接。</p></div>
      </header>

      <div className="settings-content">
          <section className="surface settings-section" id="integrations">
            <div className="panel-header"><div><h2>系统连接</h2><p>管理业务数据和运行服务</p></div></div>
            <div className="integration-list">
              <div>
                <span className="integration-icon"><Database size={18} /></span>
                <span><strong>后端 API</strong><small>http://localhost:8080</small></span>
                {healthQuery.isPending ? <span className="status-badge status-loading">检查中</span> : healthQuery.isSuccess ? <span className="status-badge status-success"><CheckCircle size={11} />已连接</span> : <span className="status-badge status-error"><WarningCircle size={11} />未连接</span>}
                <button className="compact-button" type="button" onClick={() => void healthQuery.refetch()}>重新检查</button>
              </div>
              <div>
                <span className="integration-icon"><Database size={18} /></span>
                <span><strong>CRM 集成</strong><small>同步客户、联系人与商机数据</small></span>
                <span className="status-badge status-muted">未配置</span>
                <button className="compact-button" type="button" disabled>配置</button>
              </div>
            </div>
          </section>

          <section className="surface settings-section" id="model">
            <div className="panel-header">
              <div><h2>AI 模型</h2><p>当前通过阿里云百炼的 OpenAI 兼容接口接入</p></div>
              <span className={`status-badge ${modelStatus?.ready ? 'status-success' : modelQuery.isError ? 'status-error' : 'status-muted'}`}>
                {modelStatus?.ready ? <CheckCircle size={11} /> : null}{modelStatusLabel}
              </span>
            </div>
            <div className="settings-form">
              <div className="settings-field"><span>模型提供商</span><SelectField value={provider} onChange={setProvider} ariaLabel="模型提供商" disabled={isGuest} options={[{ value: 'qwen', label: '通义千问（百炼）' }]} /><small>初版固定使用千问，后续通过模型适配层扩展。</small></div>
              <label><span>模型名称</span><input type="text" value={modelStatus?.model ?? 'qwen-plus'} readOnly /><small>由后端环境变量 QWEN_MODEL 配置。</small></label>
              <label><span>API Key</span><input type="password" value={modelStatus?.apiKeyConfigured ? '********' : ''} placeholder="由服务器环境变量提供" readOnly /><small>{modelStatus?.apiKeyConfigured ? '后端已读取 QWEN_API_KEY，密钥不会返回浏览器。' : '请在后端设置 QWEN_API_KEY，并重启服务。'}</small></label>
              <label><span>API 地址</span><input type="text" value={modelStatus?.baseUrl ?? 'https://dashscope.aliyuncs.com/compatible-mode/v1'} readOnly /><small>默认使用百炼中国大陆兼容端点。</small></label>
              <div className="settings-form-actions">
                <span className={`model-test-result${modelTest.isError ? ' is-error' : ''}`} role="status">
                  {modelTest.isSuccess ? `连接成功 · ${modelTest.data.latencyMs} ms · ${modelTest.data.responsePreview}` : modelTest.isError ? modelTest.error.message : '测试会产生一次极少量模型调用。'}
                </span>
                <button className="button button-primary" type="button" disabled={isGuest || modelTest.isPending || modelQuery.isPending} title={isGuest ? '游客模式不能调用模型' : undefined} onClick={() => modelTest.mutate()}>{modelTest.isPending ? '正在连接…' : '测试连接'}</button>
              </div>
            </div>
          </section>

          <section className="surface settings-section" id="approval-policy">
            <div className="panel-header"><div><h2>审批策略</h2><p>确定哪些 Agent 动作必须经过人工确认</p></div></div>
            <div className="setting-switch-list">
              <label><span><strong>发送客户消息</strong><small>邮件、短信和企业微信消息</small></span><input type="checkbox" defaultChecked disabled={isGuest} /></label>
              <label><span><strong>更新敏感 CRM 字段</strong><small>商机金额、阶段和关闭原因</small></span><input type="checkbox" defaultChecked disabled={isGuest} /></label>
              <label><span><strong>创建内部跟进任务</strong><small>仅影响工作区内部数据</small></span><input type="checkbox" disabled={isGuest} /></label>
            </div>
          </section>

          <section className="surface settings-section" id="members">
            <div className="panel-header"><div><h2>成员权限</h2><p>当前工作区成员</p></div></div>
            <div className="member-row"><span className="user-avatar">管</span><span><strong>系统管理员</strong><small>当前用户</small></span><span className="stage-label">管理员</span></div>
          </section>
      </div>
    </section>
  )
}
