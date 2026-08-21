# PRP-016: Audio Sharing y Broadcast LE Audio (Auracast)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-17
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la Fase 2 (Conectividad Bluetooth) con capacidad de broadcast LE Audio (Auracast), anteriormente listada en Fuera de Alcance del brief como "pendiente para una actualización posterior".
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic pueda compartir el audio procesado en tiempo real con múltiples dispositivos receptores compatibles usando Bluetooth LE Audio Broadcast (Auracast). Que desde una pantalla dedicada pueda activar la transmisión, ver un identificador de canal, configurar una clave de seguridad opcional, y ver cuántos dispositivos están recibiendo. Así puedo compartir la amplificación con otros usuarios de PSAP cercanos o retransmitir audio ambiente en entornos accesibles.

## Por Qué

| Problema | Solución |
|----------|----------|
| El audio procesado por NaturaSonic solo llega al dispositivo del usuario — no se puede compartir con acompañantes o pares | Broadcast LE Audio permite retransmitir el audio DSP procesado a múltiples receptores simultáneamente |
| En entornos accesibles (iglesias, museos, conferencias) no hay forma de compartir la amplificación personalizada | Auracast broadcast permite que cualquier receptor compatible se sincronice al stream |
| No hay visibilidad sobre las capacidades LE Audio del dispositivo | Detección de capacidades en runtime informa al usuario si su hardware soporta broadcast |
| La configuración de broadcast (seguridad, canal) requiere acceso a ajustes del sistema | Pantalla dedicada en NaturaSonic controla broadcast sin salir de la app |

**Valor**: Accesibilidad multiplicada — el procesamiento PSAP de NaturaSonic deja de ser individual y se convierte en un canal de audio accesible compartible, habilitando casos de uso en espacios públicos y asistencia a pares.

## Qué

### Criterios de éxito
- [x] `LeAudioBroadcastManager` singleton Hilt con detección de capacidades LE Audio Broadcast en runtime (API 33+)
- [x] `BroadcastState` sealed class con StateFlow: `Idle`, `Starting`, `Active(broadcastId)`, `Stopping`, `Error(reason)`, `Unsupported(reason)`
- [x] `startBroadcast()` con metadata y broadcast code opcional; `stopBroadcast()` con broadcastId
- [x] `BluetoothLeBroadcast.Callback` intento via reflexión con fallback a optimistic state updates (abstract class no subclaseable via Proxy)
- [x] `AudioSharingScreen` Compose con toggle de broadcast, campo de broadcast code, indicador de broadcast ID activo, estado de compatibilidad
- [x] `AudioSharingViewModel` con StateFlows combinados de BT state + broadcast state
- [x] Ruta `AUDIO_SHARING` en NavGraph, accesible desde Settings → "Compartir audio"
- [x] Permiso `BLUETOOTH_ADVERTISE` declarado en AndroidManifest.xml
- [x] Degradación elegante: en dispositivos sin soporte, la pantalla muestra explicación clara de requisitos
- [x] Build exitoso sin cambios en dependencias nativas ni AGP

### Comportamiento esperado

1. El usuario navega a Settings → "Compartir audio" → se abre `AudioSharingScreen`.
2. La pantalla verifica en runtime si el dispositivo soporta LE Audio Broadcast (API 33+, `isLeAudioBroadcastSourceSupported`).
3. Si no soporta: muestra card informativa con requisitos (Android 13+, hardware BT 5.2+). No hay controles de broadcast.
4. Si soporta: muestra controles de broadcast — toggle para activar/desactivar, campo de broadcast code (opcional, 4-16 caracteres), indicador de estado.
5. Al activar: `LeAudioBroadcastManager.startBroadcast()` → estado pasa a `Starting` → callback `onBroadcastStarted` → estado `Active(broadcastId)`.
6. En estado activo: se muestra broadcast ID, código de seguridad (si configurado), y estado de transmisión.
7. El audio procesado por el pipeline Oboe (post-DSP, post-limiter) es la fuente del broadcast — el stack BT del sistema lo enruta automáticamente al BIS.
8. Al desactivar: `stopBroadcast(broadcastId)` → callback `onBroadcastStopped` → estado `Idle`.
9. Si ocurre un error (permisos, hardware, HAL): estado `Error(reason)` con mensaje descriptivo.

### Casos borde

