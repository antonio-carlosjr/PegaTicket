import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from '../button'

describe('<Button>', () => {
  it('renderiza o texto', () => {
    render(<Button>Salvar</Button>)
    expect(screen.getByRole('button', { name: /salvar/i })).toBeInTheDocument()
  })

  it('dispara onClick', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Clique</Button>)
    await userEvent.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('mostra spinner quando loading', () => {
    render(<Button loading>Carregando</Button>)
    const btn = screen.getByRole('button')
    expect(btn).toBeDisabled()
    expect(btn.querySelector('svg.animate-spin')).toBeTruthy()
  })

  it('respeita disabled', async () => {
    const onClick = vi.fn()
    render(<Button disabled onClick={onClick}>Desabilitado</Button>)
    await userEvent.click(screen.getByRole('button'))
    expect(onClick).not.toHaveBeenCalled()
  })

  it('aplica variant destructive', () => {
    render(<Button variant="destructive">Excluir</Button>)
    expect(screen.getByRole('button').className).toMatch(/bg-destructive/)
  })
})
