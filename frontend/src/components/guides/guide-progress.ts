import type { FeatureGuide } from './feature-guides'

export interface GuideProgress { step: number; completed: boolean; signature?: string }
export type Progress = Record<string, GuideProgress>

// Derive the version from the content so adding/reordering steps never needs a manual version bump.
export function guideSignature(guide: FeatureGuide) {
  const content = JSON.stringify(guide)
  let hash = 2166136261
  for (let index = 0; index < content.length; index++) hash = Math.imul(hash ^ content.charCodeAt(index), 16777619)
  return (hash >>> 0).toString(36)
}

export function currentProgress(guide: FeatureGuide, stored?: GuideProgress) {
  if (!stored) return undefined
  if (stored.signature !== guideSignature(guide) || stored.step >= guide.steps.length) return { step: 0, completed: false, updated: true }
  return { ...stored, updated: false }
}

export function readProgress(key: string): Progress {
  try {
    const stored: unknown = JSON.parse(sessionStorage.getItem(key) ?? '{}')
    if (!stored || typeof stored !== 'object' || Array.isArray(stored)) return {}
    return Object.fromEntries(Object.entries(stored).filter(([, value]) => value && typeof value === 'object' && Number.isInteger(value.step) && value.step >= 0 && typeof value.completed === 'boolean'))
  } catch { return {} }
}
