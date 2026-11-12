import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { MarkdownContent } from './MarkdownContent'

describe('MarkdownContent', () => {
  afterEach(cleanup)

  it('renders GFM formatting and opens safe links in a separate tab', () => {
    render(<MarkdownContent content={'# 产品资料\n\n> 报价须审批\n\n- [x] 已确认\n- [ ] 待跟进\n\n~~旧价格~~ 和 `product_id`\n\n[产品文档](https://example.com/docs)'} />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('产品资料')
    expect(screen.getByText('报价须审批').closest('blockquote')).not.toBeNull()
    expect(screen.getAllByRole('checkbox').map((input) => (input as HTMLInputElement).checked)).toEqual([true, false])
    expect(screen.getAllByRole('checkbox').every((input) => (input as HTMLInputElement).disabled)).toBe(true)
    expect(screen.getByText('旧价格').tagName).toBe('DEL')
    expect(screen.getByText('product_id').tagName).toBe('CODE')
    expect(screen.getByRole('link', { name: '产品文档' })).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('does not turn document HTML or unsafe URLs into executable elements', () => {
    const { container } = render(<MarkdownContent content={'<script>alert(1)</script>\n\n<img src="x" onerror="alert(1)">\n\n[危险链接](javascript:alert%281%29)\n\n![危险图片](javascript:alert%281%29)\n\n正文仍可阅读'} />)
    expect(container.querySelector('script, img, [onerror], [onclick]')).toBeNull()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('危险链接')).toBeInTheDocument()
    expect(screen.getByText('正文仍可阅读')).toBeInTheDocument()
  })
})
