import {
  ArrowRight,
  CheckCircle,
  CirclesThreePlus,
  Eye,
  EyeSlash,
  LockKey,
  ShieldCheck,
} from '@phosphor-icons/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { login } from '../api/auth-api'
import { ApiError } from '../api/axios-client'
import { enterGuestMode, leaveGuestMode } from '../auth/guest-session'

interface LoginLocationState {
  from?: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(true)
  const [showPassword, setShowPassword] = useState(false)

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (session) => {
      leaveGuestMode()
      queryClient.setQueryData(['auth-session'], session)
      const requestedPath = (location.state as LoginLocationState | null)?.from
      navigate(requestedPath?.startsWith('/app') ? requestedPath : '/app/today', { replace: true })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    loginMutation.reset()
    loginMutation.mutate({ email: email.trim(), password, rememberMe })
  }

  function browseAsGuest() {
    enterGuestMode()
    queryClient.removeQueries({ queryKey: ['auth-session'] })
    navigate('/app/today', { replace: true })
  }

  const errorMessage = loginMutation.error instanceof ApiError
    ? loginMutation.error.message
    : loginMutation.error instanceof Error
      ? loginMutation.error.message
    : loginMutation.isError
      ? '暂时无法连接登录服务，请稍后重试'
      : null

  return (
    <main className="login-page">
      <section className="login-story" aria-label="产品介绍">
        <div className="login-brand">
          <span className="brand-symbol" aria-hidden><CirclesThreePlus size={20} weight="fill" /></span>
          <span>
            <strong>Sales Agent</strong>
            <small>销售运营工作台</small>
          </span>
        </div>

        <div className="login-story-copy">
          <span className="login-kicker">AI 驱动的销售协作</span>
          <h1>把客户对话转化为下一步销售动作</h1>
          <p>客户、互动记录、跟进建议和审批集中在一个可信的工作空间中。</p>
        </div>

        <div className="login-trust-list" aria-label="产品能力">
          <span><CheckCircle size={17} weight="fill" />对话与客户资料统一沉淀</span>
          <span><CheckCircle size={17} weight="fill" />敏感动作经过人工审批</span>
          <span><CheckCircle size={17} weight="fill" />每次 Agent 运行全程可追溯</span>
        </div>

        <p className="login-story-footer">企业数据仅在授权工作区内使用</p>
      </section>

      <section className="login-form-side">
        <div className="login-mobile-brand">
          <span className="brand-symbol" aria-hidden><CirclesThreePlus size={20} weight="fill" /></span>
          <strong>Sales Agent</strong>
        </div>

        <form className="login-card" onSubmit={handleSubmit}>
          <header>
            <span className="login-lock" aria-hidden><LockKey size={20} /></span>
            <h2>登录工作台</h2>
            <p>使用企业账号继续访问你的销售工作区</p>
          </header>

          <label className="login-field">
            <span>邮箱</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="name@company.com"
              autoComplete="email"
              maxLength={320}
              required
              autoFocus
            />
          </label>

          <label className="login-field">
            <span>密码</span>
            <span className="password-input">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="请输入密码"
                autoComplete="current-password"
                maxLength={200}
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
              >
                {showPassword ? <EyeSlash size={18} /> : <Eye size={18} />}
              </button>
            </span>
          </label>

          <div className="login-options">
            <label className="remember-option">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(event) => setRememberMe(event.target.checked)}
              />
              <span>在这台设备上保持登录</span>
            </label>
            <span>忘记密码请联系管理员</span>
          </div>

          {errorMessage ? <div className="login-error" role="alert">{errorMessage}</div> : null}

          <button className="login-submit" type="submit" disabled={loginMutation.isPending}>
            <span>{loginMutation.isPending ? '正在验证账号' : '登录'}</span>
            {!loginMutation.isPending ? <ArrowRight size={18} /> : <i className="button-spinner" aria-hidden />}
          </button>

          <button className="guest-submit" type="button" onClick={browseAsGuest} disabled={loginMutation.isPending}>
            以游客身份浏览
          </button>

          <div className="login-security-note">
            <ShieldCheck size={17} />
            <span>采用 JWT 双令牌与 Refresh Token 轮换，退出后会撤销当前刷新会话。</span>
          </div>
        </form>

        <p className="login-help">账号由企业管理员创建，无法登录时请联系管理员。</p>
      </section>
    </main>
  )
}
