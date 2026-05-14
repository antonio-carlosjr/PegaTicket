import { describe, expect, it } from 'vitest'
import {
  loginSchema,
  registerParticipanteSchema,
  registerPromotorSchema,
  resetSchema,
  forgotSchema,
} from '../validation'

describe('validation schemas', () => {
  describe('loginSchema', () => {
    it('aceita email valido e senha', () => {
      const r = loginSchema.safeParse({ email: 'a@b.com', senha: 'qualquer' })
      expect(r.success).toBe(true)
    })

    it('rejeita email invalido', () => {
      const r = loginSchema.safeParse({ email: 'naoeemail', senha: 'qualquer' })
      expect(r.success).toBe(false)
    })

    it('rejeita senha vazia', () => {
      const r = loginSchema.safeParse({ email: 'a@b.com', senha: '' })
      expect(r.success).toBe(false)
    })
  })

  describe('registerParticipanteSchema', () => {
    it('aceita dados validos', () => {
      const r = registerParticipanteSchema.safeParse({
        nome: 'Ana Silva',
        email: 'ana@x.com',
        senha: 'senha123',
      })
      expect(r.success).toBe(true)
    })

    it('rejeita senha curta', () => {
      const r = registerParticipanteSchema.safeParse({
        nome: 'Ana',
        email: 'a@b.com',
        senha: '123',
      })
      expect(r.success).toBe(false)
    })

    it('rejeita nome com numeros', () => {
      const r = registerParticipanteSchema.safeParse({
        nome: 'User123',
        email: 'a@b.com',
        senha: 'senha123',
      })
      expect(r.success).toBe(false)
    })
  })

  describe('registerPromotorSchema', () => {
    it('aceita CPF e telefone com mascara', () => {
      const r = registerPromotorSchema.safeParse({
        nome: 'Carlos',
        email: 'c@x.com',
        senha: 'senha123',
        cpf: '123.456.789-00',
        telefone: '(11) 91234-5678',
      })
      expect(r.success).toBe(true)
    })

    it('rejeita CPF sem mascara', () => {
      const r = registerPromotorSchema.safeParse({
        nome: 'Carlos',
        email: 'c@x.com',
        senha: 'senha123',
        cpf: '12345678900',
        telefone: '(11) 91234-5678',
      })
      expect(r.success).toBe(false)
    })

    it('rejeita telefone fixo errado', () => {
      const r = registerPromotorSchema.safeParse({
        nome: 'Carlos',
        email: 'c@x.com',
        senha: 'senha123',
        cpf: '123.456.789-00',
        telefone: '11912345678',
      })
      expect(r.success).toBe(false)
    })

    it('aceita telefone fixo (8 digitos apos DDD)', () => {
      const r = registerPromotorSchema.safeParse({
        nome: 'Carlos',
        email: 'c@x.com',
        senha: 'senha123',
        cpf: '123.456.789-00',
        telefone: '(11) 3456-7890',
      })
      expect(r.success).toBe(true)
    })
  })

  describe('forgotSchema', () => {
    it('exige email valido', () => {
      expect(forgotSchema.safeParse({ email: 'a@b.com' }).success).toBe(true)
      expect(forgotSchema.safeParse({ email: 'invalido' }).success).toBe(false)
    })
  })

  describe('resetSchema', () => {
    it('rejeita quando senhas nao batem', () => {
      const r = resetSchema.safeParse({ novaSenha: 'senha123', confirmar: 'outraSenha' })
      expect(r.success).toBe(false)
    })

    it('aceita quando senhas batem', () => {
      const r = resetSchema.safeParse({ novaSenha: 'senha123', confirmar: 'senha123' })
      expect(r.success).toBe(true)
    })
  })
})
