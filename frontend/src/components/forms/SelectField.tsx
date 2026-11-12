import { CaretDown, Check } from '@phosphor-icons/react'
import { useEffect, useId, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

interface SelectFieldProps {
  value: string
  options: readonly SelectOption[]
  onChange: (value: string) => void
  ariaLabel: string
  placeholder?: string
  className?: string
  disabled?: boolean
  portal?: boolean
}

export function SelectField({ value, options, onChange, ariaLabel, placeholder = '请选择', className = '', disabled = false, portal = false }: SelectFieldProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [menuHost, setMenuHost] = useState<Element | null>(null)
  const [menuStyle, setMenuStyle] = useState<CSSProperties>()
  const listboxId = useId()
  const selectedIndex = options.findIndex((option) => option.value === value)
  const selectedOption = selectedIndex >= 0 ? options[selectedIndex] : null

  useEffect(() => {
    const closeWhenClickingOutside = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node) && !menuRef.current?.contains(event.target as Node)) setIsOpen(false)
    }

    document.addEventListener('pointerdown', closeWhenClickingOutside)
    return () => document.removeEventListener('pointerdown', closeWhenClickingOutside)
  }, [])

  useEffect(() => {
    if (!isOpen || !portal) return
    const place = (event?: Event) => {
      if (event?.target instanceof Node && menuRef.current?.contains(event.target)) return
      const rect = rootRef.current?.getBoundingClientRect()
      if (!rect) return
      if (rect.bottom < 0 || rect.top > window.innerHeight || rect.right < 0 || rect.left > window.innerWidth) { setIsOpen(false); return }
      const below = window.innerHeight - rect.bottom, above = rect.top
      const upwards = below < 160 && above > below
      const width = Math.min(Math.max(rect.width, 166), window.innerWidth - 24)
      setMenuStyle({ position: 'fixed', left: Math.max(12, Math.min(rect.left, window.innerWidth - width - 12)), width, top: upwards ? 'auto' : rect.bottom + 5, bottom: upwards ? window.innerHeight - rect.top + 5 : 'auto', maxHeight: Math.max(70, Math.min(250, (upwards ? above : below) - 18)), zIndex: 1000 })
    }
    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true) }
  }, [isOpen, portal])

  const openMenu = () => {
    if (portal) setMenuHost(rootRef.current?.closest('dialog') ?? document.body)
    const firstEnabledIndex = options.findIndex((option) => !option.disabled)
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : Math.max(firstEnabledIndex, 0))
    setIsOpen(true)
  }

  const moveActiveOption = (direction: 1 | -1) => {
    if (!options.length) return
    let nextIndex = activeIndex
    do {
      nextIndex = (nextIndex + direction + options.length) % options.length
    } while (options[nextIndex]?.disabled && nextIndex !== activeIndex)
    setActiveIndex(nextIndex)
  }

  const selectOption = (option: SelectOption) => {
    if (option.disabled) return
    onChange(option.value)
    setIsOpen(false)
  }

  const menu = <div className="select-menu" ref={menuRef} id={listboxId} role="listbox" aria-label={ariaLabel} style={portal ? menuStyle : undefined}>
    {options.map((option, index) => <button className={`select-option${index === activeIndex ? ' is-active' : ''}${option.value === value ? ' is-selected' : ''}`} id={`${listboxId}-option-${index}`} key={option.value} type="button" role="option" aria-selected={option.value === value} disabled={option.disabled} tabIndex={-1} onMouseEnter={() => setActiveIndex(index)} onClick={() => selectOption(option)}><span className="select-option-check">{option.value === value ? <Check size={12} weight="bold" aria-hidden /> : null}</span><span>{option.label}</span></button>)}
  </div>
  return (
    <div className={`select-field${isOpen ? ' is-open' : ''}${disabled ? ' is-disabled' : ''}${className ? ` ${className}` : ''}`} ref={rootRef}>
      <button
        className="select-trigger"
        type="button"
        role="combobox"
        aria-label={ariaLabel}
        aria-expanded={isOpen}
        aria-controls={listboxId}
        aria-activedescendant={isOpen ? `${listboxId}-option-${activeIndex}` : undefined}
        disabled={disabled}
        onClick={() => isOpen ? setIsOpen(false) : openMenu()}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            if (!isOpen) openMenu()
            else moveActiveOption(event.key === 'ArrowDown' ? 1 : -1)
          } else if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            if (!isOpen) openMenu()
            else if (options[activeIndex]) selectOption(options[activeIndex])
          } else if (event.key === 'Escape') {
            if (isOpen) { event.preventDefault(); event.stopPropagation() }
            setIsOpen(false)
          } else if (event.key === 'Home' && isOpen) {
            event.preventDefault()
            setActiveIndex(Math.max(options.findIndex((option) => !option.disabled), 0))
          } else if (event.key === 'End' && isOpen) {
            event.preventDefault()
            const lastEnabledIndex = options.findLastIndex((option) => !option.disabled)
            setActiveIndex(Math.max(lastEnabledIndex, 0))
          } else if (event.key === 'Tab') {
            setIsOpen(false)
          }
        }}
      >
        <span className={selectedOption ? 'select-value' : 'select-value is-placeholder'}>{selectedOption?.label ?? placeholder}</span>
        <CaretDown className="select-caret" size={13} weight="bold" aria-hidden />
      </button>

      {isOpen ? portal ? menuHost && createPortal(menu, menuHost) : menu : null}
    </div>
  )
}
