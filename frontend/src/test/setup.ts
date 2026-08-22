import '@testing-library/jest-dom/vitest'

Object.defineProperty(globalThis, 'NodeFilter', {
  value: window.NodeFilter,
  configurable: true,
})
