import { CheckCircle, Database, EnvelopeSimple, WarningCircle } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getAiModelStatus, saveAiModelConfiguration, testAiModelConnection, type AiModelStatus } from '../api/ai-settings-api'
import { getEmailSettingsStatus, saveEmailSettings, testEmailSettingsConnection, type EmailSettingsStatus } from '../api/email-settings-api'
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

interface EmailSettingsFormProps {
  status?: EmailSettingsStatus
  isGuest: boolean
}

function EmailSettingsForm({ status, isGuest }: EmailSettingsFormProps) {
  const queryClient = useQueryClient()
  const [host, setHost] = useState(status?.host ?? 'smtp.example.com')
  const [port, setPort] = useState(String(status?.port ?? 587))
  const [username, setUsername] = useState(status?.username ?? '')
  const [password, setPassword] = useState('')
  const [fromAddress, setFromAddress] = useState(status?.fromAddress ?? status?.username ?? '')
  const [smtpAuth, setSmtpAuth] = useState(status?.smtpAuth ?? true)
  const [starttlsEnabled, setStarttlsEnabled] = useState(status?.starttlsEnabled ?? true)
  const [starttlsRequired, setStarttlsRequired] = useState(status?.starttlsRequired ?? false)
  const emailTest = useMutation({ mutationFn: testEmailSettingsConnection })
  const emailSave = useMutation({
    mutationFn: saveEmailSettings,
    onSuccess: (savedStatus) => {
      queryClient.setQueryData(['email-settings-status'], savedStatus)
      setPassword('')
      emailTest.reset()
    },
  })

  function saveEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    emailSave.mutate({
      host: host.trim(),
      port: Number(port),
      username: username.trim() || undefined,
      password: password.trim() || undefined,
      fromAddress: fromAddress.trim(),
      smtpAuth,
      starttlsEnabled,
      starttlsRequired,
    })
  }

  const statusText = emailTest.isSuccess
    ? `${emailTest.data.message} · ${emailTest.data.latencyMs} ms`
    : emailTest.isError
      ? emailTest.error.message
      : emailSave.isSuccess
        ? '发件邮箱配置已加密保存。'
        : emailSave.isError
          ? emailSave.error.message
          : status?.status === 'ENV_FALLBACK'
            ? '当前使用服务器环境变量兜底，保存后会优先使用页面配置。'
            : '请保存配置后测试连接；密码或授权码只保存密文。'

  return (
    <form className="settings-form" onSubmit={saveEmail}>
      <label><span>SMTP 服务器</span><input aria-label="SMTP 服务器" type="text" required maxLength={255} value={host} disabled={isGuest || emailSave.isPending} onChange={(event) => setHost(event.target.value)} placeholder="smtp.example.com" /></label>
      <label><span>端口</span><input aria-label="SMTP 端口" type="number" required min={1} max={65535} value={port} disabled={isGuest || emailSave.isPending} onChange={(event) => setPort(event.target.value)} placeholder="587" /></label>
      <label><span>账号</span><input aria-label="SMTP 账号" type="email" maxLength={320} value={username} disabled={isGuest || emailSave.isPending} onChange={(event) => setUsername(event.target.value)} placeholder="notifications@example.com" /></label>
      <label><span>密码/授权码</span><input aria-label="SMTP 密码或授权码" type="password" maxLength={500} autoComplete="new-password" value={password} disabled={isGuest || emailSave.isPending} onChange={(event) => setPassword(event.target.value)} placeholder={status?.passwordConfigured ? '已加密保存，留空表示不修改' : '请输入 SMTP 授权码'} /><small>{status?.passwordConfigured ? '页面不会读取或回显原文。' : '常见邮箱需要使用 SMTP 授权码，不是登录密码。'}</small></label>
      <label className="field-span-2"><span>发件人</span><input aria-label="发件人邮箱" type="email" required maxLength={320} value={fromAddress} disabled={isGuest || emailSave.isPending} onChange={(event) => setFromAddress(event.target.value)} placeholder="notifications@example.com" /></label>
      <div className="settings-toggle-row"><label><input type="checkbox" checked={smtpAuth} disabled={isGuest || emailSave.isPending} onChange={(event) => setSmtpAuth(event.target.checked)} /><span>SMTP 认证</span></label><label><input type="checkbox" checked={starttlsEnabled} disabled={isGuest || emailSave.isPending} onChange={(event) => setStarttlsEnabled(event.target.checked)} /><span>启用 STARTTLS</span></label><label><input type="checkbox" checked={starttlsRequired} disabled={isGuest || emailSave.isPending} onChange={(event) => setStarttlsRequired(event.target.checked)} /><span>要求 STARTTLS</span></label></div>
      <div className="settings-form-actions">
        <span className={`model-test-result${emailTest.isError || emailSave.isError ? ' is-error' : ''}`} role="status">{statusText}</span>
        <button className="button button-secondary" type="button" disabled={isGuest || !status?.ready || emailTest.isPending || emailSave.isPending} title={isGuest ? '游客模式不能测试邮箱' : undefined} onClick={() => emailTest.mutate()}>{emailTest.isPending ? '正在连接…' : '测试连接'}</button>
        <button className="button button-primary" type="submit" disabled={isGuest || emailSave.isPending || !host.trim() || !port.trim() || !fromAddress.trim() || (smtpAuth && !status?.passwordConfigured && !password.trim())}>{emailSave.isPending ? '保存中…' : '保存配置'}</button>
      </div>
    </form>
  )
}

export function SettingsPage() {
  const isGuest = useIsGuest()
  const healthQuery = useQuery({ queryKey: ['system-health'], queryFn: getSystemHealth })
  const modelQuery = useQuery({ queryKey: ['ai-model-status'], queryFn: getAiModelStatus })
  const emailQuery = useQuery({ queryKey: ['email-settings-status'], queryFn: getEmailSettingsStatus })
  const modelStatus = modelQuery.data
  const emailStatus = emailQuery.data

  const modelStatusLabel = modelQuery.isPending
    ? '检查中'
    : modelStatus?.ready
      ? '已就绪'
      : modelStatus?.apiKeyConfigured
        ? '主密钥不可用'
        : '待配置'
  const emailStatusLabel = emailQuery.isPending
    ? '检查中'
    : emailStatus?.ready
      ? emailStatus.status === 'ENV_FALLBACK' ? '使用服务器配置' : '已就绪'
      : emailStatus?.passwordConfigured
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
              <div>
                <span className="integration-icon"><EnvelopeSimple size={18} /></span>
                <span><strong>发件邮箱</strong><small>审批通过后用于真实发送客户邮件</small></span>
                <span className={`status-badge ${emailStatus?.ready ? 'status-success' : emailQuery.isError ? 'status-error' : 'status-muted'}`}>{emailStatus?.ready ? <CheckCircle size={11} /> : null}{emailStatusLabel}</span>
                <a className="compact-button" href="#email-settings">配置</a>
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

          <section className="surface settings-section" id="email-settings">
            <div className="panel-header">
              <div><h2>发件邮箱</h2><p>配置审批通过后发送客户邮件的 SMTP 通道</p></div>
              <span className={`status-badge ${emailStatus?.ready ? 'status-success' : emailQuery.isError ? 'status-error' : 'status-muted'}`}>
                {emailStatus?.ready ? <CheckCircle size={11} /> : null}{emailStatusLabel}
              </span>
            </div>
            {emailQuery.isPending
              ? <div className="settings-form" role="status">正在读取邮箱配置…</div>
              : <EmailSettingsForm status={emailStatus} isGuest={isGuest} />}
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
