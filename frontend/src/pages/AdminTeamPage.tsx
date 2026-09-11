import { Buildings, CheckCircle, ShieldCheck, UsersThree } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getTeam, updateTeam, type AdminTeam } from '../api/admin-api'
import { SelectField } from '../components/forms/SelectField'

const timezoneOptions = [
  { value: 'Asia/Shanghai', label: '中国标准时间（上海）' },
  { value: 'Asia/Singapore', label: '新加坡时间' },
  { value: 'Asia/Tokyo', label: '日本标准时间（东京）' },
  { value: 'Europe/London', label: '英国时间（伦敦）' },
  { value: 'America/New_York', label: '美国东部时间（纽约）' },
  { value: 'UTC', label: '协调世界时（UTC）' },
]

const localeOptions = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English (US)' },
]

export function AdminTeamPage() {
  const query = useQuery({ queryKey: ['admin-team'], queryFn: getTeam })

  return (
    <section className="module-page admin-page">
      <header className="page-heading module-heading"><div><p className="eyebrow">管理员控制台</p><h1>团队管理</h1><p>维护工作区资料、区域设置和团队运行状态。</p></div></header>

      <section className="admin-metric-grid">
        <Metric icon={UsersThree} label="团队成员" value={query.data?.totalMembers} note="不包含已离开成员" />
        <Metric icon={CheckCircle} label="活跃账号" value={query.data?.activeMembers} note="当前可以登录系统" />
        <Metric icon={ShieldCheck} label="管理员" value={query.data?.adminMembers} note="所有者与管理员" />
      </section>

      <div className="admin-team-layout">
        <section className="surface admin-team-card">
          <div className="panel-header"><div><h2>团队资料</h2><p>这些信息用于工作区展示和业务时间计算。</p></div><span className="drawer-title-icon"><Buildings size={19} /></span></div>
          {query.isPending ? <div className="audit-state">正在读取团队配置…</div> : null}
          {query.isError ? <div className="audit-state is-error">{query.error.message}</div> : null}
          {query.data ? <TeamSettingsForm key={query.data.updatedAt} team={query.data} /> : null}
        </section>

        <aside className="surface admin-policy-card"><h2>权限边界</h2><div><ShieldCheck size={18} /><span><strong>团队所有者</strong><small>可以任命管理员、管理所有成员与团队配置。</small></span></div><div><ShieldCheck size={18} /><span><strong>管理员</strong><small>可以管理普通成员，但不能修改所有者或其他管理员。</small></span></div><div><UsersThree size={18} /><span><strong>业务角色</strong><small>主管、销售和只读成员不会看到管理员入口。</small></span></div></aside>
      </div>
    </section>
  )
}

function TeamSettingsForm({ team }: { team: AdminTeam }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(team.name)
  const [timezone, setTimezone] = useState(team.timezone)
  const [locale, setLocale] = useState(team.locale)
  const mutation = useMutation({
    mutationFn: () => updateTeam({ name: name.trim(), timezone, locale }),
    onSuccess: (updatedTeam) => queryClient.setQueryData(['admin-team'], updatedTeam),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <form className="admin-team-form" onSubmit={submit}>
      <label><span>团队名称</span><input required maxLength={200} value={name} onChange={(event) => setName(event.target.value)} /></label>
      <label><span>工作区标识</span><input value={team.slug} disabled /><small>标识创建后不可修改，用于稳定识别租户。</small></label>
      <div className="admin-form-field"><span>业务时区</span><SelectField value={timezone} onChange={setTimezone} ariaLabel="业务时区" options={timezoneOptions} /><small>影响日报边界、跟进时间和数据统计，不依赖服务器所在国家。</small></div>
      <div className="admin-form-field"><span>默认语言</span><SelectField value={locale} onChange={setLocale} ariaLabel="默认语言" options={localeOptions} /></div>
      {mutation.isError ? <p className="admin-form-error">{mutation.error.message}</p> : null}
      <div className="settings-form-actions"><span className="model-test-result">{mutation.isSuccess ? '团队配置已保存。重新登录后侧栏名称会同步更新。' : `当前套餐：${team.planCode}`}</span><button className="button button-primary" type="submit" disabled={mutation.isPending || !name.trim()}>{mutation.isPending ? '保存中…' : '保存团队设置'}</button></div>
    </form>
  )
}

function Metric({ icon: Icon, label, value, note }: { icon: typeof UsersThree; label: string; value?: number; note: string }) {
  return <section className="surface admin-metric"><span><Icon size={19} /></span><div><small>{label}</small><strong>{value ?? '—'}</strong><p>{note}</p></div></section>
}
