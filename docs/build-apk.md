# Como gerar e validar o APK do Botsandro Kiosk

## 1) Pré-requisitos
- Android Studio instalado (versão estável atual).
- SDK Platform Android 34 + Build-Tools.
- Java 17 configurado.
- `adb` no PATH (Android Platform-Tools).

## 2) Build (debug)
Na raiz do projeto, rode **um** dos comandos abaixo conforme seu terminal.

### Windows (PowerShell)
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

Saída esperada:
- `app/build/outputs/apk/debug/app-debug.apk`

## 3) Instalação no tablet
Com depuração USB habilitada:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4) Fluxo funcional para validar o MVP
Após instalar e abrir o app:

1. O aplicativo mostra o launcher interno (grade de apps da lista branca).
2. No primeiro uso, aparece o diálogo para permitir execução em segundo plano.
3. Abra configurações pelo ícone da toolbar (lado esquerdo):
   - Se não existir PIN Admin, o app solicita criar um.
   - Com PIN Admin configurado, o painel só abre com PIN correto.
4. Configure os PINs:
   - **PIN Admin**: protege configurações e saída manual do modo kiosk.
   - **PIN Usuário**: exige autenticação quando o usuário retorna de app da lista branca para o kiosk.
5. Adicione apps na lista branca e tente abrir:
   - O app kiosk solta lock task localmente para permitir abrir o app externo.
   - Ao voltar para o kiosk, lock task é reativado e o PIN Usuário é solicitado (se configurado).
6. Teste o botão “Sair do modo kiosk”:
   - Deve solicitar PIN Admin.

## 5) Release (produção)
1. Criar keystore (exemplo):

```bash
keytool -genkeypair -v -keystore botsandro-release.jks -alias botsandro -keyalg RSA -keysize 2048 -validity 3650
```

2. Configurar assinatura em `app/build.gradle.kts` (`signingConfigs`).
3. Gerar APK release:

```bash
./gradlew clean assembleRelease
```

Saída esperada:
- `app/build/outputs/apk/release/app-release.apk`

## 6) Problemas comuns
### `gradlew` / `gradlew.bat` não encontrado
Verifique se você está na raiz do projeto e se os arquivos existem:

```powershell
pwd
Get-ChildItem .\gradlew*
Get-ChildItem .\gradle\wrapper
```

### App não consegue bloquear tudo (home/notificações/configurações rápidas)
Isso é limitação de app comum Android. Bloqueio corporativo completo exige Device Owner/MDM com `DevicePolicyManager`.

### App da lista branca não abre
No código atual, o kiosk chama `stopLockTaskIfActive()` antes de abrir app externo. Se ainda falhar no aparelho, valide política do fabricante/ROM e permissões do app alvo.
