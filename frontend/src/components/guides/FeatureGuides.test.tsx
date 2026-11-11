import { useCallback, useState } from 'react'
import { cleanup, render, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import type { Config, Driver, HookOpts } from 'driver.js'
import { FeatureGuides, type GuideMode } from './FeatureGuides'

const harness = vi.hoisted(() => ({ config: {} as Config, index: 0, moveTo: vi.fn() }))
vi.mock('driver.js', () => ({ driver: (config: Config) => {
  harness.config = config
  return { drive: vi.fn(), destroy: vi.fn(), getActiveIndex: () => harness.index, getConfig: () => harness.config,
    setConfig: (next: Config) => { harness.config = next }, moveTo: harness.moveTo }
} }))
const originalClose = HTMLDialogElement.prototype.close
beforeAll(() => { HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') } })
afterAll(() => { HTMLDialogElement.prototype.close = originalClose })
afterEach(() => { cleanup(); sessionStorage.clear(); vi.clearAllMocks() })
function Example() {
  const [mode, setMode] = useState<GuideMode>('current')
  const close = useCallback(() => setMode(null), [])
  return <><main className="app-main"><h1>今日工作台</h1><div className="metric-strip" /><div className="priority-panel" /></main><FeatureGuides mode={mode} onClose={close} role="GUEST" userId="guest" /></>
}
function options(index: number): HookOpts { return { config: harness.config, state: { activeIndex: index }, driver: {} as Driver, index } }

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
    expect(progress.today).toEqual({ step: 1, completed: false })
  })
})
