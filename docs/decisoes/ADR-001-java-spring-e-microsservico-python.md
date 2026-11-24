# ADR-001 — API em Java/Spring Boot e microsserviço de processamento em Python

**Data:** 23/11/2026 · **Autor:** Abraão Filipi (Inovação) · **Status:** aceita

## Contexto
O Guia de Desenvolvimento (§1) e o Escopo (§9) previam uma API única em Python + Flask.
Para o MVP, a equipe decidiu separar responsabilidades por linguagem.

## Decisão
- **API (Java 21 + Spring Boot 3.5):** login, sessão, troca de senha, administração de empresas e
  contas, autorização por perfil e por empresa, auditoria, orquestração das importações e dos downloads.
  É o único ponto de entrada da web e do futuro app.
- **Microsserviço de processamento (Python 3.12 + FastAPI):** leitura do SPED Fiscal (EFD ICMS/IPI),
  do PGDAS-D e das planilhas do Domínio Folha; gravação dos dados normalizados; resumos e relatórios
  .xlsx. Só é acessível na rede interna, com token compartilhado (`PROCESSAMENTO_TOKEN`).
- **Migrações:** Flyway, na API Java (dona do esquema). O Python só faz DML com o papel `app_rw`.
- **Senhas:** Argon2id com os mesmos parâmetros nos dois lados (m=65536, t=3, p=4, hash 32, sal 16),
  no formato PHC — o hash gerado pelo comando do README é lido pelo Spring sem conversão.
- **Web:** React + TypeScript (Vite), mobile first, servida pelo Nginx no mesmo domínio da API.

## Consequências
- O isolamento por empresa (RNF02) é decidido na API Java; o Python recebe o `empresa_id` já autorizado.
- Duas stacks para manter (Maven/JUnit e pip/pytest); o CI precisa das duas.
- Itens do Guia que mudam de ferramenta: Flask-Limiter → limitador em Redis na API Java;
  structlog → logging estruturado ECS do Spring Boot e JSON no Python; Pydantic segue no Python,
  Bean Validation no Java; `@requer_perfil` → `@PreAuthorize` + regras no `SecurityConfig`.
- O Guia §1 e o Escopo §9 precisam ser atualizados para refletir esta decisão.
