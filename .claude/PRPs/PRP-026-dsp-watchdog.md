# PRP-026: Sistema de Monitoreo de Salud de Hilos DSP y Watchdog Nativo (DSP Thread Watchdog)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-24
> **Proyecto**: NaturaSonic

---

## Origen

> Planificación directa, sin brief previo. Complementa la instrumentación de rendimiento de PRP-009 (ATrace/chrono + PerformanceTracker + PerformanceScreen) con monitoreo activo de salud del hilo DSP, detección de stalls, conteo de xRuns, y reinicio transparente con backoff.

---

## Objetivo

Quiero que NaturaSonic monitoree la salud del hilo de audio DSP en tiempo real, detecte automáticamente cuando el pipeline Oboe deja de responder o acumula errores, y fuerce un reinicio transparente sin que yo tenga que cerrar y abrir la app. Además quiero ver las estadísticas de salud (xRuns, reinicios, latencia del último callback) en una pantalla dedicada y poder forzar un reset manual.

## Por Qué

| Problema | Solución |
|----------|----------|
| `onErrorAfterClose` hace `stop(); start()` sin backoff ni límite de reintentos — si el error es persistente, el engine entra en spin loop infinito | Watchdog C++ con backoff exponencial (100ms → 200ms → 400ms → ... → 5s), máximo 5 reintentos consecutivos, y notificación a Kotlin si agota reintentos |
| Kotlin no sabe si el hilo DSP está vivo — `OboeAudioEngine.isRunning` solo verifica que el handle C++ no sea null, no que `onAudioReady` esté disparándose | Heartbeat atómico (`lastCallbackTimestampNs_`) actualizado cada callback, polling desde Kotlin para detectar stalls (>500ms sin callback) |
| No hay conteo de xRuns (underruns/overruns) — Oboe los trackea internamente (`getXRunCount()`) pero NaturaSonic nunca los lee | Leer `getXRunCount()` del output stream y exponerlo vía JNI para monitoreo y alertas |
| Si el engine se reinicia en C++, Kotlin pierde la conexión con los observadores — las preferencias (EQ, modo, AEC, etc.) no se re-aplican al nuevo stream | `DspWatchdogManager` detecta el reinicio y dispara re-aplicación de todas las preferencias activas vía AudioService |

**Valor**: NaturaSonic es un PSAP que corre como servicio foreground durante horas. Un pipeline DSP sin watchdog es un riesgo de fallo silencioso — el usuario pierde amplificación auditiva sin aviso. Este PRP convierte un fallo silencioso en recuperación transparente.

## Qué

### Criterios de éxito
- [ ] C++ trackea `lastCallbackTimestampNs_` (steady_clock) actualizado en cada `onAudioReady`
- [ ] C++ trackea `xRunCount_` leído de `outputStream_->getXRunCount()` en cada callback
- [ ] `onErrorAfterClose` implementa backoff exponencial (100ms base, 2x factor, cap 5s, max 5 reintentos)
- [ ] C++ expone `WatchdogStats` via JNI: lastCallbackNs, xRunCount, restartCount, consecutiveErrors, streamState
- [ ] `DspWatchdogManager` Kotlin singleton Hilt detecta stall (frameCount no avanza en >500ms) y fuerza `stop()+start()` del engine
- [ ] `DspWatchdogManager` dispara re-aplicación de preferencias DSP tras cada reinicio detectado
- [ ] `DspWatchdogScreen` Compose muestra: indicador de salud (verde/amarillo/rojo), xRuns, reinicios, edad del último callback, botón de reset manual
- [ ] `dsp_watchdog_enabled` en DataStore (default: true)
- [ ] `./gradlew assembleDebug` compila sin errores

### Comportamiento esperado

El watchdog arranca automáticamente con AudioService (default: habilitado). En segundo plano, `DspWatchdogManager` hace polling cada 2 segundos de los `WatchdogStats` del engine C++ vía JNI. Si detecta que `lastCallbackTimestampNs` no ha avanzado en más de 500ms (stall), o que `consecutiveErrors` supera el umbral, ejecuta un ciclo `stop()+start()` del engine y re-aplica todas las preferencias activas. El usuario ve un indicador de salud en la pantalla de watchdog: verde (todo OK), amarillo (xRuns > 10 o reinicio reciente), rojo (stall activo o reintentos agotados). Un botón "Reiniciar motor de audio" permite reset manual. La pantalla muestra: xRun count acumulado, número de reinicios en la sesión, tiempo desde el último callback exitoso, y estado del stream.

### Casos borde

