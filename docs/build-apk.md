# Como montar o APK do Botsandro Kiosk

## 1) Pré-requisitos
- Android Studio instalado (recomendado: versão estável atual).
- SDK Platform Android 34 + Build-Tools instalados.
- Java 17 configurado.
- `adb` disponível no PATH (Android Platform-Tools).

## 2) Configuração inicial
Neste MVP, a tela principal funciona como launcher interno por lista branca de apps.

## Comandos por sistema operacional
### Windows (PowerShell)
Se aparecer erro `./gradlew não é reconhecido`, use:

```powershell
.\gradlew.bat clean assembleDebug
```

### Windows (CMD)
```cmd
gradlew.bat clean assembleDebug
```

### Linux/macOS/Git Bash
```bash
./gradlew clean assembleDebug
```

## Erro comum no Windows: `.\gradlew.bat` não reconhecido
Quando esse erro aparece, normalmente o PowerShell não está na pasta raiz do projeto ou os arquivos do Gradle Wrapper não existem localmente.

Diagnóstico rápido (PowerShell):

```powershell
pwd
Get-ChildItem .\gradlew*
Get-ChildItem .\gradle\wrapper
```

Resultado esperado:
- `gradlew.bat`
- `gradlew`
- `gradle\wrapper\gradle-wrapper.jar`
- `gradle\wrapper\gradle-wrapper.properties`

Se faltar algo:
1. Navegue para a raiz do projeto `Botsandro`.
2. Rode `git pull` para atualizar.
3. Se você baixou por ZIP, baixe novamente garantindo que os arquivos do wrapper vieram.

Com tudo presente, execute:

```powershell
.\gradlew.bat clean assembleDebug
```

## 3) Gerar APK de teste (debug)
Na raiz do projeto:

```bash
./gradlew clean assembleDebug
```

Saída:
- `app/build/outputs/apk/debug/app-debug.apk`

## 4) Instalar no tablet
Conecte por USB com depuração ativada e rode:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 5) Validar comportamento kiosk básico
- App abre em launcher interno do kiosk.
- Se não houver apps liberados, aparece estado vazio.
- Painel lateral permite configurar PIN e lista branca.
- Atalhos abrem somente os apps permitidos.

## 6) Gerar APK de release (produção)
1. Criar keystore:

```bash
keytool -genkeypair -v -keystore botsandro-release.jks -alias botsandro -keyalg RSA -keysize 2048 -validity 3650
```

2. Configurar assinatura no Gradle (bloco `signingConfigs`).
3. Gerar APK:

```bash
./gradlew clean assembleRelease
```

Saída:
- `app/build/outputs/apk/release/app-release.apk`

## 7) Próximo nível de lockdown
Para ficar no padrão “kiosk corporativo real” (tipo fast-food/self-service):
- Provisionar tablet como Device Owner.
- Aplicar políticas com `DevicePolicyManager`.
- Definir launcher do kiosk e bloquear settings/home/recentes.
