# Checklist de segurança — SPEED VAGAS PRO 6.4

- [ ] Nenhuma chave `SPEED_GOOGLE_SEARCH_KEY`, `SPEED_GOOGLE_SEARCH_CX` ou Adzuna aparece no frontend.
- [ ] `credentials.json` e tokens OAuth permanecem fora do Git.
- [ ] Logs não imprimem `client_secret`, access token, refresh token ou chaves de busca.
- [ ] Login, envio e leitura do Gmail usam OAuth/Gmail API; não há senha de app/IMAP no fluxo ativo.
- [ ] Título, empresa, cidade, descrição e requisitos de fontes externas são sanitizados antes de persistir/exibir.
- [ ] Autoenvio vem desabilitado por padrão e exige Gmail conectado, currículo, score alto e cota diária disponível.
- [ ] Revisar `.gitignore` antes de cada release.
