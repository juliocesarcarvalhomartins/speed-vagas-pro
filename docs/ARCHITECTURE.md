# Arquitetura

## Backend
- `SpeedVagasServer`: HTTP server e rotas REST
- `Services`: regras de negócio / casos de uso
- `Database`: JDBC, schema e persistência
- `JobRules`: filtro, classificação e scoring
- `PublicJobSources`: fontes públicas de vagas
- `GoogleOAuthService`: OAuth 2.0
- `GmailApiService`: envio/leitura pela Gmail API
- `AutomationService`: orquestração da automação
- `ActivityService`: auditoria e notificações
- `EmailAgent`: classificação de mensagens
- `Json`: serialização/parsing JSON

## Frontend
- `app.js`: aplicação principal
- `search_intelligence.js`: orquestrador de busca / scoring dos 5 portais
- `style.css`: design
- `index.html`: layout

## Banco
Schemas H2 e MySQL ficam em `database/`.
