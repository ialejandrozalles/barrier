# Barrier Android Client

Este documento describe la implementación de cliente Android para Barrier incluida en `android-client/`.

## Objetivo

Permitir que un dispositivo Android actúe como cliente nativo de Barrier en red LAN, sin duplicación de pantalla, sin mirroring y sin escritorio remoto. El dispositivo se integra como una pantalla independiente controlable por mouse/teclado desde un servidor Barrier (Windows/Linux/macOS).

## Crédito y atribución

**Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)**

Este crédito está incluido en:

- Código fuente Kotlin del módulo Android.
- Interfaz de la app Android.
- Esta documentación.

No debe eliminarse ni modificarse en futuras versiones derivadas del módulo Android.

## Estructura del módulo

- `android-client/app/src/main/java/org/barrierfoss/androidclient/protocol/`
  - Implementación de transporte y protocolo Barrier 1.6 (`HELLO`, `HELLO_BACK`, `QINF/DINF`, `CINN/COUT`, input y keepalive).
- `android-client/app/src/main/java/org/barrierfoss/androidclient/service/`
  - Servicio de conexión foreground y servicio de accesibilidad.
- `android-client/app/src/main/java/org/barrierfoss/androidclient/input/`
  - Mapeo de eventos Barrier a gestos táctiles, cursor overlay e inyección de texto.
- `android-client/app/src/main/java/org/barrierfoss/androidclient/data/`
  - Configuración persistente de host/puerto/nombre de pantalla.

## Flujo técnico

1. La app abre conexión TCP al servidor Barrier (`host:port`, por defecto `24800`).
2. Se negocia protocolo (`Barrier 1.6`) usando handshake equivalente al cliente clásico de Barrier.
3. El cliente Android responde a `QINF` con `DINF` usando la resolución real del dispositivo.
4. Cuando el servidor envía `CINN`, Android entra en modo control remoto y muestra cursor overlay.
5. Eventos `DMMV/DMRM/DMDN/DMUP/DMWM/DKDN/DKUP/DKRP` se traducen a input Android.
6. Cuando llega `COUT`, Android sale del modo control remoto.

## Integración con pantallas virtuales de Barrier

No requiere cambios en el servidor Barrier para aparecer como pantalla virtual: se usa el modelo estándar por nombre de cliente.

Pasos en servidor (PC):

1. Abrir Barrier en modo servidor.
2. Entrar a **Configure Server**.
3. Arrastrar una pantalla nueva al layout.
4. Asignar el `screen name` exactamente igual al configurado en la app Android (sensible a mayúsculas/minúsculas).
5. Posicionar la pantalla Android (arriba/abajo/izquierda/derecha) para controlar el lado de transición del cursor.
6. Iniciar servidor.

Con esto, al mover el cursor fuera del borde configurado en PC, el control transiciona automáticamente al Android igual que entre clientes de escritorio.

## Mapeo de input en Android

- Mouse move (`DMMV/DMRM`) -> movimiento de cursor overlay y control táctil.
- Mouse left click (`DMDN/DMUP`) -> tap.
- Mouse left hold -> long press.
- Mouse drag con botón izquierdo sostenido -> drag gesture.
- Mouse wheel (`DMWM`) -> scroll vertical por gestos.
- Keyboard:
  - caracteres imprimibles -> inyección de texto en campo editable enfocado.
  - enter/tab/backspace -> acciones de edición.
  - combinaciones comunes (`Ctrl+C`, `Ctrl+V`, `Ctrl+X`, `Ctrl+A`) -> acciones de texto cuando aplica.

## Requisitos de compilación APK

- Android Studio (recomendado) o Gradle local.
- JDK 17.
- Android SDK 34.

## Compilar APK

Desde terminal:

```bash
cd android-client
gradle assembleDebug
```

APK generado:

- `android-client/app/build/outputs/apk/debug/app-debug.apk`

Para release:

```bash
cd android-client
gradle assembleRelease
```

APK release:

- `android-client/app/build/outputs/apk/release/app-release.apk`

## Uso en Android

1. Instalar el APK.
2. Abrir la app.
3. Configurar:
   - IP/host del servidor Barrier.
   - Puerto (por defecto `24800`).
   - Nombre de pantalla (el mismo configurado en Barrier server).
4. Habilitar servicio de accesibilidad de la app.
5. Pulsar **Iniciar cliente**.
6. Iniciar Barrier server en el PC.

## Compatibilidad objetivo

- Servidor Windows <-> Cliente Android
- Servidor Linux <-> Cliente Android

## Restricciones de diseño aplicadas

- No se implementa screen mirroring.
- No se implementa duplicación/extensión de video.
- No se implementa escritorio remoto.
- Android actúa exclusivamente como cliente Barrier controlable por eventos de input, igual al modelo de clientes estándar.
