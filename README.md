# Botsandro Kiosk (MVP)

Projeto inicial para criar um APK de tablet em modo kiosk/lockdown para operação empresarial.

## O que já está pronto
- App Android nativo em Kotlin.
- Tela inicial estilo launcher interno baseada em lista branca de aplicativos.
- Modo imersivo para esconder barra de navegação/status.
- Tentativa de ativar `Lock Task Mode` (fixação de tela) quando o dispositivo estiver preparado como device owner.
- Permissão de execução em segundo plano solicitada no primeiro uso (com atalho manual nas configurações).
- PIN Admin para abrir painel de configurações e para sair do modo kiosk.
- PIN Usuário para controlar retorno ao kiosk após abrir app da lista branca.
- Gestão de lista branca: adicionar/remover apps permitidos e abrir somente por atalhos internos.
- Tela vazia quando não há app na lista branca (com instrução para configuração).

## Estrutura
- `app/src/main/java/com/botsandro/kiosk/MainActivity.kt`: comportamento de kiosk (PIN admin/usuário, lista branca, lock task e permissões).
- `app/src/main/res/layout/activity_main.xml`: launcher e painel lateral de configurações.
- `app/src/main/AndroidManifest.xml`: permissões e configuração da atividade principal.
- `docs/kiosk-roadmap.md`: próximos passos de segurança e operação.

## Comandos por sistema operacional
### Windows (PowerShell)
Use o script `.bat`:

```powershell
.\gradlew.bat clean assembleDebug
```

### Windows (Git Bash)
```bash
./gradlew clean assembleDebug
```

### Linux/macOS
```bash
./gradlew clean assembleDebug
```

## Passo a passo para gerar o APK de teste (debug)
1. Instalar Android SDK + Android Build Tools (via Android Studio).
2. No terminal da raiz do projeto, gerar o build:
   ```bash
   ./gradlew clean assembleDebug
   ```
3. APK de saída:
   - `app/build/outputs/apk/debug/app-debug.apk`
4. Instalar no tablet conectado por USB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. Abrir o app `Botsandro Kiosk` no tablet e validar fluxo completo (PINs + lista branca + retorno ao kiosk).

## Limitações importantes do Android (kiosk)
- A mensagem de fixação de tela (*screen pinning*) é do sistema Android e não pode ser customizada/removida por app comum.
- Bloquear totalmente notificações e central de configurações rápidas exige modo Device Owner + políticas do `DevicePolicyManager`.
- Para manter outro app “fixado” fora do seu app de kiosk em todos os cenários, é necessário provisionamento corporativo (Device Owner/MDM).
