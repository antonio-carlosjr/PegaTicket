# ADR 0003 — Autenticação JWT stateless

**Status:** Aceito · 2026-05-12

## Contexto

RNF10 exige autenticação básica; ações sensíveis devem ser restritas a usuários verificados. Com microsserviços, mantemos sessões compartilhadas (Redis) ou tokens self-contained.

## Decisão

Autenticação **stateless via JWT (HS256/HS384)**:

1. `user-service` emite o token em `POST /auth/login` após validar BCrypt da senha.
2. O JWT contém `sub` (userId), `email`, `verificado`, `iat`, `exp` (1 h).
3. O **API Gateway** valida o token em todo request fora da whitelist (`/api/auth/**`, `/actuator/health`).
4. Se válido, o Gateway injeta `X-User-Id`, `X-User-Email`, `X-User-Verified` na requisição downstream.
5. Os microsserviços **confiam nesses headers** (não revalidam o JWT) — `permitAll()` no Spring Security interno.

Biblioteca: `io.jsonwebtoken:jjwt 0.12.x`. Secret via variável de ambiente `JWT_SECRET` (≥ 32 bytes). Util compartilhado em `shared/common-lib/.../JwtUtil.java`.

## Consequências

✅ Sem session store — qualquer instância de serviço atende qualquer request, escalonamento horizontal trivial.
✅ Microsserviços ficam simples: leem header, não dependem de Spring Security completo.
✅ Token contém o flag `verificado` — não precisa consultar `user-service` para autorizar criação de evento.

❌ **Logout server-side é difícil** — token só "expira" quando seu `exp` chega. Mitigações futuras: blacklist em Redis ou refresh tokens curtos (não implementado em Sprint 0).
❌ Acesso direto a um microsserviço (bypassando o Gateway) é inseguro se a rede não estiver isolada. Em compose, a network `ticketeira-net` isola; em produção, exigir mTLS ou só expor o Gateway.
❌ Tamanho do token (~230 chars) maior que session cookie.

Em Sprint futura, adicionar **refresh tokens** (long-lived, revogáveis) + **access tokens** (curtos, ~5 min) reduziria o impacto do problema de logout.
