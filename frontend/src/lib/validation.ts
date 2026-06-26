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

// ─── Schemas de Evento ────────────────────────────────────────────────────────

/**
 * Schema Zod para criar/editar evento.
 * Espelha as @AssertTrue do EventoCreateRequest / EventoUpdateRequest do backend.
 *
 * Regras cross-field:
 *  - dataFim >= dataInicio
 *  - PAGO: preco > 0 e prazoReembolsoDias obrigatorio
 *  - GRATUITO: preco ausente/vazio
 */
export const eventoSchema = z
  .object({
    titulo: z
      .string()
      .min(1, 'Titulo e obrigatorio')
      .max(160, 'Titulo pode ter no maximo 160 caracteres'),

    descricao: z
      .string()
      .max(5000, 'Descricao pode ter no maximo 5000 caracteres')
      .optional()
      .or(z.literal('')),

    dataInicio: z.string().min(1, 'Data de inicio e obrigatoria'),

    dataFim: z.string().min(1, 'Data de fim e obrigatoria'),

    local: z
      .string()
      .min(1, 'Local e obrigatorio')
      .max(200, 'Local pode ter no maximo 200 caracteres'),

    tipo: z.enum(['GRATUITO', 'PAGO'], {
      required_error: 'Tipo e obrigatorio',
    }),

    capacidade: z.coerce
      .number({ invalid_type_error: 'Capacidade deve ser um numero' })
      .int('Capacidade deve ser um numero inteiro')
      .positive('Capacidade deve ser maior que zero'),

    preco: z
      .string()
      .optional()
      .or(z.literal('')),

    prazoReembolsoDias: z.coerce
      .number({ invalid_type_error: 'Prazo deve ser um numero' })
      .int()
      .nonnegative('Prazo deve ser zero ou positivo')
      .optional()
      .nullable(),

    imagemUrl: z
      .string()
      .max(300, 'URL pode ter no maximo 300 caracteres')
      .optional()
      .or(z.literal('')),
  })
  .superRefine((data, ctx) => {
    // Valida periodo
    if (data.dataInicio && data.dataFim) {
      const inicio = new Date(data.dataInicio)
      const fim = new Date(data.dataFim)
      if (!isNaN(inicio.getTime()) && !isNaN(fim.getTime()) && fim < inicio) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Data de fim deve ser igual ou posterior a data de inicio',
          path: ['dataFim'],
        })
      }
    }

    // Valida preco coerente
    if (data.tipo === 'PAGO') {
      const precoNum = parseFloat(data.preco ?? '')
      if (!data.preco || isNaN(precoNum) || precoNum <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Evento PAGO exige preco maior que zero',
          path: ['preco'],
        })
      }
      if (data.prazoReembolsoDias == null) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Prazo de reembolso e obrigatorio para eventos PAGO',
          path: ['prazoReembolsoDias'],
        })
      }
    }

    if (data.tipo === 'GRATUITO' && data.preco && data.preco !== '') {
      const precoNum = parseFloat(data.preco)
      if (!isNaN(precoNum) && precoNum > 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Evento GRATUITO nao deve ter preco',
          path: ['preco'],
        })
      }
    }
  })

export type EventoFormValues = z.infer<typeof eventoSchema>

/** Schema para filtros da listagem de eventos (participante). */
export const eventosFiltroPSchema = z.object({
  q: z.string().optional(),
  tipo: z.enum(['GRATUITO', 'PAGO']).optional().or(z.literal('')),
  de: z.string().optional(),
  ate: z.string().optional(),
})
export type EventosFiltroValues = z.infer<typeof eventosFiltroPSchema>
