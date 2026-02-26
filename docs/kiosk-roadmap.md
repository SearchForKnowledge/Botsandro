# Roadmap de evolução do Botsandro Kiosk

## Fase 1 — MVP atual (concluído)
- [x] Launcher interno com grade de apps da lista branca.
- [x] Painel lateral de configurações protegido por PIN Admin.
- [x] PIN Usuário para controlar retorno ao kiosk após app liberado.
- [x] Solicitação inicial de permissão de execução em segundo plano.
- [x] Modo imersivo e tentativa de `startLockTask()`.
- [x] Ações básicas de whitelist: adicionar/remover e abrir app permitido.

## Fase 2 — Estabilização de campo
- [ ] Persistir mais estado operacional (último app aberto, horário de retorno, tentativas de PIN).
- [ ] Melhorar UX de PIN (teclado numérico dedicado, máscara, limite de tentativas).
- [ ] Melhorar logs para suporte (eventos de abertura, falha de lock task, falha de launch).
- [ ] Criar testes instrumentados para fluxos críticos de PIN e whitelist.

## Fase 3 — Lockdown corporativo real
- [ ] Provisionar dispositivos como **Device Owner**.
- [ ] Aplicar políticas com `DevicePolicyManager` (bloquear settings, status bar, etc.).
- [ ] Definir o app kiosk como launcher padrão do dispositivo.
- [ ] Gerenciar whitelist por política central (em vez de somente local).

## Fase 4 — Gestão remota
- [ ] Painel web para monitorar tablets (online/offline, bateria, versão).
- [ ] Configuração remota segura (PIN policy, whitelist e parâmetros).
- [ ] Heartbeat periódico e fila de comandos remotos.
- [ ] Atualização controlada do app (rollout por grupo/dispositivo).

## Fase 5 — Segurança e operação contínua
- [ ] Assinatura/release hardening e validação de integridade.
- [ ] Política de certificados/TLS pinning para APIs críticas.
- [ ] Telemetria e alertas operacionais.
- [ ] CI/CD com versionamento, changelog e trilha de auditoria.

## Checklist para piloto em produção
- [ ] Homologar pelo menos 2 modelos de tablet.
- [ ] Validar comportamento após reboot automático.
- [ ] Testar perda de rede e recuperação.
- [ ] Validar tentativas de fuga (home, recentes, notificações, USB).
- [ ] Documentar procedimento de suporte e reprovisionamento.
