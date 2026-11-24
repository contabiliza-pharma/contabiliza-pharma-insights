# Deploy no Railway

O Railway **não** usa os `docker-compose.yml` (eles são só para desenvolvimento). Cada repositório vira um
serviço no mesmo projeto do Railway, construído pelo próprio `Dockerfile`, e os serviços se falam pela rede
privada `<serviço>.railway.internal`. As variáveis ficam no painel de cada serviço, não em arquivos `.env`.

## 1. Serviços do projeto

| Serviço no Railway | Origem | Domínio público? |
|---|---|---|
| `Postgres` | banco do Railway (+ New → Database → PostgreSQL) | não |
| `Redis` | banco do Railway (+ New → Database → Redis) | não |
| `api` | repositório `contabiliza-insights` | opcional (só se o app mobile for chamar direto) |
| `processamento` | repositório `contabiliza-insights-processamento` | **não** |
| `web` | repositório `contabiliza-insights-front` | **sim** (é a entrada dos clientes) |

Use exatamente esses nomes: as variáveis abaixo referenciam `api`, `processamento`, `Postgres` e `Redis`.

## 2. Papéis do banco (uma vez, antes do 1º deploy da API)

```bash
psql "<DATABASE_PUBLIC_URL do Postgres>" -v migrator_senha='<senha1>' -v app_senha='<senha2>' \
     -f infra/postgres/papeis-railway.sql
```

## 3. Variáveis compartilhadas (Project Settings → Shared Variables)

| Variável | Valor |
|---|---|
| `DB_MIGRATOR_PASSWORD` | `<senha1>` (texto puro) |
| `DB_APP_PASSWORD` | `<senha2>` (texto puro) |
| `PROCESSAMENTO_TOKEN` | `openssl rand -hex 32` (texto puro) |

## 4. Variáveis de cada serviço

**api**
```
PORT=8080
APP_ENV=prod
DB_HOST=${{Postgres.PGHOST}}
DB_PORT=${{Postgres.PGPORT}}
DB_NAME=${{Postgres.PGDATABASE}}
DB_MIGRATOR_USER=app_migrator
DB_MIGRATOR_PASSWORD=${{shared.DB_MIGRATOR_PASSWORD}}
DB_APP_USER=app_rw
DB_APP_PASSWORD=${{shared.DB_APP_PASSWORD}}
REDIS_HOST=${{Redis.REDISHOST}}
REDIS_PORT=${{Redis.REDISPORT}}
REDIS_PASSWORD=${{Redis.REDISPASSWORD}}
PROCESSAMENTO_URL=http://${{processamento.RAILWAY_PRIVATE_DOMAIN}}:8000
PROCESSAMENTO_TOKEN=${{shared.PROCESSAMENTO_TOKEN}}
APP_URL_PUBLICA=https://${{web.RAILWAY_PUBLIC_DOMAIN}}
APP_ORIGENS_PERMITIDAS=https://${{web.RAILWAY_PUBLIC_DOMAIN}}
SESSION_COOKIE_NAME=__Host-session
SESSION_COOKIE_SECURE=true
ADMIN_NOME=...
ADMIN_EMAIL=...
ADMIN_SENHA_HASH=$argon2id$...     ← HASH (no painel do Railway sem aspas)
EMAIL_MODO=desligado
LOG_FORMAT=ecs
```
Healthcheck (Settings → Deploy): `/api/v1/auth/csrf`.

**processamento**
```
PORT=8000
APP_ENV=prod
DB_HOST=${{Postgres.PGHOST}}
DB_PORT=${{Postgres.PGPORT}}
DB_NAME=${{Postgres.PGDATABASE}}
DB_APP_USER=app_rw
DB_APP_PASSWORD=${{shared.DB_APP_PASSWORD}}
PROCESSAMENTO_TOKEN=${{shared.PROCESSAMENTO_TOKEN}}
FOLHA_TIPOS_PROCESSO=
FOLHA_TIPOS_FORA_DO_CUSTO=
```
Healthcheck: `/health`.

**web**
```
API_UPSTREAM=http://${{api.RAILWAY_PRIVATE_DOMAIN}}:8080
```
(`PORT` é injetada pelo Railway e o Nginx já escuta nela.) Gere o domínio público em Settings → Networking.

## 5. Ordem do primeiro deploy
Postgres e Redis → script dos papéis → `processamento` → `api` (roda as migrações e cria o administrador) → `web`.

## Observações
- Confira no painel os nomes exatos das variáveis do Postgres e do Redis (o autocompletar do Railway mostra todas).
- API e processamento escutam em `::` (IPv4 e IPv6), como a rede privada do Railway pede; o Nginx também.
- A porta de gestão da API (8081: `/actuator/health`, `/actuator/prometheus`) não recebe domínio público.
