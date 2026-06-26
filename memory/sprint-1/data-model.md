# Sprint 1 — Modelo de Dados (fatia US-050 + US-051)

> Autor: Arquiteto. Banco: `user_db` (PostgreSQL 16). Schema versionado por **Flyway**; Hibernate em `ddl-auto: validate`.
> Migrations existentes: `V1__init.sql`, `V2__papel_e_password_reset.sql`. **Próxima desta fatia: `V3`.**
> Coordenação: a fatia US-052/053 usa **`V4`** (evita colisão de numeração — ver `architecture.md` §8).

---

## 1. Escopo do delta

Nesta fatia **não há tabela nova**. Apenas:
1. `perfis_verificados` ganha a coluna `motivo_rejeicao`.
2. Índice de suporte à listagem admin por status (`idx_perfis_status`).
3. Seed idempotente do usuário ADMIN.

Sem mudança em `usuarios` (a coluna `papel` já existe desde V2; `ativo` é US-053/V4). Sem mudança em `password_reset_tokens`.

---

## 2. Migration `V3__motivo_rejeicao_e_seed_admin.sql`

```sql
-- ============================================================
-- V3 (fatia US-050/US-051): motivo de rejeicao no perfil do
-- promotor + seed idempotente do administrador.
-- Nao inclui 'ativo' (US-053/V4) nem campos ricos de perfil (US-052/V4).
-- ============================================================

-- US-050: motivo da rejeicao (preenchido apenas quando status = REJEITADO)
ALTER TABLE perfis_verificados
  ADD COLUMN motivo_rejeicao VARCHAR(300);

-- Suporte a filtro/listagem por status na tela admin (sem N+1: usado no JOIN)
CREATE INDEX idx_perfis_status ON perfis_verificados(status);

-- ============================================================
-- US-050: Seed do ADMIN (idempotente).
-- ADMIN nao nasce por cadastro publico (AuthService barra ADMIN no register).
-- senha dev: Admin@123  (hash BCrypt strength 10 — DEV-ONLY, ver ADR-T05).
-- O Backend gera o hash localmente e substitui o placeholder abaixo.
-- NUNCA commitar a senha em claro; o placeholder e' substituido pelo hash.
-- ============================================================
INSERT INTO usuarios (nome, email, senha_hash, papel, verificado, criado_em)
VALUES ('Administrador', 'admin@pegaticket.local',
        '$2a$10$<HASH_GERADO_PELO_BACKEND>', 'ADMIN', TRUE, NOW())
ON CONFLICT (email) DO NOTHING;
```

### Notas de migration
- **Aditiva e segura:** `ADD COLUMN` nullable não toca linhas existentes; `CREATE INDEX` é online o bastante para o volume acadêmico; `INSERT ... ON CONFLICT DO NOTHING` é idempotente (re-rodar não duplica admin).
- **Reversão mental:** `ALTER TABLE perfis_verificados DROP COLUMN motivo_rejeicao; DROP INDEX idx_perfis_status; DELETE FROM usuarios WHERE email='admin@pegaticket.local';`. Não há perda de dados de domínio (a coluna é nova).
- **`usuarios` não recebe `ativo`** nesta migration — propositalmente. O seed do admin omite `ativo` (a coluna não existe ainda); quando V4 adicionar `ativo NOT NULL DEFAULT TRUE`, o admin herda `TRUE`. Coordenação garantida pela ordem V3→V4.
- **Placeholder do hash:** o Backend deve rodar `BCryptPasswordEncoder().encode("Admin@123")` e colar o resultado no lugar de `$2a$10$<HASH_GERADO_PELO_BACKEND>` **antes** de commitar a V3. O literal com `<...>` é um BCrypt inválido — se esquecido, o admin não consegue logar (falha detectável no teste de smoke de login admin).

---

## 3. Estado do schema `user_db` após V3 (tabelas tocadas)

### `usuarios` (inalterada nesta fatia)
| Coluna | Tipo | Constraint |
|---|---|---|
| id | BIGSERIAL | PK |
| nome | VARCHAR(120) | NOT NULL |
| email | VARCHAR(160) | NOT NULL UNIQUE |
| senha_hash | VARCHAR(120) | NOT NULL |
| papel | VARCHAR(20) | NOT NULL DEFAULT 'PARTICIPANTE', CHECK ∈ {PARTICIPANTE,PROMOTOR,ADMIN} |
| verificado | BOOLEAN | NOT NULL DEFAULT FALSE |
| criado_em | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

Índices: `idx_usuarios_email`, `idx_usuarios_papel` (ambos de V1/V2). **+1 linha:** seed admin.

### `perfis_verificados` (delta: +`motivo_rejeicao`)
| Coluna | Tipo | Constraint |
|---|---|---|
| id | BIGSERIAL | PK |
| usuario_id | BIGINT | NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE |
| telefone | VARCHAR(20) | NOT NULL |
| cpf | VARCHAR(14) | NOT NULL UNIQUE |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDENTE' (espelha enum `StatusVerificacao`) |
| **motivo_rejeicao** | **VARCHAR(300)** | **NULLABLE (novo)** |
| criado_em | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

