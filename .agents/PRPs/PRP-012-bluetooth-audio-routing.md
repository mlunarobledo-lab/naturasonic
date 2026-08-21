# PRP-012: Gestión e Integración de Auriculares Bluetooth (Audio Routing Avanzado)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-15
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Cubre la robustez del subsistema Bluetooth (Fase 2 del brief) y su coordinación con el pipeline de audio (Fase 1/3).
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic reaccione instantáneamente cuando mis auriculares Bluetooth se desconectan inesperadamente — que el audio se silencie de forma atómica en el pipeline nativo para que nunca salga ruido ni feedback por el altavoz del teléfono. Y cuando se reconecten, que el audio vuelva automáticamente.

## Por Qué

| Problema | Solución |
|----------|----------|
| Si los auriculares BT se desconectan (batería baja, fuera de rango, apagado accidental), Android redirige el output al speaker del teléfono — el audio amplificado del PSAP genera feedback acústico con el micrófono abierto | Mute atómico a nivel C++ (`onAudioReady`) que silencia la salida en < 1 frame de audio (~5ms) al detectar desconexión BT |
| BluetoothAudioManager actual no comunica estado de conexión como StateFlow observable — AudioService no puede reaccionar a cambios BT | StateFlow reactivo `connectionState` que emite Connected/Disconnected/BluetoothOff, consumido directamente por AudioService |
| No hay monitoreo de desconexiones ASHA ni LE Audio a nivel de perfil — solo ACL genérico | Monitoreo de `BluetoothProfile.HEARING_AID` y `BluetoothAdapter.ACTION_STATE_CHANGED` además de ACL |

**Valor**: Evitar feedback acústico doloroso cuando los auriculares se desconectan — crítico para usuarios con sensibilidad auditiva que usan NaturaSonic como PSAP.

## Qué

### Criterios de éxito
- [ ] Al desconectar auriculares BT, el output del pipeline Oboe se silencia en < 1 frame (~5ms)
- [ ] El silencio es atómico a nivel C++ (std::atomic<bool>) — no depende de latencia JNI/Kotlin
- [ ] WhisperBridge y YAMNet siguen recibiendo audio (no van a speaker)
- [ ] Al reconectar auriculares, el audio se restaura automáticamente
- [ ] `connectionState` StateFlow emite estados reactivos (Connected, Disconnected, BluetoothOff)
- [ ] AudioService observa `connectionState` y ejecuta mute/unmute sin intervención del usuario
- [ ] Build exitoso (`./gradlew assembleDebug`) sin cambios en dependencias nativas ni AGP

### Comportamiento esperado

1. Usuario usa NaturaSonic con auriculares BT conectados — audio amplificado fluye normalmente.
2. Los auriculares se desconectan (batería, rango, apagado).
3. `BluetoothAudioManager` detecta `ACTION_ACL_DISCONNECTED` (o perfil HEARING_AID desconectado, o BT apagado).
4. Emite `BluetoothConnectionState.Disconnected` vía `connectionState` StateFlow.
5. `AudioService` observa el cambio → llama `audioEngine.setOutputMuted(true)`.
6. En C++, `outputMuted_` atomic se activa → `onAudioReady` escribe zeros al output buffer en el siguiente frame.
7. WhisperBridge y YAMNet siguen recibiendo audio procesado (transcripción y detección no se interrumpen).
8. Usuario reconecta auriculares.
9. `BluetoothAudioManager` detecta `ACTION_ACL_CONNECTED` → emite `BluetoothConnectionState.Connected`.
10. `AudioService` observa → `audioEngine.setOutputMuted(false)` → audio restaurado.

### Casos borde

