import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuth } from '../../auth/use-auth'
import { getMailAccount, saveMailAccount, testMailAccount, type MailAccount } from '../../api/mail-api'
import { SelectField } from '../forms/SelectField'

export function MailAccountSettings() {
  const session = useAuth()
  const writable = ['OWNER', 'ADMIN'].includes(session.role)
  const query = useQuery({ queryKey: ['mail-account'], queryFn: getMailAccount })
  return <section className="surface settings-section" id="mailbox-settings">
    <div className="panel-header"><div><h2>邮件回复接收</h2><p>团队收信邮箱 · IMAP</p></div><span className="status-badge status-muted">{session.role === 'GUEST' ? '演示配置' : query.data?.enabled ? '同步已开启' : '同步未开启'}</span></div>
    {query.isPending ? <p className="audit-state">正在读取收信配置…</p> : query.isError ? <p className="audit-state is-error" role="alert">{query.error.message}</p> : <AccountForm key={query.data.version} account={query.data} writable={writable} />}
  </section>
}
function AccountForm({ account, writable }: { account: MailAccount; writable: boolean }) {
  const client = useQueryClient()
  const [values, setValues] = useState(() => ({ host: account.host, port: account.port, username: account.username, ssl: account.ssl, folder: account.folder, enabled: account.enabled, version: account.version }))
  const [password, setPassword] = useState('')
  const save = useMutation({ mutationFn: saveMailAccount, onSuccess: result => { setPassword(''); client.setQueryData(['mail-account'], result) } })
  const test = useMutation({ mutationFn: testMailAccount })
  const disabled = !writable || save.isPending || test.isPending
  const dirty = JSON.stringify(values) !== JSON.stringify({ host: account.host, port: account.port, username: account.username, ssl: account.ssl, folder: account.folder, enabled: account.enabled, version: account.version }) || Boolean(password)
  const field = <K extends keyof typeof values>(key: K, value: typeof values[K]) => setValues(old => ({ ...old, [key]: value }))
  return <form className="settings-form" onSubmit={event => { event.preventDefault(); save.mutate({ ...values, password: password || undefined }) }}>
    <label><span>IMAP 服务器</span><input aria-label="IMAP 服务器" required maxLength={255} value={values.host} disabled={disabled} placeholder="imap.example.com" onChange={e => field('host', e.target.value.trim())} /></label>
    <label><span>IMAP 端口</span><input aria-label="IMAP 端口" type="number" required min={1} max={65535} value={values.port} disabled={disabled} onChange={e => field('port', Number(e.target.value))} /></label>
    <label><span>收信账号</span><input aria-label="IMAP 账号" required type="email" maxLength={320} value={values.username} disabled={disabled} onChange={e => field('username', e.target.value)} /></label>
    <label><span>收信授权码</span><input aria-label="IMAP 授权码" type="password" autoComplete="new-password" maxLength={500} value={password} disabled={disabled} placeholder={account.passwordConfigured ? '已加密保存，留空保留' : '请输入邮箱授权码'} onChange={e => setPassword(e.target.value)} /></label>
    <div className="settings-field"><span>传输加密</span><SelectField ariaLabel="IMAP 加密方式" value={values.ssl ? 'SSL' : 'STARTTLS'} options={[{ value: 'SSL', label: 'SSL/TLS（通常 993）' }, { value: 'STARTTLS', label: 'STARTTLS（通常 143）' }]} disabled={disabled} onChange={v => setValues(old => ({ ...old, ssl: v === 'SSL', port: v === 'SSL' ? 993 : 143 }))} /></div>
    <label><span>收信文件夹</span><input aria-label="IMAP 文件夹" required maxLength={120} value={values.folder} disabled={disabled} onChange={e => field('folder', e.target.value)} /></label>
    <div className="settings-toggle-row"><label><input type="checkbox" checked={values.enabled} disabled={disabled} onChange={e => field('enabled', e.target.checked)} /><span>自动同步客户来信</span></label></div>
    <div className="settings-form-actions"><span className={`model-test-result${save.isError || test.isError || account.lastError ? ' is-error' : ''}`} role="status">{save.error?.message ?? test.error?.message ?? (test.data?.message || account.lastError || (!writable ? '由团队管理员维护收信配置' : account.passwordConfigured ? '授权码已加密保存' : '收信与发信分别配置，首次接入读取最近 200 封邮件'))}</span><button className="button button-secondary" type="button" disabled={disabled || dirty || !account.passwordConfigured} onClick={() => test.mutate()}>{test.isPending ? '连接中…' : '测试收信'}</button><button className="button button-primary" disabled={disabled || (!account.passwordConfigured && !password)}>{save.isPending ? '保存中…' : '保存收信配置'}</button></div>
  </form>
}
