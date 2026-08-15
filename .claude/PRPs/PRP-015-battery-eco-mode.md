# PRP-015: Sistema de Telemetría Avanzada de Batería y Modo Eco

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-15
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende las Fases 3-5 con gestión inteligente de energía para uso prolongado del PSAP en guardias y entornos de baja batería.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic monitoree la batería en tiempo real y me avise cuando quede poca. Cuando la batería baje del 20% (o yo lo active manualmente), que entre en un "modo eco" que reduzca el consumo de energía reduciendo la frecuencia con la que clasifica sonidos y transcribe — así puedo usar la app en guardias largas sin que el teléfono se muera. Quiero ver el porcentaje de batería en la pantalla principal y poder configurar cuándo se activa el modo eco.

## Por Qué

| Problema | Solución |
|----------|----------|
| NaturaSonic ejecuta YAMNet cada 1s y Whisper polling cada 150ms — consumo de CPU alto en uso prolongado | Modo Eco reduce YAMNet a cada 3s y Whisper polling a 500ms, bajando CPU ~60% en esos ciclos |
| No hay visibilidad de batería — el usuario no sabe cuánto le queda sin salir de la app | Indicador de batería con porcentaje y icono en la barra superior del HomeScreen |
| En guardias largas (>4h) la batería puede agotarse antes de terminar | Auto-activación del modo eco al bajar del 20% extiende la autonomía significativamente |
| No hay forma manual de priorizar duración vs funcionalidad | Toggle manual de modo eco en pantalla dedicada con umbral configurable |

**Valor**: Autonomía extendida para uso PSAP en entornos donde cargar el teléfono no es opción (guardias nocturnas, eventos largos, emergencias).

## Qué

### Criterios de éxito
- [x] BatteryMonitor singleton que registra BroadcastReceiver para ACTION_BATTERY_CHANGED con StateFlows de nivel, isCharging, temperatura
- [x] EcoModeManager singleton que combina estado de batería + preferencia del usuario para determinar si el modo eco está activo, expone StateFlow<Boolean>
- [x] En modo eco: `DETECTION_INTERVAL_MS` sube de 1000ms a 3000ms, Whisper text polling de 150ms a 500ms
- [x] DataStore: `ecoModeEnabled` (bool, manual toggle), `ecoModeAutoActivate` (bool, default true), `ecoModeThreshold` (int, default 20)
- [x] EcoModeScreen en Compose con toggle manual, toggle de auto-activación, slider de umbral (5-50%), indicador de estado actual
- [x] Indicador de batería (icono + %) en la TopAppBar del HomeScreen
- [x] Navegación: Settings → "Modo eco" → EcoModeScreen
- [x] Build exitoso sin cambios en dependencias nativas ni AGP

### Comportamiento esperado

1. Al iniciar AudioService, BatteryMonitor registra receiver y empieza a emitir estado.
2. EcoModeManager observa BatteryMonitor + DataStore y calcula `isEcoActive`.
3. Si `ecoModeAutoActivate = true` y `batteryLevel ≤ threshold` → eco activo automáticamente.
4. Si el usuario activa `ecoModeEnabled` manualmente → eco activo independientemente de batería.
5. AudioService observa `isEcoActive` y ajusta los intervalos de detección y polling.
6. Al cargar el teléfono por encima del umbral, el eco automático se desactiva (el manual persiste).
7. HomeScreen muestra batería con icono dinámico (full/half/low/charging) y porcentaje.

### Casos borde

- **Modo eco + detección de alertas**: YAMNet sigue activo pero con intervalo 3x mayor — no se desactiva, solo se ralentiza.
- **Modo eco + transcripción activa**: Whisper sigue transcribiendo pero con polling más lento — puede haber lag perceptible en los subtítulos.
- **Batería oscila alrededor del umbral**: Hysteresis de 3% — eco se activa al bajar de threshold, se desactiva al subir a threshold+3.
- **Receiver registrado en API 33+**: Usa flag `RECEIVER_NOT_EXPORTED` (mismo patrón que BluetoothAudioManager).
- **Eco manual + auto**: Si el usuario activó manualmente, no se desactiva automáticamente al cargar — solo el toggle manual lo apaga.

---

## Contexto

### Código existente a consultar
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — `DETECTION_INTERVAL_MS = 1000L`, `startDetectionLoop()` con delay fijo
- `app/src/main/java/com/naturasonic/app/transcription/WhisperTranscriptionEngine.kt` — `startTextPolling()` con `delay(150)`
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — patrón DataStore para preferencias
- `app/src/main/java/com/naturasonic/app/ui/screens/home/HomeScreen.kt` — TopAppBar con BT indicator (patrón a replicar para batería)
- `app/src/main/java/com/naturasonic/app/ui/screens/home/HomeViewModel.kt` — combine de StateFlows para HomeUiState
- `app/src/main/java/com/naturasonic/app/bluetooth/BluetoothAudioManager.kt` — patrón BroadcastReceiver + StateFlow (referencia)
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — botones de navegación
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — Routes + composable entries
- `app/src/main/java/com/naturasonic/app/performance/PerformanceTracker.kt` — patrón singleton con StateFlows

