# SPEED VAGAS PRO 6.4 — Código-fonte real

## Novidades 6.4

A versão 6.4 implementa o roteiro de robustez: Google Intelligence no ciclo automático com cota diária, cache multi-fonte, diagnóstico de descartes, score de compatibilidade aprimorado, autoenvio seguro e opcional, aprovação em lote, dashboard/tema/responsividade, Gmail API também para leitura e testes adicionais de automação/schema. Consulte `MELHORIAS_6.4_IMPLEMENTADAS.md`.


Este pacote contém **arquivos `.java` originais do projeto**, recuperados das versões anteriores do SPEED VAGAS que ainda preservavam o source code.  
Não é saída de `javap` e não é bytecode decompilado.

## Estrutura

```text
backend/
  src/main/java/        Backend Java real
  src/test/java/        Testes Java reais
  build.bat
  test.bat

frontend/
  index.html
  style.css
  app.js
  search_intelligence.js

database/
  schema_h2.sql
  schema_mysql.sql

scripts/
  scripts de inicialização e integração

docs/
```

## Backend Java

Arquivos principais:

- `ActivityService.java`
- `AutomationService.java`
- `Database.java`
- `EmailAgent.java`
- `GmailApiService.java`
- `GoogleOAuthService.java`
- `JobRules.java`
- `Json.java`
- `PublicJobSources.java`
- `Services.java`
- `SpeedVagasServer.java`

Testes:

- `LogicSelfTest.java`
- `ProviderParsingTest.java`
- `SelfTest.java`

## Como compilar

Requer **JDK 21**. O projeto agora é um build Maven padrão (`backend/pom.xml`).

```bash
cd backend
mvn package
```

O JAR (com todas as dependências embutidas, incluindo o driver H2) será criado em:

```text
backend/target/speed-vagas.jar
```

Para rodar:

```bash
cd backend
java -jar target/speed-vagas.jar
```

> Os scripts antigos `build.bat`/`test.bat` (baseados em `javac` direto, sem Maven)
> continuam no repositório por compatibilidade, mas o fluxo recomendado passa a ser o Maven.

## Como testar

```bash
cd backend
mvn test
```

> Os testes atuais (`SelfTest`, `LogicSelfTest`, `ProviderParsingTest`) ainda são
> classes com `main()` e asserts manuais — não JUnit de verdade. Migrá-los para
> JUnit 5 (já disponível como dependência no `pom.xml`) é o próximo passo natural.

## Importante sobre a versão 6.3

O backend Java preservado veio da linha 5.6.x, que é a última linha em que os `.java` originais foram mantidos dentro do pacote.  
As evoluções 6.x de interface, inicialização e Gestor Inteligente foram incorporadas no frontend/scripts deste pacote.

Assim, este ZIP é um **source package real e editável**, mas não estou afirmando que cada byte do JAR 6.3 pode ser reproduzido bit a bit a partir dele. O objetivo aqui é devolver o código real do projeto de forma limpa, versionável e adequada para GitHub.

## Segurança

Não inclua no Git:
- OAuth client secrets
- tokens Google/Gmail
- API keys
- currículos pessoais
- banco local com dados reais