Índices: `usuario_id` (UNIQUE ⇒ indexado), **`idx_perfis_status` (novo)**.

> `usuario_id` é `BIGINT` referenciando `usuarios(id)` **no mesmo banco** (`user_db`) — FK permitida (não é cross-service). Mantida.

---

## 4. Entidades JPA (delta)

### `Usuario` (domain) — máquina de estados ADR-P07
Métodos esperados (sem expor setters públicos arbitrários):
```java
// FÁBRICA — ALTERADA: passa a criar PARTICIPANTE (não PROMOTOR)
public static Usuario novoPromotorPendente(String nome, String email, String senhaHash) {
    // papel = PARTICIPANTE, verificado = false
}

// NOVO — promoção na aprovação
public void aprovarComoPromotor() {
    // papel = PROMOTOR; verificado = true
}

// existente, mantido (reuso interno de aprovarComoPromotor)
public void marcarComoVerificado();
```
- `novoParticipante` inalterado (PARTICIPANTE, verificado=true).
- Ajustar Javadoc de `Papel.PROMOTOR` (hoje diz "começa verificado=false até aprovação" → agura: "papel concedido apenas na aprovação do admin; antes disso o candidato é PARTICIPANTE").

### `PerfilVerificado` (domain) — +motivoRejeicao
```java
@Column(name = "motivo_rejeicao", length = 300)
private String motivoRejeicao;            // nullable

public String getMotivoRejeicao() { return motivoRejeicao; }

// ALTERADO: aprovar limpa o motivo (reaprovação pós-rejeição)
public void aprovar() {
    this.status = StatusVerificacao.VERIFICADO;
    this.motivoRejeicao = null;
}

// ALTERADO: rejeitar passa a exigir motivo
public void rejeitar(String motivo) {
    this.status = StatusVerificacao.REJEITADO;
    this.motivoRejeicao = motivo;
}
```
- `aprovar()`/`rejeitar(...)` deixam de ser código morto (passam a ser chamados pelo `AdminService`).

---

## 5. Queries novas e índices

### Listagem admin (GET /users) — sem N+1
JQPL com `LEFT JOIN` + projeção por constructor expression numa única query:
```java
// Esboço (assinatura esperada no UsuarioRepository ou repo dedicado)
@Query("""
    SELECT new com.ticketeira.user.dto.UsuarioListItem(
        u.id, u.nome, u.email, u.papel, u.verificado, p.status, u.criadoEm)
    FROM Usuario u
    LEFT JOIN PerfilVerificado p ON p.usuarioId = u.id
    WHERE (:papel  IS NULL OR u.papel = :papel)
      AND (:status IS NULL OR p.status = :status)
      AND (:q      IS NULL OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
Page<UsuarioListItem> buscarParaAdmin(@Param("papel") Papel papel,
                                      @Param("status") StatusVerificacao status,
                                      @Param("q") String q,
                                      Pageable pageable);
```
> Nota de mapeamento: `PerfilVerificado` referencia `usuarioId` como `Long` (não associação `@ManyToOne`), então o JOIN é por condição explícita `ON p.usuarioId = u.id` (JPQL theta/ON-join), não por navegação. Alternativa equivalente: native query com `LEFT JOIN perfis_verificados`. O importante: **uma query, sem lazy por linha**. O `count` para a paginação roda sobre `usuarios` com os mesmos predicados de `usuarios`/`perfis` (deixar Spring Data derivar o count da `@Query` com `countQuery` se o JOIN atrapalhar a contagem).

- **Índices que sustentam a query:** `idx_usuarios_papel` (filtro papel), `idx_perfis_status` (filtro status, novo em V3), `usuario_id UNIQUE` (join). A busca `q` (ILIKE `%...%`) é **scan** — aceitável no volume acadêmico (sem índice trigram nesta fatia; registrar como possível dívida futura se a base crescer).

### Detalhe admin (GET /users/{id})
- `usuarios.findById(id)` + `perfis.findByUsuarioId(id)` (já existe). Duas queries por id único — sem N+1 (não é loop). Aceitável.

---

## 6. Fora desta migration (deferido — seam documentado)

| Item | História | Onde entra |
|---|---|---|
| `usuarios.ativo BOOLEAN` + `idx_usuarios_ativo` + bloqueio no login | US-053 | V4 |
| `perfis_verificados`: `email_contato, cep, logradouro, numero, complemento, bairro, cidade, uf, instagram, website` + `chk_perfis_uf` | US-052 | V4 |
| Templates de e-mail + `processed_events` (se aplicável) | US-054 | fatia US-054 |

O `PerfilResumoResponse` (contrato) expõe **só** os campos existentes hoje (cpf, telefone, status, motivoRejeicao, criadoEm). Quando US-052 adicionar colunas, o DTO é estendido — o drawer do front mostra só o que existe agora.
