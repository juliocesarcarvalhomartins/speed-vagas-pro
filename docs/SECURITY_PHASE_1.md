# SPEED VAGAS PRO 6.3 — Segurança / Etapa 1

Implementação da seção 1 do `PROMPT_MODERNIZACAO_SPEED_VAGAS_PRO_v2_1.md`, seguindo a ordem de ataque definida no próprio prompt.

## Implementado

- Aprovação humana obrigatória antes de qualquer envio criado pelo agente.
  - Automação cria `DRAFT_PENDING_APPROVAL`.
  - UI de Candidaturas mostra **Aprovar e enviar**.
  - Endpoint `POST /api/applications/approve` é o único caminho de aprovação do rascunho.
  - Ciclos automáticos não usam mais `sendEmails=true`.
- Google Programmable Search removido do navegador.
  - API key e `cx` não ficam em `localStorage`.
  - Novo proxy `POST /api/search/google` no backend.
  - Credenciais lidas de `SPEED_GOOGLE_SEARCH_KEY` e `SPEED_GOOGLE_SEARCH_CX`.
- PII removida do seed de `Database.java`.
  - Primeiro uso cria perfil vazio (ou usa variáveis `SPEED_PROFILE_*`).
  - Não há mais e-mail/telefone/endereço pessoais fixos no seed.
- Segredos fora do Git.
  - `.env`, `credentials.json`, tokens e dados locais adicionados ao `.gitignore`.
  - `.env.example` contém somente nomes de variáveis, sem segredos.
- Rate limiting básico em Google Search e Gmail API.

## Validações executadas nesta entrega

- `javac` Java 21 em todos os arquivos de `src/main/java`: OK.
- `node --check frontend/app.js`: OK.
- `frontend/` sincronizado com `backend/web/`: OK.
- Busca por `GS_KEY`, `GS_CX`, `googleSearchKey`, `googleSearchCx` no frontend: nenhuma ocorrência.
- Busca por `sendEmails=true`, `CURRICULO_ENVIADO`, `DISPARO_EMAIL` no código ativo: nenhuma ocorrência.
- Busca por PII pessoal no seed de `Database.java`: nenhuma ocorrência.

> O ambiente de edição não possui Maven instalado, então `mvn test/package` não pôde ser executado aqui. O código-fonte principal foi compilado diretamente com Java 21 para validar sintaxe e tipos.
