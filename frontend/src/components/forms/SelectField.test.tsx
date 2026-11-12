import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { SelectField } from './SelectField'

const options = [
  { value: 'all', label: '全部阶段' },
  { value: 'trial', label: '产品试用' },
  { value: 'review', label: '技术评审' },
]

function TestSelect() {
  const [value, setValue] = useState('all')
  return <SelectField value={value} options={options} onChange={setValue} ariaLabel="筛选商机阶段" />
}

describe('SelectField', () => {
  it('portals table dropdowns into their dialog and still selects pointer options', async () => {
    const user = userEvent.setup()
    const { unmount } = render(<dialog open aria-label="报价"><div style={{ overflow: 'hidden' }}><SelectField portal value="all" options={options} onChange={() => {}} ariaLabel="弹窗阶段" /></div></dialog>)
    await user.click(screen.getByRole('combobox', { name: '弹窗阶段' }))
    expect(screen.getByRole('listbox', { name: '弹窗阶段' }).parentElement?.tagName).toBe('DIALOG')
    window.dispatchEvent(new Event('resize'))
    await user.click(screen.getByRole('option', { name: '技术评审' }))
    expect(screen.queryByRole('listbox', { name: '弹窗阶段' })).not.toBeInTheDocument()
    unmount()
  })
  it('Escape closes an open dropdown without cancelling its parent dialog', async () => {
    const user = userEvent.setup()
    const { unmount } = render(<dialog open><SelectField portal value="all" options={options} onChange={() => {}} ariaLabel="报价阶段" /></dialog>)
    const trigger = screen.getByRole('combobox', { name: '报价阶段' })
    await user.click(trigger); await user.keyboard('{Escape}')
    expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect(trigger.closest('dialog')).toHaveAttribute('open')
    unmount()
  })
  it('supports pointer and keyboard selection', async () => {
    const user = userEvent.setup()
    render(<TestSelect />)

    const trigger = screen.getByRole('combobox', { name: '筛选商机阶段' })
    await user.click(trigger)
    await user.click(screen.getByRole('option', { name: '产品试用' }))
    expect(trigger).toHaveTextContent('产品试用')

    trigger.focus()
    await user.keyboard('{ArrowUp}{ArrowUp}{Enter}')
    expect(trigger).toHaveTextContent('全部阶段')
  })
})
