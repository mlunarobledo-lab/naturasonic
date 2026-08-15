# PRP-014: Panel de Control de Cancelación de Ruido Activa y Noise Gate Adaptativo

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-15
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la Fase 3 (Motor PSAP) con control de ruido adaptativo basado en RMS y selección de modos desde la UI.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic me deje elegir cómo manejar el ruido de fondo: apagado (todo pasa), enfoque de voz (atenúa el ruido pero deja pasar las voces), o cancelación agresiva (silencia casi todo lo que no sea voz). Que sea un selector simple en la app y que el cambio se aplique instantáneamente al audio.

## Por Qué

| Problema | Solución |
|----------|----------|
| El noise gate actual es binario (on/off) con umbral fijo — no se adapta al nivel de ruido del entorno ni distingue entre voz y ruido | Noise gate adaptativo con estimación de piso de ruido por RMS y modos diferenciados (Enfoque de Voz vs Cancelación Agresiva) |
| No hay UI para controlar el nivel de cancelación de ruido — solo un toggle en settings mezclado con otros controles | Pantalla dedicada AncControlScreen con selector de 3 modos y descripciones claras |
| El umbral fijo (0.002f) funciona mal en entornos ruidosos: o deja pasar todo o corta señal útil | Piso de ruido adaptativo con EMA que se ajusta continuamente al entorno real |

**Valor**: Control granular de ruido que se adapta al entorno — la diferencia entre un PSAP que siempre suena igual y uno que prioriza lo que importa (voz humana).

## Qué

### Criterios de éxito
- [x] Noise gate adaptativo en C++ con 3 modos: OFF (0), VOICE_FOCUS (1), AGGRESSIVE (2)
- [x] Estimación de piso de ruido por EMA (Exponential Moving Average) de RMS en períodos de silencio
- [x] Parámetros del gate (attack, release, atenuación mínima, ratio de voz) diferenciados por modo
- [x] Propagación lock-free al hilo de audio via EqSnapshot double-buffer (patrón canónico)
- [x] JNI bridge `setNoiseGateMode(int)` + actualización de `applyProfile` (bool → int)
- [x] AncControlScreen en Compose con selector de 3 modos persistido en DataStore
- [x] Build exitoso sin cambios en dependencias nativas ni AGP
- [x] Callers existentes actualizados para mapear bool → int sin regresiones

### Comportamiento esperado

1. Usuario navega a Settings → "Control de ruido".
2. Pantalla con 3 tarjetas: Desactivado, Enfoque de Voz, Cancelación Agresiva.
3. Al seleccionar un modo, se aplica instantáneamente vía `setNoiseGateMode()`.
4. El modo persiste en DataStore y se restaura al reiniciar la app.
5. En "Enfoque de Voz": el ruido de fondo se atenúa suavemente, las voces pasan completas.
6. En "Cancelación Agresiva": todo lo que no sea voz se reduce casi a silencio.

### Casos borde