- **Stall transitorio (<500ms)**: polling cada 2s ignora stalls menores que el intervalo — comportamiento correcto, Oboe recupera solo
- **Engine nunca arrancó**: `lastCallbackTimestampNs_` es 0 → no contar como stall hasta que `running_` sea true
- **5 reintentos agotados en C++**: `onErrorAfterClose` deja de reintentar, `consecutiveErrors_` queda en 5+ → DspWatchdogManager en Kotlin detecta via polling y puede intentar un ciclo completo `destroy()+create()+start()` como último recurso
- **Reset manual durante stall**: el botón llama a `audioEngine.stop(); audioEngine.start()` vía DspWatchdogManager, que después re-aplica preferencias
- **Eco mode activo**: el polling del watchdog NO se throttlea — es crítico para la seguridad auditiva del usuario
- **Transición BT (output mute)**: `outputMuted_` no afecta al heartbeat — `onAudioReady` sigue disparándose con output muteado, el watchdog ve callbacks normales

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/oboe_engine.h` — `NaturaSonicEngine` clase, `LatencyStats` struct, `onErrorAfterClose` callback, `running_` atomic
- `app/src/main/cpp/oboe_engine.cpp` — `onAudioReady` (líneas 157-235), `onErrorAfterClose` (líneas 237-244), `getLatencyStats` (líneas 274-294)
- `app/src/main/cpp/native-lib.cpp` — JNI bridge, patrón `nativeGet*` con arrays float/int
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — wrapper Kotlin JNI, `isRunning` check
- `app/src/main/java/com/naturasonic/app/performance/PerformanceTracker.kt` — DspStats StateFlow, patrón de refresh
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — foreground service, observer coroutines, `startAudio()`/`stopAudio()`
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore 24 keys, patrón Flow + suspend setter

### Gotchas conocidas
- **`getXRunCount()` devuelve el conteo acumulado del stream**: si se recrea el stream (restart), el conteo se reinicia a 0. El watchdog debe acumular xRuns entre reinicios en su propio contador
- **`onErrorAfterClose` corre en el thread de audio de Oboe (o un thread interno de Oboe)**: el backoff con `sleep` dentro de `onErrorAfterClose` NO debe bloquear el audio thread — usar `std::this_thread::sleep_for` solo es aceptable porque el stream YA está cerrado en este punto
- **`steady_clock::now()` en C++ vs `System.nanoTime()` en Kotlin**: ambos usan `CLOCK_MONOTONIC` en Android, comparables sin offset
- **Re-aplicar preferencias tras reinicio**: AudioService tiene observers por coroutine (`combine().collect`). Un reinicio del engine no dispara `collect` porque los preferences no cambiaron — el watchdog debe forzar una re-aplicación explícita
- **No modificar Room schema**: el watchdog no persiste datos — las estadísticas son de sesión (in-memory). Solo `dsp_watchdog_enabled` va a DataStore

---

## Directiva de Stack heredada

> Derivada del proyecto existente NaturaSonic.

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- AGP 8.7.3, Kotlin 2.0.21, Oboe 1.9.0, todas las dependencias actuales congeladas
- Room v5 sin migraciones nuevas
- Pipeline Oboe C++ intacto (solo se instrumenta, no se modifica la cadena DSP)

### ADD
- Ninguna dependencia nueva. `std::chrono`, `std::atomic`, `std::thread` ya disponibles en C++17. Coroutines y Hilt ya configurados.

### REMOVE
- Nada

### CONFIG
- Nada

---

## Supuestos heredados

- [ ] Pipeline Oboe operativo con `onAudioReady` callback funcionando a 48kHz
- [ ] `NaturaSonicEngine` implementa `AudioStreamErrorCallback` con `onErrorAfterClose`
- [ ] Oboe streams exponen `getXRunCount()` (API pública de Oboe 1.9.0)
- [ ] `PerformanceTracker` singleton funcional con StateFlows de DspStats
- [ ] `AudioService` foreground service con patrón de observer coroutines
- [ ] `UserPreferences` DataStore con 24 keys existentes

### Supuestos adicionales (específicos de este PRP)
- [ ] `std::chrono::steady_clock` disponible y usa `CLOCK_MONOTONIC` en Android NDK (confirmado en NDK r26+)
- [ ] `oboe::AudioStream::getXRunCount()` retorna `int32_t` con conteo acumulado (documentación Oboe 1.9.0)

---

## Fuera de Alcance

- **Watchdog del input stream**: solo se monitorea el output stream (el input no tiene error callback configurado)
- **Persistencia histórica de xRuns/reinicios**: las estadísticas son de sesión, no se guardan en Room
- **Notificaciones push al usuario**: el watchdog reinicia transparentemente, no emite notificaciones (futuro PRP si se quiere alertar al usuario)
- **Auto-diagnóstico de causa raíz**: el watchdog detecta el síntoma (stall/error) y reinicia, no diagnostica por qué falló el stream

---

## Aprendizajes heredados de fases previas

**2026-08-13: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe**
- Los nuevos campos atómicos del watchdog (`lastCallbackTimestampNs_`, `xRunCount_`, etc.) son `std::atomic` independientes porque NO son parámetros DSP coherentes entre sí — son métricas de telemetría que se leen/escriben individualmente. El patrón EqSnapshot de double-buffer NO aplica aquí.

**2026-08-10: Consumidores pesados de audio deben integrarse a nivel C++ con thread dedicado**
- El watchdog NO es un consumidor de audio — no procesa samples. Su instrumentación C++ es inline en `onAudioReady` (un solo `store` atómico del timestamp, costo ~nanosegundos) y en `onErrorAfterClose` (path de error, no de rendimiento).

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agentico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: Instrumentación C++ + JNI bridge
- **Objetivo**: Agregar heartbeat atómico (`lastCallbackTimestampNs_`) en `onAudioReady`, acumulador de xRuns (`xRunCount_`), contador de reinicios (`restartCount_`), y refactorizar `onErrorAfterClose` con backoff exponencial + límite de reintentos. Crear struct `WatchdogStats` y exponerlo vía nueva función JNI `nativeGetWatchdogStats()`. Agregar `external fun` correspondiente en `OboeAudioEngine.kt`.
- **Validación**:
  - [ ] `onAudioReady` actualiza `lastCallbackTimestampNs_` con `steady_clock::now()`
  - [ ] `xRunCount_` acumula xRuns entre reinicios del stream
  - [ ] `onErrorAfterClose` espera con backoff antes de reintentar y se detiene tras 5 fallos consecutivos
  - [ ] JNI `nativeGetWatchdogStats` retorna array con las 5 métricas
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: DspWatchdogManager + UserPreferences + AudioService integration
- **Objetivo**: Crear `DspWatchdogManager` singleton Hilt que hace polling cada 2s de `WatchdogStats` vía JNI, detecta stalls (callback timestamp no avanza en >500ms con engine running), y fuerza reinicio del engine + re-aplicación de preferencias. Agregar `dsp_watchdog_enabled` a UserPreferences. Integrar el watchdog como observer coroutine en AudioService.
- **Validación**:
  - [ ] DspWatchdogManager detecta stalls simulados y dispara restart
  - [ ] Preferencias DSP se re-aplican automáticamente tras reinicio
  - [ ] Toggle `dsp_watchdog_enabled` habilita/deshabilita el polling
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: DspWatchdogScreen UI + ViewModel + NavGraph + Settings entry
- **Objetivo**: Crear `DspWatchdogScreen` Compose con indicador de salud (verde/amarillo/rojo), display de xRun count, restart count, edad del último callback, estado del stream, y botón "Reiniciar motor de audio". Crear `DspWatchdogViewModel` con DspWatchdogManager. Integrar ruta DSP_WATCHDOG en NavGraph y entrada "Motor de audio" en SettingsScreen.
- **Validación**:
  - [ ] DspWatchdogScreen renderiza indicador de salud correcto
  - [ ] Botón de reset manual reinicia el engine y actualiza stats
  - [ ] Navegación Settings → Motor de audio funciona
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 4: Validación final
- **Objetivo**: Sistema funcionando end-to-end con watchdog activo
- **Validación**:
  - [ ] Criterios de éxito cumplidos
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] `./gradlew lint` limpio en archivos modificados
  - [ ] Watchdog detecta stall y reinicia transparentemente
  - [ ] UI refleja estado en tiempo real

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

**2026-08-24: std::atomic independientes son correctos para métricas de telemetría watchdog**
- El patrón EqSnapshot double-buffer NO aplica a métricas del watchdog (lastCallbackNs, xRunCount, restartCount, consecutiveErrors). Cada una se lee/escribe individualmente sin coherencia entre sí — std::atomic con memory_order_relaxed es el patrón correcto.

**2026-08-24: SharedFlow(replay=0) para señalizar reinicios engine → AudioService**
- Tras un reinicio del engine, los observer coroutines de AudioService no re-disparan porque los preferences no cambiaron. SharedFlow sin replay señaliza el evento puntual de reinicio, y AudioService re-lee todos los valores actuales con `.first()` y los re-aplica. Patrón aplicable a cualquier futuro evento que necesite re-sincronización engine↔preferences.

**2026-08-24: xRunCount() se resetea al recrear stream — acumular antes de restart**
- `getXRunCount()` de Oboe retorna el conteo del stream actual. Al hacer `stop()+start()`, el stream se destruye y el conteo vuelve a 0. Se debe leer y acumular en `accumulatedXRuns_` atómico ANTES de cada restart en `onErrorAfterClose`.

---

## Anti-patrones

- No agregar mutex/lock en `onAudioReady` para el watchdog — solo `std::atomic` stores
- No bloquear el audio thread con sleeps para backoff — `onErrorAfterClose` se ejecuta después de que el stream ya cerró
- No persistir xRuns/reinicios en Room — son métricas de sesión volátiles
- No throttlear el watchdog en eco mode — es un mecanismo de seguridad, no de feature
- No modificar la cadena DSP (AEC → Processor → TransientLimiter → VolumeLimiter) — solo instrumentar
- No agregar dependencias nuevas ni modificar AGP/Gradle versions

---

*PRP COMPLETADO — 2026-08-24. Build exitoso sin errores (47 tareas, 3 arquitecturas NDK).*
