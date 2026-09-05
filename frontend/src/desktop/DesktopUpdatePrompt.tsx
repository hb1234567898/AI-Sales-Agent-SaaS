import { ArrowClockwise, CheckCircle, DownloadSimple, WarningCircle, X } from '@phosphor-icons/react'
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

export function DesktopUpdatePrompt() {
  const [state, setState] = useState<UpdateState>('idle')
  const [update, setUpdate] = useState<AvailableUpdate | null>(null)
  const [dismissed, setDismissed] = useState(false)
  const [progress, setProgress] = useState(0)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (!isTauriRuntime()) return
    let cancelled = false

    async function checkForUpdates() {
      try {
        setState('checking')
        const { check } = await import('@tauri-apps/plugin-updater')
        const nextUpdate = await check()
        if (cancelled) return
        if (!nextUpdate) {
          setState('idle')
          return
        }
        setUpdate(nextUpdate)
        setState('available')
      } catch (error) {
        if (cancelled) return
        setErrorMessage(error instanceof Error ? error.message : '检查更新失败')
        setState('error')
      }
    }

    void checkForUpdates()
    return () => {
      cancelled = true
    }
  }, [])

  if (!isTauriRuntime() || dismissed || state === 'idle' || state === 'checking') return null

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
      setErrorMessage(error instanceof Error ? error.message : '下载或安装更新失败')
      setState('error')
    }
  }

  const installing = state === 'downloading' || state === 'installing'
  const failed = state === 'error'

  return (
    <aside className={`desktop-update-card${failed ? ' is-error' : ''}`} role="status" aria-live="polite">
      <button
        className="desktop-update-close"
        type="button"
        onClick={() => setDismissed(true)}
        disabled={installing}
        aria-label="稍后再说"
      >
        <X size={14} />
      </button>

      <span className="desktop-update-icon" aria-hidden>
        {failed ? <WarningCircle size={18} /> : state === 'updated' ? <CheckCircle size={18} /> : <DownloadSimple size={18} />}
      </span>

      <div className="desktop-update-copy">
        <strong>{failed ? '更新检查失败' : state === 'updated' ? '更新已安装' : `发现新版本 ${update?.version ?? ''}`}</strong>
        <span>
          {failed
            ? errorMessage
            : installing
              ? state === 'installing' ? '正在启动安装程序，请根据提示完成更新。' : `正在下载更新包 ${progress || 0}%`
              : update?.body || 'GitHub Releases 已发布新的桌面端版本。'}
        </span>
        {installing ? <i style={{ '--progress': `${progress}%` } as CSSProperties} /> : null}
      </div>

      {!failed && !installing && state !== 'updated' ? (
        <button className="desktop-update-action" type="button" onClick={() => void installUpdate()}>
          <ArrowClockwise size={14} />立即更新
        </button>
      ) : null}
    </aside>
  )
}

function isTauriRuntime() {
  return typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__)
}
