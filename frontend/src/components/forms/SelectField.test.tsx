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
