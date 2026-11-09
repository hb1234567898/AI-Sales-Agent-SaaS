import { ArrowRight, BookOpen, CheckCircle, Circle, Play, X } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { Driver, DriveStep } from 'driver.js'
import 'driver.js/dist/driver.css'
import './feature-guides.css'
import { availableGuides, type FeatureGuide, type GuideStep } from './feature-guides'

export type GuideMode = 'catalog' | 'current' | null
interface GuideProgress { step: number; completed: boolean }
type Progress = Record<string, GuideProgress>
function readProgress(key: string): Progress {
  try {
    const stored: unknown = JSON.parse(sessionStorage.getItem(key) ?? '{}')
    if (!stored || typeof stored !== 'object') return {}
    return Object.fromEntries(Object.entries(stored).filter(([, value]) => value && typeof value.step === 'number' && Number.isInteger(value.step) && value.step >= 0 && typeof value.completed === 'boolean'))
  } catch { return {} }
}
function escapeText(value: string) { return value.replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]!) }
function visibleTarget(selector: string) {
  return [...document.querySelectorAll<HTMLElement>(selector)].find(element => element.getClientRects().length > 0)
}
function waitForPage(heading: string, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const ready = () => document.querySelector('.app-main h1')?.textContent?.includes(heading)
    if (ready()) { resolve(); return }
    const cleanup = () => { observer.disconnect(); window.clearTimeout(timeout); signal.removeEventListener('abort', abort) }
    const observer = new MutationObserver(() => { if (ready()) { cleanup(); resolve() } })
    const abort = () => { cleanup(); reject(new DOMException('Guide cancelled', 'AbortError')) }
    const timeout = window.setTimeout(() => { cleanup(); reject(new Error('页面尚未加载完成，请稍后重新开始本页引导。')) }, 10000)
    observer.observe(document.body, { childList: true, subtree: true })
    signal.addEventListener('abort', abort, { once: true })
  })
}
async function revealStep(step: GuideStep, signal: AbortSignal) {
  if (step.reveal?.startsWith('knowledge-')) {
    const label = step.reveal === 'knowledge-ask' ? '知识问答' : '资料库'
    const tab = [...document.querySelectorAll<HTMLButtonElement>('.knowledge-tabs [role="tab"]')].find(button => button.textContent?.includes(label))
    if (tab?.getAttribute('aria-selected') !== 'true') tab?.click()
  }
  if (step.reveal?.startsWith('customer-')) {
    if (!document.querySelector('.customer-drawer')) visibleTarget('.table-action-button')?.click()
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
    const label = step.reveal === 'customer-profile' ? '客户资料' : '互动记录'
    const tab = [...document.querySelectorAll<HTMLButtonElement>('.customer-drawer-tabs button')].find(button => button.textContent?.includes(label))
    if (!tab?.classList.contains('is-active')) tab?.click()
  }
  if (step.reveal === 'token-budget' && !document.querySelector('.mcp-token-modal')) visibleTarget('.mcp-quota-button')?.click()
  if (step.reveal === 'token-usage') document.querySelector<HTMLButtonElement>('.mcp-token-modal header [aria-label="关闭"]')?.click()
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
  if (signal.aborted) throw new DOMException('Guide cancelled', 'AbortError')
}