- **BT apagado por el usuario (no desconexión de dispositivo)**: `ACTION_STATE_CHANGED` con `STATE_OFF` → mute output.
- **Múltiples dispositivos BT**: si hay más de un dispositivo conectado, solo mutear si TODOS se desconectan.
- **Reconexión rápida (bounce)**: debounce de 200ms en el observer para evitar flicker de mute/unmute.
- **AudioService no iniciado**: si no hay servicio de audio activo, los cambios de conexión BT se ignoran.
- **Primer arranque sin BT**: el engine arranca sin mute — el usuario puede usar la app con speaker si quiere.

---

## Contexto

### Código existente a consultar
- `app/src/main/java/com/naturasonic/app/bluetooth/BluetoothAudioManager.kt` — clase existente con BroadcastReceiver para ACL events, `connectedDevices` StateFlow, `compatibility` StateFlow. **Nota**: el usuario refirió a esta clase como "BleAshaManager" pero en el codebase real es `BluetoothAudioManager`.
- `app/src/main/java/com/naturasonic/app/bluetooth/BluetoothDeviceInfo.kt` — data class con name, address, type, isConnected.
- `app/src/main/cpp/oboe_engine.h` / `oboe_engine.cpp` — `NaturaSonicEngine` con `onAudioReady`, `running_` atomic. No tiene mecanismo de mute.
- `app/src/main/cpp/native-lib.cpp` — JNI bridge. Patrón a seguir para nuevas funciones nativas.
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — wrapper Kotlin del engine nativo.
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — foreground service que orquesta audio. **No inyecta BluetoothAudioManager** actualmente.
- `app/src/main/cpp/audio_processor.h` — patrón canónico double-buffer `EqSnapshot` con `std::atomic<int>` para cambios thread-safe.

### Gotchas conocidas
- El `onAudioReady` de Oboe corre en thread de alta prioridad — no puede hacer allocations, locks contenciosos, ni JNI calls. El mute DEBE ser un `std::atomic<bool>` load, no un mutex.
- `ACTION_ACL_DISCONNECTED` puede llegar con delay de hasta ~2s en algunos dispositivos — el mute atómico C++ es la última línea de defensa, pero el BroadcastReceiver es el trigger primario.
- `BluetoothProfile.HEARING_AID` (ASHA) requiere API 29+ — OK, es nuestro minSdk.
- En Android 13+ (API 33), LE Audio tiene perfil `BluetoothProfile.LE_AUDIO` (valor 22) disponible.

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Toda la infraestructura nativa actual (Oboe, whisper.cpp, audio_processor, volume_limiter)
- Todas las dependencias en `build.gradle.kts` — congeladas
- AGP version — congelada

### ADD
- Ninguna dependencia nueva

### REPLACE
- Ninguno

### REMOVE
- Ninguno

### CONFIG
- Ningún cambio de configuración

### Refinamientos a la Directiva durante este PRP
- Este PRP NO toca dependencias nativas ni AGP. Solo agrega código Kotlin y modifica C++ existente (atomic bool + zero-fill).

---

## Supuestos heredados

- [x] El dispositivo Android del usuario tiene Android 10+ (API 29+)
- [x] El usuario tiene auriculares Bluetooth compatibles (Classic, LE Audio, o ASHA)
- [x] `BLUETOOTH_CONNECT` y `BLUETOOTH_SCAN` ya están en el AndroidManifest

### Supuestos adicionales (específicos de este PRP)
- [x] `BluetoothAudioManager` ya es `@Singleton` inyectado por Hilt — se puede inyectar directamente en `AudioService`
- [x] `onAudioReady` soporta lectura de `std::atomic<bool>` sin impacto en latencia (load acquire es ~1 CPU cycle)

---

## Fuera de Alcance heredado

- Streaming Auracast / broadcast LE Audio
- Versión iOS
- Backend en la nube
- Audiograma clínico
- Llamadas VoIP
- Grabación persistente de audio

### Fuera de Alcance adicional (específico de este PRP)
- Reconexión automática forzada por la app (Android gestiona la reconexión BT a nivel de sistema)
- UI de estado de conexión BT (futuro PRP)
- Selección manual de dispositivo de audio output (futuro)