- **Cambio de modo durante audio activo**: el EqSnapshot se swapea atómicamente — transición sin glitch.
- **Entorno muy silencioso**: el piso de ruido adaptativo baja, evitando gating innecesario.
- **Solo ruido, sin voz**: en modo agresivo el audio se atenúa fuertemente — comportamiento esperado.
- **`applyProfile` con modo legacy**: callers que pasan `noiseSuppressionEnabled: Boolean` se mapean a 0/1.

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/audio_processor.h` — EqSnapshot double-buffer, noise gate actual con umbral fijo (0.002f)
- `app/src/main/cpp/audio_processor.cpp` — `applyNoiseGate()` actual (envelope follower simple), `process()` dispatch
- `app/src/main/cpp/oboe_engine.h/cpp` — forward de `applyProfile` y `setNoiseSuppressionEnabled`
- `app/src/main/cpp/native-lib.cpp` — JNI bridge (`nativeApplyProfile` toma `jboolean`)
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — `applyProfile(bands, amp, noiseSuppression: Boolean)`
- `app/src/main/java/com/naturasonic/app/audio/AudioModeManager.kt` — `ModeConfig.nsEnabled` (bool) → `applyProfile`
- `app/src/main/java/com/naturasonic/app/data/local/entity/AudioProfile.kt` — `noiseSuppressionEnabled: Boolean` (Room, NO cambiar schema)
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore para settings

### Gotchas conocidas
- `applyProfile` en C++ escribe directo al snapshot inactivo sin copiar — todos los campos se sobreescriben. El nuevo campo `noiseGateMode` debe incluirse en el write.
- `BiquadState` y `noiseGateEnvelope_` son estado continuo del audio — NO viven en EqSnapshot (no se duplican). Los nuevos estados del noise gate adaptativo (`ngSignalLevel_`, `ngNoiseFloor_`, `ngGateGain_`) siguen el mismo patrón.
- La Room entity `AudioProfile.noiseSuppressionEnabled` NO se modifica. El mapeo bool→int ocurre en la capa Kotlin al llamar `applyProfile`.

### Algoritmo del Noise Gate Adaptativo

**Estimación de piso de ruido** (EMA):
- `signalLevel` = EMA rápida del valor absoluto de la señal (τ ≈ 0.999)
- `noiseFloor` = EMA lenta actualizada SOLO cuando `signalLevel < noiseFloor × 2.0` (periodo "silencioso")
- Floor mínimo = 1e-7 (previene división por cero)

**Decisión de gate** (por sample):
- Si `signalLevel > noiseFloor × voiceRatio` → voz detectada → target gain = 1.0
- Si no → target gain = `minAttenuation`

**Suavizado** (attack/release):
- Gate abriendo: `gain += (target - gain) × attackCoeff` (rápido)
- Gate cerrando: `gain *= releaseCoeff` (lento), clamped a `minAttenuation`

**Parámetros por modo:**

| Parámetro | VOICE_FOCUS | AGGRESSIVE |
|-----------|-------------|------------|
| voiceRatio | 4.0 | 2.5 |
| minAttenuation | 0.15 | 0.02 |
| attackCoeff | 0.02 | 0.05 |
| releaseCoeff | 0.9995 | 0.999 |
| noiseFloorAdapt | 0.001 | 0.002 |

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
- [x] AudioProcessor C++ con EqSnapshot double-buffer funcional
- [x] `applyProfile` JNI bridge operativo
- [x] UserPreferences DataStore funcional

---

## Fuera de Alcance heredado

- Audiograma clínico
- Streaming Auracast
- Versión iOS
- Backend en la nube

### Fuera de Alcance adicional

- ANC óptico/hardware (cancelación por micrófono invertido — requiere hardware)
- Análisis de frecuencia (FFT) para detección de voz — RMS es suficiente para un PSAP
- Perfiles de ruido por entorno (auto-selección del modo según GPS/actividad)

---

## Aprendizajes heredados

- **2026-08-13**: Double-buffer copy-modify-swap es el patrón canónico para parámetros DSP. Nuevos campos del gate adaptativo van al EqSnapshot.
- **2026-08-15**: `std::atomic<bool>` para flags independientes, pero parámetros coherentes entre sí van al EqSnapshot. El `noiseGateMode` es coherente con el resto del perfil → EqSnapshot.
- **2026-08-03**: Algoritmos PSAP se implementan directamente en C++ sobre Oboe.

---

## Plan de implementación

### Fase 1: C++ — Noise Gate Adaptativo con RMS VAD y 3 modos
- **Objetivo**: Reemplazar `bool noiseSuppression` por `int noiseGateMode` en EqSnapshot. Implementar `applyAdaptiveNoiseGate()` con estimación de piso de ruido por EMA, detección de voz por ratio RMS, y suavizado attack/release. Parámetros diferenciados por modo.
- **Validación**: Compilación exitosa. El campo int integra al double-buffer sin romper copy-modify-swap.

### Fase 2: JNI bridge + Kotlin wrapper + NaturaSonicEngine
- **Objetivo**: Actualizar `nativeApplyProfile` (jboolean → jint). Agregar `nativeSetNoiseGateMode`. Actualizar OboeAudioEngine.kt y NaturaSonicEngine.
- **Validación**: Las funciones JNI se resuelven correctamente.

### Fase 3: Actualizar callers existentes
- **Objetivo**: AudioModeManager, AudiogramViewModel, SettingsViewModel — mapear `Boolean → Int` (0/1) sin cambiar Room schema.
- **Validación**: No hay regresiones en el flujo de perfiles existente.

### Fase 4: AncControlScreen + AncViewModel + UserPreferences
- **Objetivo**: Pantalla Compose con selector de 3 modos, persistencia en DataStore, aplicación reactiva al engine.
- **Validación**: La pantalla renderiza. Cambiar modo actualiza el audio instantáneamente.

### Fase 5: Navegación + integración con SettingsScreen
- **Objetivo**: Ruta ANC_CONTROL en NavGraph. Botón "Control de ruido" en Settings.
- **Validación**: Navegación funciona ida y vuelta.

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

- No usar mutex en `applyAdaptiveNoiseGate` — se ejecuta en el audio thread, solo lee del snapshot activo
- No modificar `AudioProfile` Room entity — el mapeo bool→int ocurre en Kotlin
- No usar FFT para detección de voz — RMS es suficiente y mucho más barato computacionalmente
- No duplicar estado del gate en el EqSnapshot — `ngSignalLevel_`, `ngNoiseFloor_`, `ngGateGain_` son estado continuo como `BiquadState`

---

*PRP COMPLETADO — 2026-08-15. Todas las fases ejecutadas y validadas.*