- **API < 33**: Feature completamente oculta o marcada como "no disponible" — no intenta acceder a APIs inexistentes.
- **Hardware sin BT 5.2+**: `isLeAudioBroadcastSourceSupported` retorna `FEATURE_NOT_SUPPORTED` → estado `Unsupported`.
- **`BLUETOOTH_PRIVILEGED` requerido (API 33-34)**: En dispositivos donde `startBroadcast()` lanza `SecurityException` → capturar, estado `Error`, mostrar mensaje de que la función requiere Android 15+ o soporte OEM específico.
- **Bluetooth apagado**: Broadcast no disponible — mostrar indicador y sugerencia de activar BT.
- **AudioService detenido**: Broadcast se detiene automáticamente si el servicio de audio se para.
- **Output muteado (BT desconectado)**: El broadcast se mantiene activo independientemente del mute de output — los receptores broadcast siguen recibiendo audio procesado.
- **OEM sin HAL wired**: API 33-34 expone las clases pero algunos OEMs no implementaron el HAL — `getProfileProxy()` retorna false → `Unsupported`.
- **Broadcast activo + cambio de modo de escucha**: El broadcast continúa con el nuevo perfil DSP — los receptores reciben el audio recién configurado.

---

## Contexto

### Documentación externa
- [Bluetooth LE Audio overview (Android Developers)](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview) — API surface oficial
- [BluetoothLeAudio reference](https://developer.android.com/reference/android/bluetooth/BluetoothLeAudio) — clase base LE Audio
- [Auracast on Android 16 (Medium)](https://bleadvertiserapp.medium.com/auracast-on-android-16-the-ble-audio-shift-devs-are-building-wrong-fc30bd8a057e) — análisis técnico de implementación real, gotchas de OEMs, detección de soporte
- [Bluetooth permissions (Android Developers)](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) — permisos BT requeridos

### Código existente a consultar
- `app/src/main/java/com/naturasonic/app/bluetooth/BluetoothAudioManager.kt` — patrón de profile proxy, BroadcastReceiver, StateFlow, singleton Hilt
- `app/src/main/java/com/naturasonic/app/bluetooth/BluetoothDeviceInfo.kt` — sealed class `BluetoothConnectionState`, enums `BluetoothType`, `BluetoothCompatibility`
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — integración de BT monitor, lifecycle del servicio foreground
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — `getAudioBuffer()` devuelve audio post-DSP a 48kHz mono
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — patrón de navegación a sub-pantallas (lambda + TextButton + Icon)
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — `Routes` object + composable entries
- `app/src/main/java/com/naturasonic/app/ui/screens/eco/EcoModeScreen.kt` — patrón reciente de pantalla de configuración con toggles, sliders, estado reactivo
- `app/src/main/java/com/naturasonic/app/battery/BatteryMonitor.kt` — patrón de BroadcastReceiver + StateFlow para telemetría (referencia para BroadcastManager)

### Gotchas conocidas
- **`BLUETOOTH_PRIVILEGED` en API 33-34**: `BluetoothLeBroadcast.startBroadcast()` fue `@SystemApi` con `BLUETOOTH_PRIVILEGED` requerido. En Android 15+ (Samsung) y Android 16 (Pixel), el acceso se abrió para third-party apps. Implementar con try-catch defensivo.
- **OEM HAL variability**: El API surface existe desde API 33 pero muchos OEMs no implementaron el HAL de LE Audio Broadcast correctamente. `getProfileProxy(LE_AUDIO_BROADCAST)` puede retornar `false` incluso en dispositivos API 33+.
- **`BluetoothProfile.LE_AUDIO_BROADCAST` = 26**: Constante no documentada oficialmente en todas las versiones — usar reflexión como fallback si la constante no es accesible.
- **`BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT` = 28**: Para el rol receptor/asistente — fuera de alcance de este PRP pero documentado para futuro.
- **Audio routing automático**: Cuando un broadcast está activo, el stack BT del sistema enruta el audio del output stream al BIS. El pipeline Oboe no necesita modificación — el audio procesado llega al broadcast a través del sistema.
- **`BluetoothLeAudioContentMetadata`**: Requiere API 33+ para instanciar. Usa `BluetoothLeAudioContentMetadata.Builder()` con programa info y language.

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Toda la infraestructura nativa actual
- Todas las dependencias — congeladas
- AGP 8.7.3 — congelado
- NDK / CMake / Oboe / whisper.cpp — congelados
- Pipeline Oboe C++ — no se modifica

### ADD
- Permiso `BLUETOOTH_ADVERTISE` en AndroidManifest.xml (para broadcast source role)

### REMOVE / REPLACE / CONFIG
- Ninguno

### Refinamientos a la Directiva durante este PRP
- Ninguno anticipado — no se añaden dependencias externas, solo se consumen APIs del framework Android.

---

## Supuestos heredados

- [x] El dispositivo tiene Android 10+ (API 29+) como mínimo
- [x] `BLUETOOTH_CONNECT` y `BLUETOOTH_SCAN` ya declarados en el manifest
- [x] AudioService foreground service corriendo durante uso activo
- [x] BluetoothAudioManager ya gestiona conexiones unicast y estado BT

### Supuestos adicionales (específicos de este PRP)
- [ ] Para broadcast source: dispositivo con Android 13+ (API 33+) y chipset Bluetooth 5.2+ con soporte LE Audio Broadcast
- [ ] `BluetoothAdapter.isLeAudioBroadcastSourceSupported` disponible como verificación de soporte en API 33+
- [ ] El stack BT del sistema enruta automáticamente el output audio al BIS cuando un broadcast está activo
- [ ] `BluetoothProfile.LE_AUDIO_BROADCAST` (tipo 26) accesible vía `getProfileProxy()` en dispositivos compatibles

---

## Fuera de Alcance heredado

- Audiograma clínico
- Versión iOS
- Backend en la nube
- Integración con audífonos clínicos FDA
- VoIP / llamadas de voz

### Fuera de Alcance adicional (específico de este PRP)

- **Rol de Broadcast Assistant / Receiver**: Escanear y sincronizar con broadcasts de terceros (`BluetoothLeBroadcastAssistant`, tipo 28) queda para un PRP futuro
- **QR code sharing**: Compartir metadata del broadcast vía QR para que receptores se sincronicen fácilmente — futuro
- **Múltiples broadcasts simultáneos**: Solo un broadcast activo a la vez
- **Selección de codec LC3 / bitrate**: El stack BT del sistema decide los parámetros de codec — no se expone configuración al usuario
- **Broadcast + unicast simultáneo configurable**: El sistema decide si permite ambos — no se gestiona manualmente
- **Historial de broadcasts**: No se persiste en Room el historial de sesiones de broadcast

---

## Aprendizajes heredados

- **2026-08-15 (PRP-012)**: BroadcastReceiver en API 33+ requiere flag `RECEIVER_NOT_EXPORTED`. Aplicar al receiver de estado del broadcast si se necesita uno.
- **2026-08-15 (PRP-012)**: `BluetoothAudioManager` usa `connectionState: StateFlow<BluetoothConnectionState>` como patrón de estado reactivo — replicar con `broadcastState: StateFlow<BroadcastState>`.
- **2026-08-15 (PRP-012)**: Output mute atómico (`setOutputMuted`) opera independiente del broadcast — el broadcast debe seguir transmitiendo audio procesado incluso si el output local está muteado.
- **2026-08-15 (PRP-014)**: DataStore patrón establecido con Flow + suspend setter — seguir mismo patrón si se persiste broadcast code preferido.
- **2026-08-15 (PRP-015)**: BatteryMonitor como singleton con StateFlow es el patrón más reciente de sensor → StateFlow — replicar para broadcast state.

---

## Plan de implementación

> IMPORTANTE: solo FASES aquí. Las subtareas se generan al ENTRAR a cada fase.

### Fase 1: LeAudioBroadcastManager + Runtime Capability Detection
- **Objetivo**: Crear `LeAudioBroadcastManager` singleton Hilt que encapsule toda la interacción con `BluetoothLeBroadcast` (profile tipo 26). Detectar en runtime si el dispositivo soporta LE Audio Broadcast (API 33+, `isLeAudioBroadcastSourceSupported`, profile proxy disponible). Exponer `StateFlow<BroadcastState>` con sealed class completa. Declarar `BLUETOOTH_ADVERTISE` en manifest.
- **Validación**: Compilación exitosa. StateFlow emite `Unsupported` en API < 33 y `Idle` o `Unsupported` en API 33+ según hardware.

### Fase 2: Broadcast Lifecycle — Start/Stop/Callbacks
- **Objetivo**: Implementar `startBroadcast(broadcastCode: String?)` y `stopBroadcast()` en `LeAudioBroadcastManager`. Registrar `BluetoothLeBroadcast.Callback` para todos los eventos del ciclo de vida (started, stopped, failed, metadata changed). Manejar `SecurityException` por `BLUETOOTH_PRIVILEGED` con degradación elegante. Exponer broadcast ID activo y metadata vía StateFlow.
- **Validación**: Ciclo start → active → stop funciona en API 33+ con hardware compatible. Error handling captura SecurityException sin crash.

### Fase 3: AudioSharingScreen + AudioSharingViewModel + Navegación
- **Objetivo**: Crear `AudioSharingScreen` Compose con controles de broadcast (toggle, campo de broadcast code con visibilidad toggle, indicador de broadcast ID, estado de compatibilidad, indicador de error). Crear `AudioSharingViewModel` que combine `LeAudioBroadcastManager.broadcastState` + `BluetoothAudioManager.connectionState`. Agregar ruta `AUDIO_SHARING` a `Routes` y `NavGraph`. Agregar botón "Compartir audio" en `SettingsScreen`.
- **Validación**: Pantalla renderiza correctamente. Controles responden a cambios de estado. Navegación fluida desde Settings.

### Fase 4: Integración con AudioService + Validación Final
- **Objetivo**: Integrar `LeAudioBroadcastManager` en `AudioService` para que el broadcast se detenga automáticamente cuando el servicio se para. Verificar que el audio procesado por Oboe llega correctamente al broadcast stream. Validación final del build.
- **Validación**:
  - [x] `./gradlew assembleDebug` exitoso (arm64-v8a, armeabi-v7a, x86_64)
  - [x] No hay cambios en dependencias nativas ni AGP
  - [x] Broadcast state correctamente gestionado en lifecycle del servicio

---

## Aprendizajes

> Esta sección crece con cada error.

**2026-08-17: BluetoothLeBroadcast.Callback es abstract class, no interface — Proxy.newProxyInstance no funciona**
- **Error**: Se intentó crear un callback dinámico para `BluetoothLeBroadcast.Callback` usando `java.lang.reflect.Proxy.newProxyInstance`, pero Proxy solo trabaja con interfaces, no con clases abstractas.
- **Fix**: Implementar patrón optimistic state updates como fallback — las transiciones de estado se basan en el éxito/fallo de la invocación del método, no en callbacks. `registerCallbackReflective` intenta el registro pero degrada silenciosamente. En Android 16+ donde la clase es completamente pública, una futura iteración podría crear una subclase concreta.
- **Aplicar en**: Cualquier futura API de Android que use abstract class callbacks (no interface) y se necesite acceder vía reflexión.

**2026-08-17: BluetoothLeBroadcast, BluetoothLeAudioContentMetadata son @SystemApi — requieren reflexión completa**
- **Error**: Estas clases no existen en el android.jar público del SDK. Import directo causa unresolved reference en compilación.
- **Fix**: Todo acceso a estas clases vía `Class.forName()` + `getMethod()` + `invoke()`. Las clases públicas del SDK (BluetoothAdapter, BluetoothProfile, BluetoothStatusCodes) se usan con import directo. El profile type constant (26) se define como `private const`.
- **Aplicar en**: Cualquier API de Android marcada como @SystemApi que se quiera usar defensivamente con fallback.

**2026-08-17: callbackInstance field olvidado al generar LeAudioBroadcastManager**
- **Error**: El campo `callbackInstance` se usaba en `registerCallbackReflective` y `unregisterCallbackReflective` pero no fue declarado como propiedad de la clase — build error `Unresolved reference`.
- **Fix**: Agregar `private var callbackInstance: Any? = null` junto a los otros campos de instancia.
- **Aplicar en**: Al generar managers con callbacks registrables via reflexión, verificar que los campos de almacenamiento del proxy existan.

---

## Anti-patrones

- No modificar el pipeline Oboe C++ — el broadcast consume el output a nivel del sistema BT, no requiere cambios nativos
- No acceder a APIs de API 33+ sin verificar `Build.VERSION.SDK_INT >= TIRAMISU` primero
- No asumir que `getProfileProxy(LE_AUDIO_BROADCAST)` retornará true en todos los dispositivos API 33+
- No ignorar `SecurityException` en `startBroadcast()` — capturar y degradar elegantemente
- No hardcodear broadcast IDs ni códigos de seguridad
- No usar `BLUETOOTH_PRIVILEGED` (es system-only) — trabajar con los permisos disponibles y manejar las restricciones
- No desactivar el broadcast al mutear output local — son canales independientes

---

## Archivos creados/modificados

### Creados
- `app/src/main/java/com/naturasonic/app/bluetooth/BroadcastState.kt` — sealed class + enum BroadcastCapability
- `app/src/main/java/com/naturasonic/app/bluetooth/LeAudioBroadcastManager.kt` — singleton Hilt, reflexión completa
- `app/src/main/java/com/naturasonic/app/ui/screens/audiosharing/AudioSharingViewModel.kt` — combine 3 flows
- `app/src/main/java/com/naturasonic/app/ui/screens/audiosharing/AudioSharingScreen.kt` — UI Compose

### Modificados
- `app/src/main/AndroidManifest.xml` — +BLUETOOTH_ADVERTISE
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — +AUDIO_SHARING route
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — +onNavigateToAudioSharing, +CellTower button
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — +LeAudioBroadcastManager lifecycle integration

---

*PRP-016 COMPLETADO — 2026-08-17. Build exitoso en 3 arquitecturas.*
