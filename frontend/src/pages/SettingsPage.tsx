import { CheckCircle, Database, WarningCircle } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getAiModelStatus, saveAiModelConfiguration, testAiModelConnection, type AiModelStatus } from '../api/ai-settings-api'
import { getSystemHealth } from '../api/system-api'
import { SelectField } from '../components/forms/SelectField'
import { useIsGuest } from '../auth/use-auth'

interface AiModelSettingsFormProps {
  status?: AiModelStatus
  isGuest: boolean
}

function AiModelSettingsForm({ status, isGuest }: AiModelSettingsFormProps) {
  const queryClient = useQueryClient()
  const [provider, setProvider] = useState<string>(status?.provider ?? 'QWEN')
  const [model, setModel] = useState(status?.model ?? 'qwen-plus')
  const [baseUrl, setBaseUrl] = useState(status?.baseUrl ?? 'https://dashscope.aliyuncs.com/compatible-mode/v1')
  const [apiKey, setApiKey] = useState('')
  const modelTest = useMutation({ mutationFn: testAiModelConnection })
  const modelSave = useMutation({
    mutationFn: saveAiModelConfiguration,
    onSuccess: (savedStatus) => {
      queryClient.setQueryData(['ai-model-status'], savedStatus)
      setApiKey('')
      modelTest.reset()
    },
  })

  function saveModel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    modelSave.mutate({
      provider: 'QWEN',
      model: model.trim(),
      baseUrl: baseUrl.trim(),
      apiKey: apiKey.trim() || undefined,
    })
  }

  return (
    <form className="settings-form" onSubmit={saveModel}>
      <div className="settings-field"><span>模型提供商</span><SelectField value={provider} onChange={setProvider} ariaLabel="模型提供商" disabled={isGuest || modelSave.isPending} options={[{ value: 'QWEN', label: '通义千问（百炼）' }]} /><small>初版固定使用千问，后续通过模型适配层扩展。</small></div>
      <label><span>模型名称</span><input aria-label="模型名称" type="text" required maxLength={120} value={model} disabled={isGuest || modelSave.isPending} onChange={(event) => setModel(event.target.value)} placeholder="例如：qwen3.7-plus" /><small>保存后立即用于新的模型调用，无需重启服务。</small></label>
      <label><span>API Key</span><input aria-label="API Key" type="password" maxLength={500} autoComplete="new-password" value={apiKey} disabled={isGuest || modelSave.isPending} onChange={(event) => setApiKey(event.target.value)} placeholder={status?.apiKeyConfigured ? '已加密保存，留空表示不修改' : '请输入百炼 API Key'} /><small>{status?.apiKeyConfigured ? '密钥已加密保存，页面不会读取或回显原文。' : '首次保存必须输入 Key，数据库只保存 AES-GCM 密文。'}</small></label>
      <label><span>API 地址</span><input aria-label="API 地址" type="url" required maxLength={500} value={baseUrl} disabled={isGuest || modelSave.isPending} onChange={(event) => setBaseUrl(event.target.value)} /><small>仅接受 HTTPS 地址，默认使用百炼中国大陆兼容端点。</small></label>
      <div className="settings-form-actions">
        <span className={`model-test-result${modelTest.isError || modelSave.isError ? ' is-error' : ''}`} role="status">
          {modelTest.isSuccess ? `连接成功 · ${modelTest.data.latencyMs} ms · ${modelTest.data.responsePreview}` : modelTest.isError ? modelTest.error.message : modelSave.isSuccess ? '配置已加密保存。' : modelSave.isError ? modelSave.error.message : '请先保存配置，再测试连接。测试会产生一次极少量模型调用。'}
        </span>
        <button className="button button-secondary" type="button" disabled={isGuest || !status?.apiKeyConfigured || modelTest.isPending || modelSave.isPending} title={isGuest ? '游客模式不能调用模型' : undefined} onClick={() => modelTest.mutate()}>{modelTest.isPending ? '正在连接…' : '测试连接'}</button>
        <button className="button button-primary" type="submit" disabled={isGuest || modelSave.isPending || !model.trim() || !baseUrl.trim() || (!status?.apiKeyConfigured && !apiKey.trim())}>{modelSave.isPending ? '保存中…' : '保存配置'}</button>
      </div>
    </form>
  )
}

export function SettingsPage() {
  const isGuest = useIsGuest()
  const healthQuery = useQuery({ queryKey: ['system-health'], queryFn: getSystemHealth })
  const modelQuery = useQuery({ queryKey: ['ai-model-status'], queryFn: getAiModelStatus })
  const modelStatus = modelQuery.data

  const modelStatusLabel = modelQuery.isPending
    ? '检查中'
    : modelStatus?.ready
      ? '已就绪'
      : modelStatus?.apiKeyConfigured
        ? '主密钥不可用'
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
            {modelQuery.isPending
              ? <div className="settings-form" role="status">正在读取模型配置…</div>
              : <AiModelSettingsForm status={modelStatus} isGuest={isGuest} />}
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
