# Barrier Android Client Module

Android client implementation for Barrier protocol 1.6.

See full documentation in `../doc/android-client.md`.

## Quick build

Requirements:

- JDK 17
- Android SDK 34
- Gradle installed in PATH

Commands:

```bash
cd android-client
gradle assembleDebug
```

Windows helper script:

```bat
build_apk.bat
```

APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

## USB (ADB reverse)

Para baja latencia por USB, habilita USB debugging y ejecuta en el PC:

```bash
adb devices
adb reverse tcp:24800 tcp:24800
```

El cliente intenta USB automaticamente cuando detecta el cable y vuelve a LAN si
no hay ADB reverse.

## Credit

Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)
