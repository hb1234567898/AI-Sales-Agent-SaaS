import { afterEach, describe, expect, it } from 'vitest'
import { featureGuides } from './feature-guides'
import { currentProgress, guideSignature, readProgress } from './guide-progress'

const guide = featureGuides.find(guide => guide.id === 'knowledge')!
const saved = { step: 2, completed: true, signature: guideSignature(guide) }

describe('guide content updates', () => {
  afterEach(() => sessionStorage.clear())

  it('preserves progress while a guide is unchanged', () => {
    expect(currentProgress(guide, saved)).toEqual({ ...saved, updated: false })
    expect(currentProgress(guide)).toBeUndefined()
  })

  it('marks an extended guide as updated without clearing other guides', () => {
    const extended = { ...guide, steps: [...guide.steps, { ...guide.steps[0], title: '新增功能' }] }
    expect(currentProgress(extended, saved)).toEqual({ step: 0, completed: false, updated: true })
    expect(currentProgress(guide, saved)?.completed).toBe(true)
  })

  it('restarts when steps change order or their instructions change', () => {
    const reordered = { ...guide, steps: [...guide.steps].reverse() }
    expect(currentProgress(reordered, saved)?.updated).toBe(true)
    const edited = { ...guide, steps: guide.steps.map((step, index) => index ? step : { ...step, description: '新版操作说明' }) }
    expect(currentProgress(edited, saved)?.updated).toBe(true)
  })

  it('recognizes legacy and invalid step progress as needing the current guide', () => {
    expect(currentProgress(guide, { step: 1, completed: true })?.updated).toBe(true)
    expect(currentProgress(guide, { ...saved, step: 999 })).toEqual({ step: 0, completed: false, updated: true })
  })

  it('ignores malformed progress but keeps valid entries', () => {
    sessionStorage.setItem('progress', JSON.stringify({ knowledge: saved, broken: null, negative: { step: -1, completed: true }, text: 'bad' }))
    expect(readProgress('progress')).toEqual({ knowledge: saved })
    sessionStorage.setItem('progress', '{broken json')
    expect(readProgress('progress')).toEqual({})
  })
})