export function FeatureGuides({ mode, onClose, role, userId }: { mode: GuideMode; onClose: () => void; role: string; userId: string }) {
  const location = useLocation()
  const navigate = useNavigate()
  const guides = useMemo(() => availableGuides(role), [role])
  const current = guides.find(guide => guide.path === location.pathname)
  const storageKey = `sales-agent:feature-guides:v1:${role === 'GUEST' ? 'guest' : userId}`
  const [progress, setProgress] = useState<Progress>(() => readProgress(storageKey))
  const progressRef = useRef(progress)
  const [selection, setSelection] = useState({ id: current?.id ?? 'today', path: location.pathname })
  const selected = (selection.path === location.pathname ? guides.find(guide => guide.id === selection.id) : current) ?? guides[0]
  const [launch, setLaunch] = useState<{ guide: FeatureGuide; step: number } | null>(null)
  const [finished, setFinished] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [tourActive, setTourActive] = useState(false)
  const dialogRef = useRef<HTMLDialogElement>(null)
  const tourRef = useRef<Driver | null>(null)
  const totalCompleted = guides.filter(guide => progress[guide.id]?.completed).length

  const persist = useCallback((id: string, value: GuideProgress) => {
    const next = { ...progressRef.current, [id]: value }
    progressRef.current = next
    setProgress(next)
    sessionStorage.setItem(storageKey, JSON.stringify(next))
  }, [storageKey])

  const start = useCallback((guide: FeatureGuide, step = 0) => {
    tourRef.current?.destroy()
    onClose()
    setFinished(null)
    setError('')
    setSelection({ id: guide.id, path: guide.path })
    setLaunch({ guide, step: Math.min(step, guide.steps.length - 1) })
    navigate(guide.path)
  }, [navigate, onClose])

  useEffect(() => {
    if (mode !== 'current' || !current) return
    const frame = requestAnimationFrame(() => start(current, progressRef.current[current.id]?.completed ? 0 : progressRef.current[current.id]?.step ?? 0))
    return () => cancelAnimationFrame(frame)
  }, [mode, current, start])

  useEffect(() => {
    const dialog = dialogRef.current
    if (mode === 'catalog') {
      dialog?.showModal()
    } else dialog?.close()
  }, [mode, current])

  useEffect(() => {
    if (!launch || location.pathname !== launch.guide.path) return
    const controller = new AbortController()
    const guide = launch.guide
    const customerWasOpen = Boolean(document.querySelector('.customer-drawer'))
    const quotaWasOpen = Boolean(document.querySelector('.mcp-token-modal'))
    let tour: Driver | undefined
    let transitioning = false
    let displayedIndex = launch.step
    async function run() {
      try {
        const { driver } = await import('driver.js')
        if (controller.signal.aborted) return
        await waitForPage(guide.heading, controller.signal)
        await revealStep(guide.steps[launch!.step], controller.signal)
        const steps = (): DriveStep[] => guide.steps.map(step => ({
          element: visibleTarget(step.target),
          disableActiveInteraction: !step.allowInteraction,
          popover: {
            title: step.title,
            description: `<p>${escapeText(step.description)}</p><div class="feature-tour-result"><strong>完成后</strong><span>${escapeText(step.result)}</span></div>${role === 'GUEST' ? `<p class="feature-tour-guest">${escapeText(guide.guestNote)}</p>` : ''}`,
          },
        }))
        const finish = () => { persist(guide.id, { step: 0, completed: true }); setFinished(guide.id); tour?.destroy() }
        const move = async (index: number) => {
          if (transitioning) return
          if (index >= guide.steps.length) { finish(); return }
          transitioning = true
          try {
            await revealStep(guide.steps[index], controller.signal)
            displayedIndex = index
            persist(guide.id, { step: index, completed: progressRef.current[guide.id]?.completed ?? false })
            if (tour) tour.setConfig({ ...tour.getConfig(), steps: steps() })
            tour?.moveTo(index)
          }
          catch (reason) { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '引导暂时不可用') }
          finally { transitioning = false }
        }
        tour = driver({
          steps: steps(), animate: false, smoothScroll: false, stagePadding: 6, stageRadius: 6,
          disableActiveInteraction: true, overlayClickBehavior: 'none', overlayOpacity: 0.4,
          popoverClass: 'feature-tour', showProgress: true, progressText: '{{current}} / {{total}}',
          nextBtnText: '下一步', prevBtnText: '上一步', doneBtnText: '完成本功能', closeBtnLabel: '暂停引导',
          onNextClick: () => { void move((tour?.getActiveIndex() ?? 0) + 1) },
          onPrevClick: () => { void move(Math.max(0, (tour?.getActiveIndex() ?? 0) - 1)) },
          onDoneClick: finish,
          onHighlighted: () => { setTourActive(true); persist(guide.id, { step: displayedIndex, completed: progressRef.current[guide.id]?.completed ?? false }) },
          onDestroyed: () => { if (tourRef.current === tour) tourRef.current = null; setTourActive(false); setLaunch(null) },
        })
        tourRef.current = tour
        tour.drive(launch!.step)
      } catch (reason) {
        if (!controller.signal.aborted) { setError(reason instanceof Error ? reason.message : '引导暂时不可用'); setLaunch(null) }
      }
    }
    void run()
    return () => {
      controller.abort(); tour?.destroy()
      if (!customerWasOpen && guide.id === 'customers') document.querySelector<HTMLButtonElement>('.customer-drawer .drawer-close')?.click()
      if (!quotaWasOpen && guide.id === 'token-quota') document.querySelector<HTMLButtonElement>('.mcp-token-modal header [aria-label="关闭"]')?.click()
    }
  }, [launch, location.pathname, persist, role])

  const selectedProgress = progress[selected.id]
  const finishedGuide = guides.find(guide => guide.id === finished)
  const nextGuide = finishedGuide ? guides[guides.indexOf(finishedGuide) + 1] : undefined

  return <>
    <dialog className="feature-guide-dialog" ref={dialogRef} aria-labelledby="feature-guides-title" onCancel={onClose} onClick={event => { if (event.target === event.currentTarget) { const rect = event.currentTarget.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) onClose() } }}>
      <header className="feature-guide-header"><div><BookOpen size={22} /><span><h2 id="feature-guides-title">功能使用指南</h2><p>跟着页面一步步了解每个功能</p></span></div><button className="icon-button" aria-label="关闭使用引导" type="button" onClick={onClose}><X size={20} /></button></header>
      <div className="feature-guide-layout">
        <nav className="feature-guide-directory" aria-label="功能引导目录">
          <div className="feature-guide-overall"><strong>已完成 {totalCompleted} / {guides.length}</strong><progress aria-label="功能引导完成进度" value={totalCompleted} max={guides.length} /></div>
          {(['销售工作', 'AI 与协作', '配置与管理'] as const).map(group => <section key={group}><h3>{group}</h3>{guides.filter(guide => guide.group === group).map(guide => <button type="button" key={guide.id} className={progress[guide.id]?.completed ? 'is-complete' : undefined} aria-current={selected.id === guide.id ? 'true' : undefined} onClick={() => setSelection({ id: guide.id, path: location.pathname })}>{progress[guide.id]?.completed ? <CheckCircle size={17} weight="fill" /> : <Circle size={17} />}<span>{guide.title}<small>{guide.steps.length} 个步骤{progress[guide.id]?.completed ? ' · 已完成' : progress[guide.id] ? ' · 已开始' : ''}</small></span></button>)}</section>)}
        </nav>
        <article className="feature-guide-detail" aria-label={`${selected.title}功能说明`}>
          <p className="feature-guide-category">{selected.group} · {selected.steps.length} 个步骤</p>
          <h3>{selected.title}</h3><p className="feature-guide-summary">{selected.summary}</p>
          <dl className="feature-guide-preparation"><dt>开始前</dt><dd>{selected.prerequisite}</dd>{role === 'GUEST' && <><dt>游客体验</dt><dd>{selected.guestNote}</dd></>}</dl>
          <ol className="feature-guide-outline">{selected.steps.map((step, index) => <li key={step.title}><span>{index + 1}</span><div><h4>{step.title}</h4><p>{step.description}</p><small>完成后：{step.result}</small></div></li>)}</ol>
          <footer className="feature-guide-detail-footer"><p>导览会高亮页面区域，可随时暂停；进度在当前浏览会话中保留。</p><div>{selectedProgress && !selectedProgress.completed && selectedProgress.step > 0 && <button className="button button-secondary" onClick={() => start(selected, selectedProgress.step)}><Play size={15} />继续第 {selectedProgress.step + 1} 步</button>}<button className="button button-primary" onClick={() => start(selected)}><Play size={15} weight="fill" />{selectedProgress?.completed ? '重新查看引导' : '开始逐步引导'}</button></div></footer>
        </article>
      </div>
    </dialog>
    {launch && !tourActive && <div className="feature-guide-notice" role="status">正在打开{launch.guide.title}…</div>}
    {error && <div className="feature-guide-notice is-error" role="alert"><span>{error}</span><button className="icon-button" onClick={() => setError('')} aria-label="关闭引导提示"><X size={16} /></button></div>}
    {finishedGuide && <div className="feature-guide-notice" role="status"><CheckCircle size={20} /><span>已完成“{finishedGuide.title}”引导</span>{nextGuide && <button className="button button-secondary" onClick={() => start(nextGuide)}>下一功能<ArrowRight size={14} /></button>}<button className="icon-button" onClick={() => setFinished(null)} aria-label="关闭完成提示"><X size={16} /></button></div>}
  </>
}
