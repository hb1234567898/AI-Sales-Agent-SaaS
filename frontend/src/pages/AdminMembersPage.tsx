import { MagnifyingGlass, Plus, ShieldCheck, UserCircle, X } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useMemo, useState } from 'react'
import {
  createMember,
  getMembers,
  updateMember,
  type AdminMember,
  type CreateMemberInput,
  type MemberRole,
  type MemberStatus,
  type UpdateMemberInput,
} from '../api/admin-api'
import { SelectField } from '../components/forms/SelectField'
import { useAuth } from '../auth/use-auth'

const roleLabels: Record<MemberRole, string> = {
  OWNER: '团队所有者',
  ADMIN: '管理员',
  MANAGER: '销售主管',
  SALES: '销售成员',
  VIEWER: '只读成员',
}

const statusLabels: Record<MemberStatus, string> = {
  INVITED: '待加入',
  ACTIVE: '正常',
  SUSPENDED: '已停用',
  LEFT: '已离开',
}

const filterRoles = [
  { value: '', label: '全部角色' },
  ...Object.entries(roleLabels).map(([value, label]) => ({ value, label })),
]

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '正常' },
  { value: 'SUSPENDED', label: '已停用' },
  { value: 'LEFT', label: '已离开' },
]

export function AdminMembersPage() {
  const session = useAuth()
  const [keyword, setKeyword] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<AdminMember | 'create' | null>(null)
  const query = useQuery({
    queryKey: ['admin-members', keyword, role, status, page],
    queryFn: () => getMembers({ keyword: keyword.trim(), role, status, page, size: 20 }),
    placeholderData: (previous) => previous,
  })

  function updateFilter(action: () => void) {
    action()
    setPage(0)
  }

  return (
    <section className="module-page admin-page">
      <header className="page-heading module-heading">
        <div><p className="eyebrow">管理员控制台</p><h1>成员管理</h1><p>添加团队成员，分配工作角色并管理账号状态。</p></div>
        <div className="page-actions"><button className="button button-primary" type="button" onClick={() => setEditor('create')}><Plus size={16} />添加成员</button></div>
      </header>

      <section className="surface admin-summary-strip">
        <div><span>成员总数</span><strong>{query.data?.totalElements ?? '—'}</strong></div>
        <div><span>当前筛选</span><strong>{query.data?.content.length ?? '—'}</strong></div>
        <div><span>你的权限</span><strong>{roleLabels[session.role as MemberRole] ?? session.role}</strong></div>
      </section>

      <section className="surface admin-panel">
        <div className="admin-filters">
          <label className="audit-search"><MagnifyingGlass size={16} /><input aria-label="搜索成员" type="search" placeholder="搜索姓名或邮箱" value={keyword} onChange={(event) => updateFilter(() => setKeyword(event.target.value))} /></label>
          <SelectField value={role} onChange={(value) => updateFilter(() => setRole(value))} ariaLabel="筛选角色" options={filterRoles} />
          <SelectField value={status} onChange={(value) => updateFilter(() => setStatus(value))} ariaLabel="筛选状态" options={statusOptions} />
        </div>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>成员</th><th>角色</th><th>状态</th><th>最近登录</th><th>加入时间</th><th /></tr></thead>
            <tbody>
              {query.data?.content.map((member) => (
                <tr key={member.id}>
                  <td><span className="member-identity"><i>{member.displayName.slice(0, 1)}</i><span><strong>{member.displayName}</strong><small>{member.email}</small></span></span></td>
                  <td><span className={`role-chip role-${member.role.toLowerCase()}`}>{member.role === 'OWNER' || member.role === 'ADMIN' ? <ShieldCheck size={13} /> : null}{roleLabels[member.role]}</span></td>
                  <td><span className={`member-status status-${member.status.toLowerCase()}`}><i />{statusLabels[member.status]}</span></td>
                  <td>{formatDate(member.lastLoginAt)}</td>
                  <td>{formatDate(member.joinedAt ?? member.createdAt)}</td>
                  <td><button className="table-action" type="button" disabled={member.role === 'OWNER' || member.id === session.memberId} onClick={() => setEditor(member)}>管理</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          {query.isPending ? <div className="audit-state">正在读取成员…</div> : null}
          {query.isError ? <div className="audit-state is-error">{query.error.message}</div> : null}
          {query.data?.content.length === 0 ? <div className="audit-state">没有符合条件的成员。</div> : null}
        </div>

        <div className="table-pagination">
          <div><strong>{query.data?.totalElements ?? 0}</strong><span>位成员</span></div>
          <div><button type="button" disabled={!query.data || query.data.first} onClick={() => setPage((value) => Math.max(0, value - 1))}>上一页</button><span>第 {(query.data?.page ?? page) + 1} 页 / 共 {Math.max(query.data?.totalPages ?? 1, 1)} 页</span><button type="button" disabled={!query.data || query.data.last} onClick={() => setPage((value) => value + 1)}>下一页</button></div>
        </div>
      </section>

      {editor ? <MemberEditor member={editor === 'create' ? null : editor} actorRole={session.role as MemberRole} onClose={() => setEditor(null)} /> : null}
    </section>
  )
}

function MemberEditor({ member, actorRole, onClose }: { member: AdminMember | null; actorRole: MemberRole; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(member?.displayName ?? '')
  const [email, setEmail] = useState(member?.email ?? '')
  const [role, setRole] = useState<MemberRole>(member?.role ?? 'SALES')
  const [status, setStatus] = useState<MemberStatus>(member?.status ?? 'ACTIVE')
  const [initialPassword, setInitialPassword] = useState('')
  const roleOptions = useMemo(() => [
    ...(actorRole === 'OWNER' ? [{ value: 'ADMIN', label: '管理员' }] : []),
    { value: 'MANAGER', label: '销售主管' },
    { value: 'SALES', label: '销售成员' },
    { value: 'VIEWER', label: '只读成员' },
  ], [actorRole])
  const mutation = useMutation({
    mutationFn: () => member
      ? updateMember(member.id, { displayName: displayName.trim(), role, status } satisfies UpdateMemberInput)
      : createMember({ displayName: displayName.trim(), email: email.trim(), role, initialPassword } satisfies CreateMemberInput),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-members'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-team'] })
      onClose()
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <div className="admin-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="member-editor-title">
        <header><span className="drawer-title-icon"><UserCircle size={20} /></span><div><h2 id="member-editor-title">{member ? '管理成员' : '添加成员'}</h2><p>{member ? '修改成员职责或停用账号。' : '创建可直接登录的团队账号。'}</p></div><button className="icon-button" type="button" onClick={onClose} aria-label="关闭"><X size={18} /></button></header>
        <form onSubmit={submit}>
          <label><span>姓名</span><input required maxLength={120} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
          <label><span>邮箱</span><input required type="email" maxLength={320} disabled={Boolean(member)} value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <div className="admin-form-field"><span>角色</span><SelectField value={role} onChange={(value) => setRole(value as MemberRole)} ariaLabel="成员角色" options={roleOptions} /></div>
          {member ? <div className="admin-form-field"><span>账号状态</span><SelectField value={status} onChange={(value) => setStatus(value as MemberStatus)} ariaLabel="账号状态" options={statusOptions.filter((option) => option.value)} /></div> : (
            <label><span>初始密码</span><input required type="password" minLength={10} maxLength={72} autoComplete="new-password" value={initialPassword} onChange={(event) => setInitialPassword(event.target.value)} placeholder="至少 10 位，包含大小写字母和数字" /><small>密码仅用于首次登录，服务端只保存 BCrypt 摘要。</small></label>
          )}
          {mutation.isError ? <p className="admin-form-error">{mutation.error.message}</p> : null}
          <footer><button className="button button-secondary" type="button" onClick={onClose}>取消</button><button className="button button-primary" type="submit" disabled={mutation.isPending}>{mutation.isPending ? '保存中…' : member ? '保存修改' : '创建成员'}</button></footer>
        </form>
      </section>
    </div>
  )
}

function formatDate(value: string | null) {
  if (!value) return '从未登录'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
