## O que muda?
Implementa as fundações de controle de acesso, perfis e autenticação para o PegaTicket, finalizando a Sprint 1.

## Histórias Atendidas
- **US-050**: Controle de Papéis Base (Admin, Participante, Promotor)
- **US-051**: JWT com Claims de Papel
- **US-052/053/054**: Fluxo de aprovação de promotores (Pendente -> Verificado/Rejeitado) e Envio de E-mails Assíncronos

## Riscos Mitigados
- **Header Spoofing**: O API Gateway limpa cabeçalhos de identidade `X-User-*` forjados por usuários mal-intencionados antes de delegar a requisição aos microsserviços.
- **DataIntegrityViolation (Concorrência)**: Colisões de banco em cadastro duplo são capturadas pelo GlobalExceptionHandler e retornam código `409 Conflict`.
- **N+1 e Teto de Tráfego**: A listagem do portal Admin utiliza paginação no Banco de Dados (Spring Data `Pageable`).

## Resultado do Code Review
- Todos os bloqueadores de ambiente resolvidos (Crash loops do Flyway vs DDL Validation).
- Suíte de Testes (Unit, Integration) rodando com sucesso.
- Passou na bateria de testes de regressão do `opus` e `sonnet`.

## Definition of Done
- [x] Código limpo, testado e em conformidade com as regras (Coding Standards).
- [x] Testes integrados cobrindo os caminhos críticos (Autorização, Transição de Status).
- [x] UI construída e respondendo à paginação.
- [x] Sem P0/P1 no Code Review.
