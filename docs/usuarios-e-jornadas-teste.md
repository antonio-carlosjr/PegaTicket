# Usuários de Teste · Jornadas · E-mails — PegaTicket

> Para a equipe testar o sistema em **produção**. Atualizado em 26/06/2026.

## 0. Acesso

- **App:** https://frontend-theta-sooty-61.vercel.app
- ⚠️ **Cold-start:** os serviços dormem quando ociosos; a **1ª ação após inatividade pode demorar ~8 s** (a navegação se recupera sozinha; se algo falhar, é só repetir/recarregar).

---

## 1. Relação de usuários (prontos para uso)

| Papel | E-mail | Senha | Estado | Serve para testar |
|---|---|---|---|---|
| **Admin** | `admin@pegaticket.local` | `Admin@123` | seed | Controle de usuários, aprovar/rejeitar promotores, ativar/inativar |
| **Participante** | `participante.demo@teste.com` | `Teste@123` | verificado | Explorar eventos, inscrição, ingresso QR |
| **Promotor (aprovado)** | `promotor.demo@teste.com` | `Teste@123` | verificado | Criar/editar/publicar/cancelar eventos |
| **Promotor (pendente)** | `promotor.pendente@teste.com` | `Teste@123` | aguardando aprovação | Ver bloqueio do pendente + fluxo de aprovação pelo admin |

> 📧 **Para receber e-mails de verdade** (boas-vindas, aprovação, reset), **registre uma conta nova com um e-mail real seu** — as contas `@teste.com` acima servem pra testar as telas/fluxos, não pra receber e-mail.

---

## 2. Jornadas e fluxos por papel

### 🔑 Autenticação & Conta (todos) — Sprint 1
1. **Cadastro de participante** — "Crie agora" → aba *Participante* → cria conta e **já entra** (auto-login).
2. **Cadastro de promotor** — aba *Promotor* → perfil rico: CPF e telefone **com máscara**, e-mail de contato, **CEP que preenche e trava o endereço (ViaCEP)**, redes. → entra como participante **pendente** até o admin aprovar.
3. **Login / Logout.**
4. **Esqueci minha senha** — envia link de reset por e-mail → tela de redefinição.
5. **Meu perfil** (clique no avatar/nome no topo) — edita dados cadastrais (promotor: perfil rico com máscara/ViaCEP) e **troca a senha** ali direto (exige a senha atual).

### 🎫 Participante — Sprints 2 e 3
1. **Explorar eventos** — busca por título/local, filtro por **tipo** e **data**, paginação.
2. **Detalhe do evento** (data no fuso BRT, vagas, capacidade, preço).
3. **Inscrever-se** num evento **gratuito** → recebe **ingresso com QR** na hora.
4. **Meus ingressos** — cards com **QR code**.
5. **Minhas inscrições** — histórico paginado (com nome do evento).
6. **Inscrição duplicada** — tentar de novo no mesmo evento → bloqueado ("Você já está inscrito").
7. **Evento esgotado** — quando as vagas zeram, o botão fica indisponível.

### 📣 Promotor verificado — Sprint 2
1. **Criar evento** — wizard (nome/descrição → datas/local/capacidade → tipo gratuito/pago).
2. **Meus eventos** — listar.
3. **Publicar / Cancelar** (máquina de estados: RASCUNHO → PUBLICADO → CANCELADO).
4. **Editar** evento em rascunho.
5. **Ownership** — só vê/edita os próprios eventos.
6. *(também pode se inscrever em eventos como participante)*

### ⏳ Promotor pendente
1. Home mostra **banner "Cadastro em análise"** e **não** permite criar eventos.
2. Após o admin aprovar → vira **Promotor verificado** e ganha "Criar evento".

### 🛠️ Admin — Sprint 1
1. **Home do admin** — CTA "Gerenciar usuários" + cards: Total de usuários · **Promotores pendentes** (destacado) · Verificados · Status da plataforma (health).
2. **Gerenciar usuários** (`/admin/usuarios`) — tabela com papel, status (Ativo/Inativo) e **badge "Promotor pendente"** nos que aguardam aprovação.
3. **Detalhes** de um usuário → vê o perfil completo do promotor.
4. **Aprovar / Rejeitar** promotor pendente (rejeição exige **motivo**) → **dispara e-mail**.
5. **Ativar / Inativar** usuários (a conta seed do admin não pode ser inativada).

> **Roteiro sugerido para a banca/equipe:** cadastrar promotor (com e-mail real) → logar como admin e aprovar → logar como o promotor (já verificado) → criar e publicar um evento gratuito → logar como participante → inscrever-se → ver o ingresso QR em "Meus ingressos".

---

## 3. Disparos de e-mail (status: ✅ ATIVO em produção)

Infra: **Resend** via SMTP (`smtp.resend.com`, porta **2587**), remetente `no-reply@antoniocarlosdev.com.br`. Falha de envio é **logada e não quebra a ação** (o cadastro/aprovação acontece mesmo se o e-mail falhar).

| # | E-mail | Gatilho | Destinatário |
|---|---|---|---|
| 1 | **Boas-vindas** | ao registrar uma conta | novo usuário |
| 2 | **Promotor aprovado** | admin clica em Aprovar | o promotor |
| 3 | **Promotor rejeitado** (com o motivo) | admin clica em Rejeitar | o promotor |
| 4 | **Redefinição de senha** | "Esqueci minha senha" | o usuário |

> ⚠️ **Recebimento:** o Resend só entrega a **destinatários reais** se o domínio `antoniocarlosdev.com.br` estiver **verificado** na conta Resend. Para testar o recebimento de fato, use um **e-mail real** no cadastro e confirme a verificação do domínio no painel do Resend (senão, em modo sandbox, só chega ao dono da conta).

---

## 4. Observação sobre dados legados

A base de produção tem usuários de teste antigos com `papel`/`verificado` **fora de sincronia** com o status do perfil (ex.: aparecem como "Promotor pendente" mesmo já tendo o papel). **Aprová-los pelo modal do admin sincroniza tudo** (papel → PROMOTOR, verificado → true, perfil → VERIFICADO). Os 4 usuários da tabela acima estão consistentes.
