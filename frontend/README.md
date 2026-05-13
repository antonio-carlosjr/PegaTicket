# Ticketeira Frontend

React 18 + Vite + TypeScript. Consome o API Gateway via REST.

## Comandos

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # tsc + vite build (saida em dist/)
npm run lint
```

## Variaveis de ambiente

| Var | Default | Descricao |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | URL do API Gateway |

Definir no `.env.local` (gitignored) ou na env do shell.

## Estrutura

```
src/
├── api/
│   ├── client.ts        # axios instance + token storage
│   └── auth.ts          # register, login, me, health
├── hooks/
│   └── useAuth.tsx      # contexto de auth + localStorage
├── pages/
│   ├── Home.tsx         # health check + perfil
│   ├── Login.tsx
│   └── Register.tsx
├── routes/
│   └── AppRoutes.tsx
├── main.tsx
└── index.css
```

## Rodando via docker-compose

```bash
docker compose --profile frontend up -d
```

Em conjunto com o backend:

```bash
docker compose --profile backend --profile frontend up -d --build
```
