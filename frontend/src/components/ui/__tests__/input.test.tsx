import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Input } from '../input'

describe('<Input>', () => {
  it('aceita texto', async () => {
    render(<Input placeholder="nome" />)
    const input = screen.getByPlaceholderText('nome') as HTMLInputElement
    await userEvent.type(input, 'Ana')
    expect(input.value).toBe('Ana')
  })

  it('marca aria-invalid quando invalid=true', () => {
    render(<Input invalid placeholder="campo" />)
    expect(screen.getByPlaceholderText('campo')).toHaveAttribute('aria-invalid', 'true')
  })

  it('renderiza leftIcon', () => {
    render(<Input leftIcon={<span data-testid="ico" />} placeholder="x" />)
    expect(screen.getByTestId('ico')).toBeInTheDocument()
  })

  it('passa props padrao do html', () => {
    render(<Input type="email" autoComplete="email" placeholder="email" />)
    const input = screen.getByPlaceholderText('email')
    expect(input).toHaveAttribute('type', 'email')
    expect(input).toHaveAttribute('autocomplete', 'email')
  })
})
