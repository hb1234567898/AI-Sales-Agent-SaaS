import { CheckCircle, Database, WarningCircle } from '@phosphor-icons/react'
import { useQuery } from '@tanstack/react-query'
import { getSystemHealth } from '../api/system-api'

export function SettingsPage() {
  const healthQuery = useQuery({ queryKey: ['system-health'], queryFn: getSystemHealth })

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
            <div className="panel-header"><div><h2>AI 模型</h2><p>控制分析与动作生成使用的模型</p></div></div>
            <form className="settings-form">
              <label><span>模型提供商</span><select defaultValue=""><option value="" disabled>请选择模型提供商</option><option>OpenAI</option><option>通义千问</option><option>本地模型</option></select><small>保存后会用于新的 Agent 运行。</small></label>
              <label><span>模型名称</span><input type="text" placeholder="例如 qwen-plus" /><small>请输入提供商支持的模型标识。</small></label>
              <label><span>API Key</span><input type="password" placeholder="输入密钥" /><small>密钥将由后端加密保存。</small></label>
              <div className="settings-form-actions"><button className="button button-primary" type="button" disabled>保存模型配置</button></div>
            </form>
          </section>

          <section className="surface settings-section" id="approval-policy">
            <div className="panel-header"><div><h2>审批策略</h2><p>确定哪些 Agent 动作必须经过人工确认</p></div></div>
            <div className="setting-switch-list">
              <label><span><strong>发送客户消息</strong><small>邮件、短信和企业微信消息</small></span><input type="checkbox" defaultChecked /></label>
              <label><span><strong>更新敏感 CRM 字段</strong><small>商机金额、阶段和关闭原因</small></span><input type="checkbox" defaultChecked /></label>
              <label><span><strong>创建内部跟进任务</strong><small>仅影响工作区内部数据</small></span><input type="checkbox" /></label>
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
