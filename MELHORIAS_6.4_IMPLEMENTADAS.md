# SPEED VAGAS PRO 6.4 — melhorias implementadas

## Backend
- Leitura e envio Gmail unificados em OAuth/Gmail API.
- Testes de decisão do `AutomationService`.
- Cota diária persistente para Google Custom Search, com status usado/restante.
- Diagnóstico estruturado de descarte por vaga.
- Erros de fontes públicas padronizados por código e mensagem.
- Verificação de paridade de schema H2/MySQL.

## Busca
- Google Intelligence participa da automação em segundo plano.
- Cache para Remotive, Adzuna, Google CSE e novas fontes.
- Score reforçado por título, combinação/frequência de skills e termos PT-BR.
- Fontes públicas sem chave ampliadas com Arbeitnow e RemoteOK.
- Filtro de elegibilidade concentrado na ingestão; a automação consome vagas já aprovadas.

## Candidatura
- Autoenvio opcional, desligado por padrão, score mínimo 85 e limite diário 3 por padrão.
- Autoenvio só fica apto com Gmail conectado e currículo configurado.
- Fila de aprovação em lote para score 90+.
- Feedback de match ruim para impedir nova ação automática naquela vaga.

## Interface
- Dashboard com vagas analisadas, compatíveis, enviadas hoje, taxa de resposta e entrevistas.
- Estados de carregamento/vazio.
- Diagnóstico de descarte com badges.
- Tema claro/escuro e responsividade.
- Exibição da cota diária Google.

## Segurança
- Segredos ficam no backend.
- Sanitização de campos externos.
- Checklist de release em `docs/RELEASE_SECURITY_CHECKLIST.md`.
