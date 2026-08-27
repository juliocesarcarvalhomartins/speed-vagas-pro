# Compatibilidade H2 / MySQL

O projeto mantém `database/schema_h2.sql` e `database/schema_mysql.sql`, espelhados também em `backend/sql/`.

O teste `SchemaCompatibilityTest` verifica a presença das tabelas e colunas críticas usadas pelo backend, incluindo `google_search_quota`, `job_decisions` e `job_feedback`.

Para homologação MySQL, criar um banco vazio, aplicar `database/schema_mysql.sql`, iniciar o backend apontando para ele e executar os fluxos: perfil, busca, gravação de vaga, rascunho, aprovação, eventos e diagnóstico de descarte.
