import { CheckCircle, CircleNotch, DownloadSimple, WarningCircle, X } from '@phosphor-icons/react'
import { useEffect, useState, type CSSProperties } from 'react'

type UpdateState = 'idle' | 'checking' | 'available' | 'downloading' | 'installing' | 'updated' | 'error'

interface AvailableUpdate {
  version: string
  currentVersion?: string
  date?: string
  body?: string
  downloadAndInstall: (handler?: (event: DownloadEvent) => void) => Promise<void>
}

type DownloadEvent =
  | { event: 'Started'; data?: { contentLength?: number } }
  | { event: 'Progress'; data?: { chunkLength?: number } }
  | { event: 'Finished'; data?: unknown }
  | { event: string; data?: unknown }

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown
  }
}

export function DesktopUpdateIndicator() {
  const [state, setState] = useState<UpdateState>('idle')
  const [update, setUpdate] = useState<AvailableUpdate | null>(null)
  const [version, setVersion] = useState('')
  const [progress, setProgress] = useState(0)
  const [errorMessage, setErrorMessage] = useState('')
  const [cancelled, setCancelled] = useState(false)

  useEffect(() => {
    if (!isTauriRuntime()) return
    let disposed = false

    async function checkForUpdates() {
      try {
        setState('checking')
        const { check } = await import('@tauri-apps/plugin-updater')
        const nextUpdate = await check()
        if (disposed) return
        if (!nextUpdate) {
          setState('idle')
          return
        }
        setUpdate(nextUpdate)
        setVersion(nextUpdate.version)
        setState('available')
      } catch (error) {
        if (disposed) return
        console.warn('桌面端更新检查失败，已静默忽略。', error)
        setState('idle')
      }
    }

    void checkForUpdates()
    return () => {
      disposed = true
    }
  }, [])

  if (!isTauriRuntime() || cancelled || state === 'idle' || state === 'checking') return null

  async function installUpdate() {
    if (!update) return
    let downloaded = 0
    let contentLength = 0
    setProgress(0)
    setState('downloading')
    try {
      await update.downloadAndInstall((event) => {
        const eventData = typeof event.data === 'object' && event.data !== null ? event.data : {}
        if (event.event === 'Started' && 'contentLength' in eventData) {
          contentLength = Number(eventData.contentLength ?? 0)
          setProgress(0)
        }
        if (event.event === 'Progress' && 'chunkLength' in eventData) {
          downloaded += Number(eventData.chunkLength ?? 0)
          setProgress(contentLength > 0 ? Math.min(99, Math.round((downloaded / contentLength) * 100)) : 0)
        }
        if (event.event === 'Finished') {
          setProgress(100)
          setState('installing')
        }
      })
      setState('updated')
      const { relaunch } = await import('@tauri-apps/plugin-process')
      await relaunch()
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '桌面端更新下载或安装失败')
      setState('error')
    }
  }

  const failed = state === 'error'
  const label = failed
    ? '更新异常'
    : state === 'updated'
      ? '更新完成'
      : state === 'installing'
        ? '安装更新'
        : state === 'available'
          ? `发现新版本 ${version}`
          : `正在下载 ${progress || 0}%`
  const title = failed
    ? errorMessage
    : state === 'updated'
      ? '桌面端更新已完成，正在重启应用。'
      : state === 'installing'
        ? `正在安装 ${version}，应用会自动重启。`
        : state === 'available'
          ? `桌面端新版本 ${version} 已发布，可选择立即更新或稍后处理。`
          : `正在下载 ${version} 更新包。`

  return (
    <span
      className={`desktop-update-indicator${failed ? ' is-error' : ''}`}
      title={title}
      role="status"
      aria-live="polite"
      style={{ '--progress': `${progress}%` } as CSSProperties}
    >
      {failed ? <WarningCircle size={14} /> : state === 'updated' ? <CheckCircle size={14} /> : state === 'installing' ? <CircleNotch size={14} className="mcp-spin" /> : <DownloadSimple size={14} />}
      {label}
      {state === 'available' ? (
        <span className="desktop-update-actions">
          <button type="button" onClick={() => setCancelled(true)} title="本次启动不再提醒">
            <X size={12} />稍后
          </button>
          <button type="button" className="is-primary" onClick={() => void installUpdate()}>
            立即更新
          </button>
        </span>
      ) : null}
    </span>
  )
}

function isTauriRuntime() {
  return typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__)
}
