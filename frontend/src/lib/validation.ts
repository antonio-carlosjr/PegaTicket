import { z } from 'zod'

export const emailSchema = z
  .string()
  .min(1, { message: 'E-mail e obrigatorio' })
  .email({ message: 'E-mail invalido' })
  .max(160)

export const senhaSchema = z
  .string()
  .min(6, { message: 'A senha precisa de pelo menos 6 caracteres' })
  .max(72, { message: 'Senha muito longa' })

export const nomeSchema = z
  .string()
  .min(2, { message: 'Nome precisa de pelo menos 2 caracteres' })
  .max(120)
  .regex(/^[\p{L}][\p{L} '-]+$/u, { message: 'Use apenas letras, espacos e hifens' })

// 000.000.000-00
const CPF_REGEX = /^\d{3}\.\d{3}\.\d{3}-\d{2}$/
// (00) 00000-0000 ou (00) 0000-0000
const TELEFONE_REGEX = /^\(\d{2}\) \d{4,5}-\d{4}$/

export const cpfSchema = z.string().regex(CPF_REGEX, { message: 'CPF deve estar no formato 000.000.000-00' })
export const telefoneSchema = z.string().regex(TELEFONE_REGEX, { message: 'Telefone deve estar no formato (00) 00000-0000' })

export const loginSchema = z.object({
  email: emailSchema,
  senha: z.string().min(1, { message: 'Senha e obrigatoria' }),
})
export type LoginFormValues = z.infer<typeof loginSchema>

export const registerParticipanteSchema = z.object({
  nome: nomeSchema,
  email: emailSchema,
  senha: senhaSchema,
})
export type RegisterParticipanteValues = z.infer<typeof registerParticipanteSchema>

export const registerPromotorSchema = z.object({
  nome: nomeSchema,
  email: emailSchema,
  senha: senhaSchema,
  cpf: cpfSchema,
  telefone: telefoneSchema,
  emailContato: z.string().email('E-mail invalido').or(z.literal('')).optional(),
  cep: z.string().regex(/^\d{5}-\d{3}$/, 'CEP invalido').or(z.literal('')).optional(),
  logradouro: z.string().max(160).optional(),
  numero: z.string().max(20).optional(),
  complemento: z.string().max(80).optional(),
  bairro: z.string().max(80).optional(),
  cidade: z.string().max(80).optional(),
  uf: z.string().length(2, 'UF deve ter 2 letras').or(z.literal('')).optional(),
  instagram: z.string().max(80).optional(),
  website: z.string().max(200).optional(),
})
export type RegisterPromotorValues = z.infer<typeof registerPromotorSchema>

export const forgotSchema = z.object({ email: emailSchema })
export type ForgotFormValues = z.infer<typeof forgotSchema>

export const resetSchema = z
  .object({
    novaSenha: senhaSchema,
    confirmar: senhaSchema,
  })
  .refine((data) => data.novaSenha === data.confirmar, {
    path: ['confirmar'],
    message: 'As senhas nao coincidem',
  })
export type ResetFormValues = z.infer<typeof resetSchema>
