# ADR 0005 — Monorepo com Maven multi-module

**Status:** Aceito · 2026-05-12

## Contexto

Cinco entregáveis backend (Gateway + 4 microsserviços) + uma lib compartilhada + frontend + infra. Time pequeno (projeto acadêmico) com ~3 devs. Decisão estrutural: monorepo único ou um repositório por entregável.

## Decisão

**Monorepo único** com layout:

```
ticketeira/
├── pom.xml                  # parent Maven multi-module
├── shared/common-lib/       # módulo Maven
├── services/                # 5 módulos Maven (gateway + 4 microsserviços)
├── frontend/                # projeto Vite independente
├── infra/                   # docker-compose, init scripts
└── docs/                    # ADRs, OpenAPI, diagramas
```

Backend gerenciado por **Maven 3.9 + multi-module**, com:

- Parent POM em `pom.xml` declara BOM (Spring Boot 3.3.x), Java 21 e `pluginManagement`.
- `shared/common-lib` é dependência declarada nos services via `dependencyManagement`.
- Cada `services/*/Dockerfile` usa o contexto da raiz e copia apenas o necessário (parent + common-lib + o serviço alvo) — build reproduzível.

CI no GitHub Actions: workflows separados por path (`.github/workflows/backend.yml` e `frontend.yml`), disparados só quando os arquivos relevantes mudam.

## Consequências

✅ Refactor cross-module (ex.: renomear DTO em `common-lib`) é uma única PR atômica.
✅ Build local com um comando: `mvn verify` valida tudo.
✅ Versionamento unificado: branch `main` é sempre uma snapshot consistente do sistema todo.
✅ Onboarding mais simples: clonar 1 repo basta.
✅ Diff de PR mostra implicações cruzadas (ex.: alterar API do Gateway + adaptar frontend na mesma PR).

❌ Repo cresce com o tempo — clone fica maior.
❌ CI builda mais do que poderia se cada serviço fosse isolado. Mitigado por `paths:` filter nos workflows + cache do `.m2`.
❌ Permissões granulares de acesso ficam impossíveis (todo dev vê todo código). Não é problema acadêmico.

Em produção, **cada serviço continua deployável independente** (Dockerfile próprio + `mvn -pl services/X -am package`). O monorepo é só uma decisão de versionamento e CI, não de deploy.
