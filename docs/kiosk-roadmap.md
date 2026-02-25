# Roadmap de evolução do kiosk

## Fase 1 (MVP técnico)
- [x] APK com WebView full screen.
- [x] Modo imersivo para reduzir fuga de tela.
- [x] Estrutura pronta para lock task.
- [x] Tela offline amigável (sem internet).
- [ ] Endpoint de heartbeat para monitorar status do tablet.

## Fase 2 (controle corporativo)
- [ ] Provisionar tablet como **Device Owner**.
- [ ] Implementar `DevicePolicyManager` para bloquear configurações.
- [ ] Definir app como launcher padrão (home custom).
- [ ] Whitelist de apps permitidos (somente kiosk + utilitários necessários).

## Fase 3 (operações e segurança)
- [ ] Atualização remota de URL/config via API segura.
- [ ] Renovação automática de sessão/tokens.
- [ ] Telemetria: online/offline, bateria, versão do app, última sincronização.
- [ ] Gestão remota de conteúdo em caso de queda do backend.

## Fase 4 (hardening)
- [ ] TLS pinning e política de certificados.
- [ ] Bloqueio de captura de tela quando necessário.
- [ ] Proteção anti-tamper e assinatura de release.
- [ ] Pipeline de CI/CD para APK/AAB com versionamento.

## Checklist de piloto
- [ ] 2 modelos de tablet homologados.
- [ ] Teste com reboot automático e retorno ao kiosk.
- [ ] Teste de perda de rede e recuperação.
- [ ] Teste de tentativa de saída (home, recentes, notificações, USB).
- [ ] Documentação de suporte de campo (troca de aparelho e reprovisionamento).
