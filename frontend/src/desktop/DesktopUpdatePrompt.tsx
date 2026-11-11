import { ArrowClockwise, Check, CircleNotch, DownloadSimple, WarningCircle, X } from '@phosphor-icons/react'
import { check, type Update } from '@tauri-apps/plugin-updater'
import { useEffect, useId, useRef, useState, type CSSProperties } from 'react'

type UpdateState = 'idle' | 'available' | 'downloading' | 'downloaded' | 'installing' | 'updated' | 'error'
type FailureStage = 'download' | 'install' | 'restart'

interface UpdateStatus {
  state: UpdateState
  version: string
  progress: number
  errorMessage: string
  failureStage: FailureStage
}

interface UpdateActions {
  retry: () => Promise<void>
  install: () => Promise<void>
}

const checkInterval = 5 * 60_000
const checkCooldown = 60_000
const initialStatus: UpdateStatus = { state: 'idle', version: '', progress: 0, errorMessage: '', failureStage: 'download' }

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown
  }
}

export function DesktopUpdateIndicator() {
  const [status, setStatus] = useState(initialStatus)
  const [open, setOpen] = useState(false)
  const actions = useRef<UpdateActions | null>(null)
  const container = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const dialogId = useId()

  useEffect(() => {
    if (!isTauriRuntime()) return
    let disposed = false
    let checking = false
    let downloading = false
    let installing = false
    let lastCheckedAt = -Infinity
    let update: Update | null = null
    let snapshot = initialStatus

    function publish(change: Partial<UpdateStatus>) {
      snapshot = { ...snapshot, ...change }
      if (!disposed) setStatus(snapshot)
    }

    function release(resource: Update | null) {
      if (resource) void resource.close().catch(() => {})
    }

    async function downloadUpdate() {
      if (disposed || !update || downloading || installing) return
      const target = update
      downloading = true
      let downloaded = 0
      let contentLength = 0
      publish({ state: 'downloading', progress: 0, errorMessage: '' })
      try {
        await target.download((event) => {
          if (disposed) return
          if (event.event === 'Started') {
            contentLength = event.data.contentLength ?? 0
          } else if (event.event === 'Progress') {
            downloaded += event.data.chunkLength
            publish({ progress: contentLength > 0 ? Math.min(99, Math.round(downloaded / contentLength * 100)) : 0 })
          }
        })
        // download() resolves only after the complete package has been verified.
        publish({ state: 'downloaded', progress: 100 })
      } catch (error) {
        publish({ state: 'error', failureStage: 'download', errorMessage: errorText(error, '更新包下载失败，请重试。') })
      } finally {
        downloading = false
        if (disposed) release(target)
      }
    }

    function updateInProgress() {
      return downloading || installing || snapshot.state === 'updated' || snapshot.failureStage === 'restart'
    }

    async function checkForUpdates() {
      if (disposed || checking || updateInProgress() || navigator.onLine === false) return
      if (Date.now() - lastCheckedAt < checkCooldown) return
      checking = true
      lastCheckedAt = Date.now()
      try {
        const candidate = await check({ timeout: 15_000 })
        if (disposed || updateInProgress()) {
          release(candidate)
          return
        }
        if (!candidate) return
        if (candidate.version === update?.version) {
          release(candidate)
          return
        }
        const previous = update
        update = candidate
        release(previous)
        publish({ state: 'available', version: candidate.version, progress: 0, errorMessage: '', failureStage: 'download' })
        await downloadUpdate()
      } catch (error) {
        if (!disposed) console.warn('桌面端更新检查失败，将在下次检测时重试。', error)
      } finally {
        checking = false
      }
    }

    async function restartApp() {
      if (disposed || installing) return
      installing = true
      publish({ state: 'updated', errorMessage: '' })
      try {
        const { relaunch } = await import('@tauri-apps/plugin-process')
        if (!disposed) await relaunch()
      } catch (error) {
        publish({ state: 'error', failureStage: 'restart', errorMessage: errorText(error, '自动重启失败，请手动重启应用。') })
      } finally {
        installing = false
      }
    }

    async function installUpdate() {
      const ready = snapshot.state === 'downloaded' || snapshot.state === 'error' && snapshot.failureStage === 'install'
      if (disposed || !update || !ready || downloading || installing) return
      const target = update
      installing = true
      publish({ state: 'installing', errorMessage: '' })
      try {
        await target.install()
      } catch (error) {
        publish({ state: 'error', failureStage: 'install', errorMessage: errorText(error, '更新安装失败，请重试。') })
        return
      } finally {
        installing = false
        if (disposed) release(target)
      }
      await restartApp()
    }

    actions.current = {
      retry: () => snapshot.failureStage === 'restart' ? restartApp() : snapshot.failureStage === 'install' ? installUpdate() : downloadUpdate(),
      install: installUpdate,
    }
    function checkWhenActive() {
      if (document.visibilityState !== 'hidden') void checkForUpdates()
    }
    void checkForUpdates()
    const timer = window.setInterval(checkWhenActive, checkInterval)
    window.addEventListener('focus', checkWhenActive)
    window.addEventListener('online', checkWhenActive)
    document.addEventListener('visibilitychange', checkWhenActive)
    return () => {
      disposed = true
      actions.current = null
      window.clearInterval(timer)
      window.removeEventListener('focus', checkWhenActive)
      window.removeEventListener('online', checkWhenActive)
      document.removeEventListener('visibilitychange', checkWhenActive)
      if (!downloading && !installing) release(update)
    }
  }, [])

  useEffect(() => {
    if (!open) return
    function closeOutside(event: PointerEvent) {
      if (event.target instanceof Node && !container.current?.contains(event.target)) setOpen(false)
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        trigger.current?.focus()
      }
    }
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  if (!isTauriRuntime() || status.state === 'idle') return null

  const { state, version, progress, errorMessage, failureStage } = status
  const failed = state === 'error'
  const ready = state === 'downloaded' || state === 'updated'
  const busy = state === 'installing' || state === 'updated'
  const label = failed ? '应用更新失败'
    : state === 'downloaded' ? `新版本 ${version} 已下载，点击安装`
    : state === 'updated' ? '更新完成，正在重启应用'
    : state === 'installing' ? `正在安装新版本 ${version}`
    : state === 'downloading' ? `正在下载新版本 ${version}${progress > 0 ? `，${progress}%` : ''}`
    : `发现新版本 ${version}`
  const description = failed ? errorMessage
    : state === 'downloaded' ? '更新包已下载并校验，安装将在确认后开始。'
    : state === 'updated' ? '更新已安装，正在重启应用。'
    : state === 'installing' ? '正在启动安装程序，请等待应用重启。'
    : '正在后台下载更新包，完成后可选择安装。'

  return (
    <div ref={container} className="desktop-update-indicator">
      <button
        ref={trigger}
        type="button"
        className={`icon-button desktop-update-trigger${ready ? ' is-ready' : ''}${failed ? ' is-error' : ''}`}
        title={label}
        aria-label={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? dialogId : undefined}
        onClick={() => setOpen((value) => !value)}
        style={{ '--progress': `${progress}%` } as CSSProperties}
      >
        {failed ? <WarningCircle size={20} /> : ready ? <Check size={20} /> : state === 'installing' ? <CircleNotch size={20} className="mcp-spin" /> : <DownloadSimple size={20} />}
        {state === 'downloading' && <i className="desktop-update-progress" aria-hidden />}
      </button>
      <span className="desktop-update-status" role="status" aria-live="polite">{label}</span>
      {open && (
        <div className="desktop-update-popover" id={dialogId} role="dialog" aria-label="桌面应用更新">
          <div className="desktop-update-heading">
            <strong>新版本 {version}</strong>
            <button className="icon-button" type="button" title="关闭更新详情" aria-label="关闭更新详情" onClick={() => { setOpen(false); trigger.current?.focus() }}><X size={16} /></button>
          </div>
          <p className={failed ? 'is-error' : undefined}>{description}</p>
          {state === 'downloading' && <progress aria-label="更新包下载进度" max={100} value={progress > 0 ? progress : undefined} />}
          <div className="desktop-update-actions">
            <button className="button button-secondary" type="button" onClick={() => setOpen(false)}>稍后</button>
            {failed ? (
              <button className="button button-primary" type="button" onClick={() => void actions.current?.retry()}><ArrowClockwise size={16} />{failureStage === 'restart' ? '重试重启' : failureStage === 'install' ? '重试安装' : '重试下载'}</button>
            ) : state === 'downloaded' || busy ? (
              <button className="button button-primary" type="button" disabled={busy} onClick={() => void actions.current?.install()}><ArrowClockwise size={16} />{busy ? '正在更新' : '安装并重启'}</button>
            ) : null}
          </div>
        </div>
      )}
    </div>
  )
}

function isTauriRuntime() {
  return typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__)
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : typeof error === 'string' ? error : fallback
}