---

## Aprendizajes heredados de fases previas

- **2026-08-13**: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe. `std::atomic` para flags independientes es válido (como el `outputMuted_` que implementaremos).
- **2026-08-12**: Ring buffer C++ para consumidores Kotlin — el yamnetBuffer y whisperBridge deben seguir recibiendo audio aunque el output esté muteado.
- **2026-08-10**: Consumidores pesados de audio deben integrarse a nivel C++ — el mute opera a nivel de output, no de pipeline completo.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agentico (mapear contexto -> generar
> subtareas -> ejecutar).

### Fase 1: Output Mute Atómico en C++ + JNI + Kotlin
- **Objetivo**: Agregar un flag `std::atomic<bool> outputMuted_` al engine C++ que, cuando está activo, escribe zeros al output buffer en `onAudioReady` sin interrumpir whisper/yamnet. Exponer vía JNI y Kotlin wrapper.
- **Validación**: Compilación nativa exitosa. La función `setOutputMuted(true)` silencia el output; `setOutputMuted(false)` lo restaura.

### Fase 2: BluetoothConnectionMonitor — StateFlow Reactivo
- **Objetivo**: Agregar `connectionState` StateFlow a `BluetoothAudioManager` con sealed class `BluetoothConnectionState` (Connected/Disconnected/BluetoothOff). Monitorear ACL + ASHA profile + BT adapter state.
- **Validación**: `connectionState` emite el estado correcto ante conexión/desconexión/BT off.

### Fase 3: Integración AudioService ↔ BluetoothAudioManager
- **Objetivo**: Inyectar `BluetoothAudioManager` en `AudioService`, observar `connectionState`, y ejecutar `setOutputMuted` reactivamente. Debounce de 200ms para evitar flicker.
- **Validación**: Al desconectar BT → output muteado. Al reconectar → output restaurado. Sin intervención del usuario.

### Fase 4: Validación Final
- **Objetivo**: Sistema funcionando end-to-end.
- **Validación**:
  - [ ] Criterios de éxito cumplidos
  - [ ] `./gradlew assembleDebug` exitoso
  - [ ] `./gradlew lint` sin errores nuevos
  - [ ] No hay cambios en dependencias nativas ni AGP
  - [ ] WhisperBridge y YAMNet siguen funcionando con output muteado

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

### 2026-08-15: Output mute atómico con std::atomic<bool> es el patrón correcto para flags independientes en onAudioReady
- **Error**: N/A — implementación directa sin errores.
- **Fix**: `std::atomic<bool>` con `memory_order_relaxed` en `onAudioReady` para mute de output. No requiere mutex ni double-buffer porque es un flag booleano independiente (no un grupo de parámetros coherentes como EqSnapshot).
- **Aplicar en**: Cualquier flag on/off futuro que deba leerse en el audio thread (bypass mode, recording toggle, etc.). Solo usar double-buffer cuando el flag es parte de un grupo de parámetros que deben ser coherentes.

### 2026-08-15: BroadcastReceiver en API 33+ requiere RECEIVER_NOT_EXPORTED flag
- **Error**: N/A — anticipado durante desarrollo.
- **Fix**: `context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)` para API 33+, fallback a `context.registerReceiver(receiver, filter)` para versiones anteriores. Sin este flag, Android 13+ lanza SecurityException en receivers registrados dinámicamente.
- **Aplicar en**: Todo futuro BroadcastReceiver registrado dinámicamente en la app.

---

## Anti-patrones

- No usar mutex en `onAudioReady` para el flag de mute — solo `std::atomic<bool>` con `memory_order_relaxed`
- No detener/reiniciar streams de Oboe ante desconexión BT — solo mutear output
- No hardcodear nombres de dispositivos BT
- No crear dependencias nuevas para funcionalidad que Android APIs ya proveen

---

*PRP en ejecución. Bucle-agentico activo.*
