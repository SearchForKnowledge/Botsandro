# Botsandro Kiosk (MVP)

Projeto inicial para criar um APK de tablet em modo kiosk/lockdown para operação empresarial.

## O que já está pronto
- App Android nativo em Kotlin.
- Tela inicial estilo launcher interno baseada em lista branca de aplicativos.
- Modo imersivo para esconder barra de navegação/status.
- Tentativa de ativar `Lock Task Mode` (fixação de tela) quando o dispositivo estiver preparado como device owner.
- PIN para proteger a abertura do painel lateral de configurações.
- Gestão de lista branca: adicionar/remover apps permitidos e abrir somente por atalhos internos.
- Tela vazia quando não há app na lista branca (com instrução para configuração).

## Estrutura
- `app/src/main/java/com/botsandro/kiosk/MainActivity.kt`: comportamento de kiosk.
- `app/src/main/AndroidManifest.xml`: configuração de app e atividade.
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

## Erro comum no Windows: `gradlew.bat` não reconhecido
Se o PowerShell disser que `./gradlew` ou `.\gradlew.bat` não existe, quase sempre é porque o terminal **não está na pasta raiz do projeto** ou os arquivos do wrapper não vieram no checkout.

No PowerShell, rode:

```powershell
pwd
Get-ChildItem .\gradlew*
```

Você deve ver os arquivos `gradlew` e `gradlew.bat`.

Se não aparecerem:
1. Entre na pasta correta do projeto (`Botsandro`) no terminal.
2. Atualize o repositório:
   ```powershell
   git pull
   ```
3. Confirme que existe também a pasta `gradle\wrapper` com:
   - `gradle-wrapper.jar`
   - `gradle-wrapper.properties`

Depois execute novamente:

```powershell
.\gradlew.bat clean assembleDebug
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
5. Abrir o app `Botsandro Kiosk` no tablet e validar navegação.

## Passo a passo para APK de produção (release assinado)
1. Criar keystore:
   ```bash
   keytool -genkeypair -v -keystore botsandro-release.jks -alias botsandro -keyalg RSA -keysize 2048 -validity 3650
   ```
2. Configurar assinatura no `app/build.gradle.kts` (bloco `signingConfigs` + `buildTypes.release`).
3. Gerar release:
   ```bash
   ./gradlew clean assembleRelease
   ```
4. APK de saída:
   - `app/build/outputs/apk/release/app-release.apk`

> Observação: o bloqueio total de configurações/home/recentes exige provisionamento de dispositivo (MDM, Android Enterprise ou fluxo de device owner).
