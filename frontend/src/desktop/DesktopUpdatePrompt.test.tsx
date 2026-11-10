import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { DownloadEvent, Update } from '@tauri-apps/plugin-updater'
import { StrictMode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DesktopUpdateIndicator } from './DesktopUpdatePrompt'

const plugin = vi.hoisted(() => ({ check: vi.fn(), relaunch: vi.fn() }))
vi.mock('@tauri-apps/plugin-updater', () => ({ check: plugin.check }))
vi.mock('@tauri-apps/plugin-process', () => ({ relaunch: plugin.relaunch }))

const interval = 5 * 60_000

function available(version = '1.2.2') {
  return {
    version,
    download: vi.fn<Update['download']>().mockResolvedValue(undefined),
    install: vi.fn<Update['install']>().mockResolvedValue(undefined),
    close: vi.fn<Update['close']>().mockResolvedValue(undefined),
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

async function advance(ms = 0) {
  await act(async () => { await vi.advanceTimersByTimeAsync(ms) })
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.stubGlobal('__TAURI_INTERNALS__', {})
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
  plugin.check.mockReset().mockResolvedValue(null)
  plugin.relaunch.mockReset().mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('desktop application updates', () => {
  it('does not call native updater APIs in a browser', async () => {
    vi.stubGlobal('__TAURI_INTERNALS__', undefined)
    const view = render(<DesktopUpdateIndicator />)
    await advance(interval)
    expect(plugin.check).not.toHaveBeenCalled()
    expect(view.container).toBeEmptyDOMElement()
  })

  it('finds a release after startup and changes the download icon to a check only after verification', async () => {
    const update = available()
    const transfer = deferred<void>()
    update.download.mockReturnValue(transfer.promise)
    plugin.check.mockResolvedValueOnce(null).mockResolvedValueOnce(update)
    render(<DesktopUpdateIndicator />)
    await advance()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    await advance(interval)
    expect(plugin.check).toHaveBeenCalledTimes(2)
    const report = update.download.mock.calls[0][0]!
    act(() => {
      report({ event: 'Started', data: { contentLength: 100 } })
      report({ event: 'Progress', data: { chunkLength: 40 } })
    })
    const downloading = screen.getByRole('button', { name: '正在下载新版本 1.2.2，40%' })
    expect(downloading.querySelector('svg')).not.toBeNull()
    expect(downloading).not.toHaveClass('is-ready')
    act(() => report({ event: 'Finished' }))
    expect(screen.queryByRole('button', { name: /已下载/ })).not.toBeInTheDocument()
    await act(async () => transfer.resolve())
    const ready = screen.getByRole('button', { name: '新版本 1.2.2 已下载，点击安装' })
    expect(ready).toHaveClass('is-ready')
    expect(update.install).not.toHaveBeenCalled()
    fireEvent.click(ready)
    expect(screen.getByRole('dialog', { name: '桌面应用更新' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '稍后' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(ready).toBeInTheDocument()
    fireEvent.click(ready)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(ready).toHaveFocus()
    fireEvent.click(ready)
    fireEvent.click(screen.getByRole('button', { name: '安装并重启' }))
    await advance()
    expect(update.install).toHaveBeenCalledTimes(1)
    expect(plugin.relaunch).toHaveBeenCalledTimes(1)
  })

  it('checks again on focus, reconnect and visibility changes with a shared cooldown', async () => {
    render(<DesktopUpdateIndicator />)
    await advance()
    await advance(59_000)
    fireEvent(window, new Event('focus'))
    await advance()
    expect(plugin.check).toHaveBeenCalledTimes(1)
    await advance(1000)
    fireEvent(window, new Event('focus'))
    fireEvent(window, new Event('online'))
    fireEvent(document, new Event('visibilitychange'))
    await advance()
    expect(plugin.check).toHaveBeenCalledTimes(2)
    await advance(60_000)
    fireEvent(window, new Event('online'))
    await advance()
    expect(plugin.check).toHaveBeenCalledTimes(3)
    await advance(60_000)
    vi.mocked(Object.getOwnPropertyDescriptor(document, 'visibilityState')!.get!).mockReturnValue('hidden')
    fireEvent(document, new Event('visibilitychange'))
    await advance()
    expect(plugin.check).toHaveBeenCalledTimes(3)
    vi.mocked(Object.getOwnPropertyDescriptor(document, 'visibilityState')!.get!).mockReturnValue('visible')
    fireEvent(document, new Event('visibilitychange'))
    await advance()
    expect(plugin.check).toHaveBeenCalledTimes(4)
  })

  it('serializes checks and transfers, and never replaces a ready package with another copy of the same release', async () => {
    const check = deferred<ReturnType<typeof available>>()
    const transfer = deferred<void>()
    const update = available()
    const duplicate = available()
    update.download.mockReturnValue(transfer.promise)
    plugin.check.mockReturnValueOnce(check.promise).mockResolvedValue(duplicate)
    render(<DesktopUpdateIndicator />)
    await advance(interval)
    expect(plugin.check).toHaveBeenCalledTimes(1)
    await act(async () => check.resolve(update))
    await advance(interval)
    expect(plugin.check).toHaveBeenCalledTimes(1)
    expect(update.download).toHaveBeenCalledTimes(1)
    await act(async () => transfer.resolve())
    await advance(interval)
    expect(plugin.check).toHaveBeenCalledTimes(2)
    expect(duplicate.download).not.toHaveBeenCalled()
    expect(duplicate.close).toHaveBeenCalledTimes(1)
    expect(update.close).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /已下载/ })).toBeInTheDocument()
  })

  it('can discover a newer release while a previously downloaded release is waiting', async () => {
    const older = available('1.2.2')
    const newer = available('1.2.3')
    plugin.check.mockResolvedValueOnce(older).mockResolvedValueOnce(newer)
    render(<DesktopUpdateIndicator />)
    await advance()
    expect(screen.getByRole('button', { name: '新版本 1.2.2 已下载，点击安装' })).toBeInTheDocument()
    await advance(interval)
    expect(older.close).toHaveBeenCalledTimes(1)
    expect(newer.download).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: '新版本 1.2.3 已下载，点击安装' })).toBeInTheDocument()
  })

  it('recovers from failed checks and skips requests while offline', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    plugin.check.mockRejectedValueOnce(new Error('offline'))
    render(<DesktopUpdateIndicator />)
    await advance()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    vi.mocked(Object.getOwnPropertyDescriptor(navigator, 'onLine')!.get!).mockReturnValue(false)
    await advance(interval)
    expect(plugin.check).toHaveBeenCalledTimes(1)
    vi.mocked(Object.getOwnPropertyDescriptor(navigator, 'onLine')!.get!).mockReturnValue(true)
    fireEvent(window, new Event('online'))
    await advance()
    expect(plugin.check).toHaveBeenCalledTimes(2)
  })

  it('offers a download retry without ever automatically installing', async () => {
    const update = available()
    update.download.mockRejectedValueOnce('签名校验失败')
    plugin.check.mockResolvedValue(update)
    render(<DesktopUpdateIndicator />)
    await advance()
    fireEvent.click(screen.getByRole('button', { name: '应用更新失败' }))
    expect(screen.getByText('签名校验失败')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '重试下载' }))
    await advance()
    expect(update.download).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('button', { name: /已下载/ })).toHaveClass('is-ready')
    expect(update.install).not.toHaveBeenCalled()
  })

  it('retries installation and restart separately without reinstalling after a restart failure', async () => {
    const update = available()
    update.install.mockRejectedValueOnce(new Error('安装被取消'))
    plugin.relaunch.mockRejectedValueOnce(new Error('重启失败'))
    plugin.check.mockResolvedValue(update)
    render(<DesktopUpdateIndicator />)
    await advance()
    fireEvent.click(screen.getByRole('button', { name: /已下载/ }))
    fireEvent.click(screen.getByRole('button', { name: '安装并重启' }))
    await advance()
    expect(screen.getByText('安装被取消')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '重试安装' }))
    await advance()
    expect(screen.getByText('重启失败')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '重试重启' }))
    await advance()
    expect(update.download).toHaveBeenCalledTimes(1)
    expect(update.install).toHaveBeenCalledTimes(2)
    expect(plugin.relaunch).toHaveBeenCalledTimes(2)
  })

  it('keeps a background check from replacing the package during installation', async () => {
    const update = available()
    const newer = available('1.2.3')
    const check = deferred<ReturnType<typeof available>>()
    const install = deferred<void>()
    update.install.mockReturnValue(install.promise)
    plugin.check.mockResolvedValueOnce(update).mockReturnValueOnce(check.promise)
    render(<DesktopUpdateIndicator />)
    await advance()
    await advance(interval)
    fireEvent.click(screen.getByRole('button', { name: /已下载/ }))
    fireEvent.click(screen.getByRole('button', { name: '安装并重启' }))
    await act(async () => check.resolve(newer))
    expect(update.install).toHaveBeenCalledTimes(1)
    expect(newer.download).not.toHaveBeenCalled()
    expect(newer.close).toHaveBeenCalledTimes(1)
    expect(update.close).not.toHaveBeenCalled()
    await act(async () => install.resolve())
  })

  it('cleans up timers and late check results on unmount', async () => {
    const pending = deferred<ReturnType<typeof available>>()
    const update = available()
    plugin.check.mockReturnValue(pending.promise)
    const view = render(<DesktopUpdateIndicator />)
    await advance()
    view.unmount()
    await act(async () => pending.resolve(update))
    await advance(interval)
    fireEvent(window, new Event('focus'))
    expect(plugin.check).toHaveBeenCalledTimes(1)
    expect(update.download).not.toHaveBeenCalled()
    expect(update.close).toHaveBeenCalledTimes(1)
  })

  it('releases a download only once it has finished after unmount', async () => {
    const transfer = deferred<void>()
    const update = available()
    update.download.mockReturnValue(transfer.promise)
    plugin.check.mockResolvedValue(update)
    const view = render(<DesktopUpdateIndicator />)
    await advance()
    view.unmount()
    expect(update.close).not.toHaveBeenCalled()
    const report = update.download.mock.calls[0][0] as (event: DownloadEvent) => void
    act(() => report({ event: 'Progress', data: { chunkLength: 50 } }))
    await act(async () => transfer.resolve())
    expect(update.close).toHaveBeenCalledTimes(1)
    expect(update.install).not.toHaveBeenCalled()
  })

  it('does not duplicate downloads in React StrictMode', async () => {
    const abandoned = available()
    const update = available()
    plugin.check.mockResolvedValueOnce(abandoned).mockResolvedValueOnce(update)
    render(<StrictMode><DesktopUpdateIndicator /></StrictMode>)
    await advance()
    expect(abandoned.close).toHaveBeenCalledTimes(1)
    expect(abandoned.download).not.toHaveBeenCalled()
    expect(update.download).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: /已下载/ })).toBeInTheDocument()
  })
})
