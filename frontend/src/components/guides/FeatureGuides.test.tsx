import { useCallback, useState } from 'react'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Config, Driver, HookOpts } from 'driver.js'
import { FeatureGuides, type GuideMode } from './FeatureGuides'
import { featureGuides } from './feature-guides'
import { guideSignature } from './guide-progress'

const harness = vi.hoisted(() => ({ config: {} as Config, index: 0, moveTo: vi.fn(), drive: vi.fn() }))
vi.mock('driver.js', () => ({ driver: (config: Config) => {
  harness.config = config
  return { drive: harness.drive, destroy: vi.fn(), getActiveIndex: () => harness.index, getConfig: () => harness.config,
    setConfig: (next: Config) => { harness.config = next }, moveTo: harness.moveTo }
} }))
const originalClose = HTMLDialogElement.prototype.close
const originalShow = HTMLDialogElement.prototype.showModal
beforeAll(() => {
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
})
afterAll(() => { HTMLDialogElement.prototype.close = originalClose; HTMLDialogElement.prototype.showModal = originalShow })
afterEach(() => { cleanup(); sessionStorage.clear(); vi.clearAllMocks(); vi.restoreAllMocks() })
beforeEach(() => { harness.config = {}; harness.index = 0 })
function Example() {
  const [mode, setMode] = useState<GuideMode>('current')
  const close = useCallback(() => setMode(null), [])
  return <><main className="app-main"><h1>今日工作台</h1><div className="metric-strip" /><div className="priority-panel" /></main><FeatureGuides mode={mode} onClose={close} role="GUEST" userId="guest" /></>
}
function options(index: number): HookOpts { return { config: harness.config, state: { activeIndex: index }, driver: {} as Driver, index } }

function KnowledgeExample() {
  const [mode, setMode] = useState<GuideMode>('current')
  const [tab, setTab] = useState('资料库')
  const [opened, setOpened] = useState(false)
  const close = useCallback(() => setMode(null), [])
  return <><main className="app-main"><h1>企业知识库</h1><div className="knowledge-tabs">{['资料库', '知识问答'].map(label => <button key={label} role="tab" aria-selected={tab === label} onClick={() => setTab(label)}>{label}</button>)}</div>
    {tab === '资料库' ? <div className="knowledge-library"><table className="knowledge-table"><tbody><tr><td><button className="knowledge-document-link" onClick={() => setOpened(true)}>产品手册.md</button><span className="knowledge-status is-ready" /></td></tr></tbody></table></div> : <form className="knowledge-question" />}
    {opened && <aside className="knowledge-drawer"><button aria-label="关闭资料详情" onClick={() => setOpened(false)}>关闭</button><div className="knowledge-preview" aria-label="Markdown预览" /></aside>}
  </main><FeatureGuides mode={mode} onClose={close} role="GUEST" userId="guest" /></>
}

describe('feature tour progress', () => {
  it('does not let delayed highlight callbacks overwrite a newer previous-step selection', async () => {
    harness.index = 0
    harness.moveTo.mockImplementation((index: number) => { harness.index = index })
    render(<MemoryRouter initialEntries={['/app/today']}><Example /></MemoryRouter>)
    await waitFor(() => expect(harness.config.onNextClick).toBeTypeOf('function'))
    harness.config.onNextClick?.(undefined, {}, options(0))
    await waitFor(() => expect(harness.index).toBe(1))
    harness.config.onNextClick?.(undefined, {}, options(1))
    await waitFor(() => expect(harness.index).toBe(2))
    harness.config.onPrevClick?.(undefined, {}, options(2))
    await waitFor(() => expect(harness.index).toBe(1))
    harness.config.onHighlighted?.(undefined, {}, options(2))
    const progress = JSON.parse(sessionStorage.getItem('sales-agent:feature-guides:v1:guest')!)
    expect(progress.today).toEqual({ step: 1, completed: false, signature: guideSignature(featureGuides.find(guide => guide.id === 'today')!) })
  })
  it('shows updated instructions while preserving completion for unchanged guides', () => {
    const today = featureGuides.find(guide => guide.id === 'today')!
    sessionStorage.setItem('sales-agent:feature-guides:v1:guest', JSON.stringify({ today: { step: 0, completed: true, signature: guideSignature(today) }, knowledge: { step: 4, completed: true, signature: 'previous-content' } }))
    render(<MemoryRouter initialEntries={['/app/knowledge']}><FeatureGuides mode="catalog" onClose={vi.fn()} role="GUEST" userId="guest" /></MemoryRouter>)
    const dialog = screen.getByRole('dialog', { name: '功能使用指南' })
    expect(within(dialog).getByText(`已完成 1 / ${featureGuides.filter(guide => !guide.adminOnly).length}`)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: /今日工作台.*已完成/ })).toHaveClass('is-complete')
    expect(within(dialog).getByRole('button', { name: /企业知识库.*有更新/ })).not.toHaveClass('is-complete')
    expect(within(dialog).getByRole('heading', { name: '预览 Markdown 与核对源码' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '查看更新引导' })).toBeInTheDocument()
  })
  it('starts an updated current-page tour from step zero instead of an obsolete index', async () => {
    sessionStorage.setItem('sales-agent:feature-guides:v1:guest', JSON.stringify({ today: { step: 3, completed: false, signature: 'old' } }))
    harness.index = 0
    render(<MemoryRouter initialEntries={['/app/today']}><Example /></MemoryRouter>)
    await waitFor(() => expect(harness.drive).toHaveBeenCalledWith(0))
    act(() => harness.config.onHighlighted?.(undefined, {}, options(0)))
    await waitFor(() => expect(JSON.parse(sessionStorage.getItem('sales-agent:feature-guides:v1:guest')!).today).toEqual({ step: 0, completed: false, signature: guideSignature(featureGuides.find(guide => guide.id === 'today')!) }))
  })
  it('opens the knowledge preview, highlights it before the library, and closes it for the question step', async () => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue([{}] as unknown as DOMRectList)
    const guide = featureGuides.find(guide => guide.id === 'knowledge')!
    const index = guide.steps.findIndex(step => step.reveal === 'knowledge-preview')
    sessionStorage.setItem('sales-agent:feature-guides:v1:guest', JSON.stringify({ knowledge: { step: index, completed: false, signature: guideSignature(guide) } }))
    harness.moveTo.mockImplementation(index => { harness.index = index })
    render(<MemoryRouter initialEntries={['/app/knowledge']}><KnowledgeExample /></MemoryRouter>)
    await waitFor(() => expect(harness.drive).toHaveBeenCalledWith(index))
    expect(harness.config.steps?.[index].element).toBe(screen.getByLabelText('Markdown预览'))
    harness.index = index
    act(() => harness.config.onNextClick?.(undefined, {}, options(index)))
    await waitFor(() => expect(harness.moveTo).toHaveBeenCalledWith(index + 1))
    expect(screen.queryByLabelText('Markdown预览')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '知识问答' })).toHaveAttribute('aria-selected', 'true')
  })
})
