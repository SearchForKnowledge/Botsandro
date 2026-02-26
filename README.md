Botsandro Kiosk (MVP)

Projeto Android em Kotlin para transformar tablets em modo kiosk corporativo (lockdown / launcher restrito).

Este app pode operar como Device Owner, permitindo controle avançado via DevicePolicyManager.

📦 O que já está implementado

✅ App Android nativo em Kotlin

✅ Launcher interno baseado em lista branca

✅ Modo imersivo (oculta barra de navegação e status)

✅ Lock Task Mode automático (quando configurado como Device Owner)

✅ Definição como HOME padrão (launcher corporativo)

✅ PIN Admin:

Abrir configurações

Sair do modo kiosk

✅ PIN Usuário:

Controlar retorno ao kiosk após abrir app permitido

✅ Gestão de lista branca:

Adicionar apps permitidos

Remover apps permitidos

Aplicação automática no DevicePolicyManager

✅ Solicitação de permissão para ignorar otimização de bateria

✅ Compatível com atualização via adb install -r

🧠 Conceitos Importantes
Device Admin vs Device Owner
Tipo	Nível	Pode remover via ADB?
Device Admin comum	Médio	Sim
Device Owner	Total (corporativo)	❌ Normalmente não

Botsandro Kiosk usa Device Owner para:

Definir launcher HOME padrão

Configurar whitelist do LockTask

Controlar modo fixado sem intervenção do usuário

📂 Estrutura
app/src/main/java/com/botsandro/kiosk/MainActivity.kt
app/src/main/java/com/botsandro/kiosk/KioskDeviceAdminReceiver.kt
app/src/main/res/layout/activity_main.xml
app/src/main/AndroidManifest.xml
🔧 Build do Projeto
Windows (PowerShell)
.\gradlew.bat clean assembleDebug
Git Bash / Linux / macOS
./gradlew clean assembleDebug

APK gerado em:

app/build/outputs/apk/debug/app-debug.apk
📲 Instalação no Tablet
Instalar / Atualizar APK (RECOMENDADO)
adb install -r app/build/outputs/apk/debug/app-debug.apk

⚠️ Sempre prefira -r (reinstalar) para manter o Device Owner.

🔐 Configurar como Device Owner

⚠️ Só funciona em dispositivo novo ou recém-resetado (ou sem owner).

1️⃣ Instalar o APK
adb install --user 0 -r app-debug.apk
2️⃣ Definir como Device Owner
adb shell dpm set-device-owner --user 0 com.botsandro.kiosk.debug/com.botsandro.kiosk.KioskDeviceAdminReceiver

Se usar versão release:

adb shell dpm set-device-owner --user 0 com.botsandro.kiosk/com.botsandro.kiosk.KioskDeviceAdminReceiver
🔎 Verificar se virou Device Owner
adb shell dpm list-owners

Saída esperada:

1 owner:
User  0: admin=com.botsandro.kiosk.debug/com.botsandro.kiosk.KioskDeviceAdminReceiver,DeviceOwner
❗ Erros Comuns e Soluções
❌ Unknown admin
java.lang.IllegalArgumentException: Unknown admin
Causa:

Classe errada no comando.

Verifique:
adb shell dumpsys package com.botsandro.kiosk.debug | findstr KioskDeviceAdminReceiver

Use exatamente o nome que aparecer.

❌ DELETE_FAILED_DEVICE_POLICY_MANAGER
Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]
Causa:

App está como Device Owner.

Solução:

NÃO desinstalar

Usar apenas:

adb install -r app-debug.apk

Se realmente precisar remover → Factory Reset necessário

❌ Attempt to remove non-test admin
SecurityException: Attempt to remove non-test admin
Significado:

Seu app é Device Owner real.
Android não permite remover via ADB.

Solução:

Factory Reset do dispositivo.

❌ Não consegue set-device-owner novamente

Se aparecer erro após já ter configurado antes:

O Android só permite 1 Device Owner.

Se já houve provisionamento anterior, pode exigir reset.

🔄 Atualização Segura do APK

Sempre use:

adb install -r app-debug.apk

Nunca use:

adb uninstall com.botsandro.kiosk.debug

Se desinstalar, pode:

Perder owner

Ficar preso sem conseguir reinstalar como owner

Exigir reset

🏗 Fluxo Correto para Produção

Definir package final (evitar .debug)

Resetar dispositivo

Instalar APK final

Rodar:

adb shell dpm set-device-owner --user 0 com.botsandro.kiosk/com.botsandro.kiosk.KioskDeviceAdminReceiver

Nunca mais desinstalar — apenas atualizar

🛡 Limitações do Android (Importante)

Não é possível remover mensagem de fixação padrão do Android

Não é possível remover Device Owner via ADB em produção

Notificações e quick settings só são totalmente controláveis com Device Owner

Provisionamento completo corporativo pode exigir QR provisioning (Android Enterprise)

🚀 Roadmap Futuro

Bloqueio de barra de notificações totalmente via política

Bloqueio de power menu

Bloqueio de USB debugging

Remote config

Provisionamento via QR Code Android Enterprise

Build release assinado com keystore empresarial

📌 Resumo Final
Cenário	O que fazer
Atualizar app	adb install -r
Erro DELETE_FAILED_DEVICE_POLICY_MANAGER	Não desinstalar
Quer trocar package	Factory reset
Unknown admin	Conferir nome via dumpsys
Já é owner	Não rodar set-device-owner de novo
