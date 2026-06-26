# Retrospectiva: Sprint 1

## O que deu certo
- A divisão entre serviços de backend e gateway com Spring Cloud funcionou bem.
- Decisão rápida de adotar uma stack padrão e de centralizar o JWT.
- A interação em "pair programming" permitiu encontrar gaps de design no início da implementação.

## Desafios Enfrentados
- **Docker Daemon no Windows:** Tivemos instabilidade e falhas de timeout com os containers devido ao carregamento inicial no WSL2, o que fez o Healthcheck estourar o limite de tempo do Docker Compose. A infra no entanto estabilizou após rodar de novo.
- **Diferenças H2 vs PostgreSQL:** O schema DDL (`uf CHAR(2)`) era estrito e divergiu do mapeamento `@Entity` (`String`) gerando conflito no start do Hibernate via Spring Data. Foi corrigido no fix da Sprint, mas mostrou a necessidade de testar cenários reais.
- **Spoofing e Autorização:** A dependência em headers repassados (`X-User-Papel`) sem limpeza explícita no API Gateway era uma brecha de segurança crítica P0.

## Ações para as próximas Sprints
1. Validar sempre se a tipagem do Flyway casa perfeitamente com a tipagem `@Column` do JPA.
2. Considerar `testcontainers` ao invés de H2 nos testes integrados para garantir 100% de paridade (PostgreSQL) — será avaliado na próxima sprint.
3. Garantir que todo DTO (`Request` objects) aplique validação estrita, e evitar reuso de Request DTOs para propósitos diferentes (ex: o `SolicitarPromotorRequest` extraído foi uma boa solução).
