# Botsandro Kiosk (MVP)

Projeto inicial para criar um APK de tablet em modo kiosk/lockdown para operação empresarial.

## O que já está pronto
- App Android nativo em Kotlin.
- Tela única em `WebView` para abrir o painel web de monitoramento.
- Modo imersivo para esconder barra de navegação/status.
- Tentativa de ativar `Lock Task Mode` (fixação de tela) quando o dispositivo estiver preparado como device owner.
- Back controlado: só volta no histórico da WebView.
- Mensagem de fallback quando a URL principal estiver indisponível.

## Estrutura
- `app/src/main/java/com/botsandro/kiosk/MainActivity.kt`: comportamento de kiosk.
- `app/src/main/AndroidManifest.xml`: configuração de app e atividade.
- `docs/kiosk-roadmap.md`: próximos passos de segurança e operação.

## Configurar a URL do painel
Por padrão, a URL está em `https://example.com`. Para build de teste, use:

```bash
./gradlew assembleDebug -PkioskUrl="https://seu-painel"
```

## Comandos por sistema operacional
### Windows (PowerShell)
Use o script `.bat`:

```powershell
.\gradlew.bat clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

### Windows (Git Bash)
```bash
./gradlew clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

### Linux/macOS
```bash
./gradlew clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

## Passo a passo para gerar o APK de teste (debug)
1. Instalar Android SDK + Android Build Tools (via Android Studio).
2. No terminal da raiz do projeto, gerar o build:
   ```bash
   ./gradlew clean assembleDebug -PkioskUrl="https://seu-painel"
   ```
3. APK de saída:
   - `app/build/outputs/apk/debug/app-debug.apk`
4. Instalar no tablet conectado por USB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. Abrir o app `Botsandro Kiosk` no tablet e validar navegação.

## Passo a passo para APK de produção (release assinado)
1. Criar keystore:
   ```bash
   keytool -genkeypair -v -keystore botsandro-release.jks -alias botsandro -keyalg RSA -keysize 2048 -validity 3650
   ```
2. Configurar assinatura no `app/build.gradle.kts` (bloco `signingConfigs` + `buildTypes.release`).
3. Gerar release:
   ```bash
   ./gradlew clean assembleRelease -PkioskUrl="https://seu-painel"
   ```
4. APK de saída:
   - `app/build/outputs/apk/release/app-release.apk`

> Observação: o bloqueio total de configurações/home/recentes exige provisionamento de dispositivo (MDM, Android Enterprise ou fluxo de device owner).