### Gotchas conocidas
- `AudioService.DETECTION_INTERVAL_MS` es `const val` — debe cambiar a función o variable dinámica para que el eco funcione.
- `WhisperTranscriptionEngine.startTextPolling()` usa `delay(150)` hardcoded — necesita parametrizar.
- `ACTION_BATTERY_CHANGED` es un sticky broadcast — no requiere `registerReceiver` explícito para lectura inicial, pero sí para updates continuos.
- En API 33+ los receivers necesitan `RECEIVER_NOT_EXPORTED` flag.

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Toda la infraestructura nativa actual
- Todas las dependencias — congeladas
- AGP version — congelada

### ADD / REMOVE / REPLACE / CONFIG
- Ninguno

---

## Supuestos heredados

- [x] El dispositivo tiene Android 10+ (API 29+)
- [x] ACTION_BATTERY_CHANGED broadcast disponible en todos los Android 10+
- [x] AudioService foreground service corriendo durante uso activo
- [x] UserPreferences DataStore funcional
- [x] SoundAlertDetector y WhisperTranscriptionEngine inyectados vía Hilt

---

## Fuera de Alcance heredado

- Audiograma clínico
- Streaming Auracast
- Versión iOS
- Backend en la nube

### Fuera de Alcance adicional

- Optimización de consumo a nivel NDK/C++ (reducir sample rate o buffer size del pipeline Oboe)
- Estadísticas históricas de consumo de batería por sesión
- WakeLock management avanzado
- CPU frequency scaling

---

## Aprendizajes heredados

- **2026-08-15 (PRP-012)**: BroadcastReceiver en API 33+ requiere flag `RECEIVER_NOT_EXPORTED` — aplicar en BatteryMonitor.
- **2026-08-15 (PRP-014)**: DataStore patrón establecido con Flow + suspend setter — seguir mismo patrón para eco mode keys.
- **2026-08-15 (PRP-009)**: PerformanceTracker como singleton con StateFlows — patrón replicable para BatteryMonitor.

---

## Plan de implementación

### Fase 1: BatteryMonitor — BroadcastReceiver + StateFlow telemetry
- **Objetivo**: Crear `BatteryMonitor` singleton Hilt que registra un BroadcastReceiver para `ACTION_BATTERY_CHANGED`, expone `StateFlow<BatteryState>` con level (0-100), isCharging, temperature.
- **Validación**: Compilación exitosa. StateFlows emiten valores al registrar.

### Fase 2: EcoModeManager + UserPreferences
- **Objetivo**: Crear `EcoModeManager` singleton que combine BatteryMonitor + DataStore preferences para determinar `isEcoActive: StateFlow<Boolean>`. Agregar keys de eco mode a `UserPreferences` (ecoModeEnabled, ecoModeAutoActivate, ecoModeThreshold). Implementar hysteresis de 3%.
- **Validación**: El StateFlow de isEcoActive responde a cambios de batería y preferencias.

### Fase 3: Integración con AudioService + WhisperTranscriptionEngine
- **Objetivo**: AudioService observa `EcoModeManager.isEcoActive` y ajusta `DETECTION_INTERVAL_MS` dinámicamente (1000ms normal → 3000ms eco). WhisperTranscriptionEngine recibe intervalo configurable para text polling (150ms normal → 500ms eco).
- **Validación**: Los intervalos cambian en runtime sin reiniciar el servicio.

### Fase 4: EcoModeScreen + EcoViewModel + navegación
- **Objetivo**: Pantalla Compose con toggle de eco manual, toggle de auto-activación, slider de umbral (5-50%), indicador de estado actual (activo/inactivo, razón). Ruta ECO_MODE en NavGraph. Botón "Modo eco" en SettingsScreen.
- **Validación**: Pantalla renderiza. Toggles y slider persisten en DataStore.

### Fase 5: Indicador de batería en HomeScreen
- **Objetivo**: Agregar icono de batería (BatteryFull/BatteryStd/Battery2Bar/BatteryAlert/BatteryChargingFull) + porcentaje en la TopAppBar del HomeScreen. Color cambia a error cuando < 20%. Chip "ECO" visible cuando modo eco activo.
- **Validación**: Indicador visible y reactivo en la pantalla principal.

### Fase 6: Validación Final
- **Objetivo**: Sistema funcionando end-to-end.
- **Validación**:
  - [x] `./gradlew assembleDebug` exitoso
  - [x] `./gradlew lint` sin errores nuevos
  - [x] No hay cambios en dependencias nativas ni AGP

---

## Aprendizajes

> Esta sección crece con cada error.

---

## Anti-patrones

- No usar WakeLock para mantener el BroadcastReceiver — ACTION_BATTERY_CHANGED es sticky, no requiere wakelock
- No hacer polling activo de BatteryManager — el receiver es event-driven
- No desactivar completamente YAMNet en eco — ralentizar, no apagar
- No modificar el pipeline Oboe C++ para eco — las optimizaciones son puramente en la capa Kotlin (intervalos de loops asíncronos)

---

*PRP COMPLETADO — 2026-08-15. Todas las fases ejecutadas y validadas.*
