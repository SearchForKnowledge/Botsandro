# Como montar o APK do Botsandro Kiosk

## 1) Pré-requisitos
- Android Studio instalado (recomendado: versão estável atual).
- SDK Platform Android 34 + Build-Tools instalados.
- Java 17 configurado.
- `adb` disponível no PATH (Android Platform-Tools).

## 2) Configurar URL do painel
Você pode definir a URL no momento do build com a propriedade `kioskUrl`.

Exemplo:

```bash
./gradlew assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

## Comandos por sistema operacional
### Windows (PowerShell)
Se aparecer erro `./gradlew não é reconhecido`, use:

```powershell
.\gradlew.bat clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

### Windows (CMD)
```cmd
gradlew.bat clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

### Linux/macOS/Git Bash
```bash
./gradlew clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
```

## 3) Gerar APK de teste (debug)
Na raiz do projeto:

```bash
./gradlew clean assembleDebug -PkioskUrl="https://painel.suaempresa.com"
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
- App abre direto no WebView.
- Oculta barra de status/navegação (modo imersivo).
- Botão voltar navega no histórico web (quando existir).
- Se a URL falhar, mostra tela de indisponibilidade.

## 6) Gerar APK de release (produção)
1. Criar keystore:

```bash
keytool -genkeypair -v -keystore botsandro-release.jks -alias botsandro -keyalg RSA -keysize 2048 -validity 3650
```

2. Configurar assinatura no Gradle (bloco `signingConfigs`).
3. Gerar APK:

```bash
./gradlew clean assembleRelease -PkioskUrl="https://painel.suaempresa.com"
```

Saída:
- `app/build/outputs/apk/release/app-release.apk`

## 7) Próximo nível de lockdown
Para ficar no padrão “kiosk corporativo real” (tipo fast-food/self-service):
- Provisionar tablet como Device Owner.
- Aplicar políticas com `DevicePolicyManager`.
- Definir launcher do kiosk e bloquear settings/home/recentes.
