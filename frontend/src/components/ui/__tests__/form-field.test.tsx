import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FormField } from '../form-field'
import { Input } from '../input'

describe('<FormField>', () => {
  it('renderiza label + children', () => {
    render(
      <FormField label="Nome" htmlFor="nome">
        <Input id="nome" />
      </FormField>
    )
    expect(screen.getByText('Nome')).toBeInTheDocument()
    expect(screen.getByLabelText('Nome')).toBeInTheDocument()
  })

  it('mostra asterisco quando required', () => {
    render(
      <FormField label="Email" required>
        <Input />
      </FormField>
    )
    expect(screen.getByText('*')).toBeInTheDocument()
  })

  it('mostra mensagem de erro', () => {
    render(
      <FormField label="Email" error={{ type: 'required', message: 'E obrigatorio' }}>
        <Input />
      </FormField>
    )
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('E obrigatorio')
  })

  it('mostra hint quando nao ha erro', () => {
    render(
      <FormField label="Senha" hint="Min 6 chars">
        <Input />
      </FormField>
    )
    expect(screen.getByText('Min 6 chars')).toBeInTheDocument()
  })

  it('erro tem prioridade sobre hint', () => {
    render(
      <FormField label="X" hint="dica" error={{ type: 'x', message: 'falhou' }}>
        <Input />
      </FormField>
    )
    expect(screen.getByText('falhou')).toBeInTheDocument()
    expect(screen.queryByText('dica')).not.toBeInTheDocument()
  })
})
