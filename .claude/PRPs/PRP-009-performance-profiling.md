# PRP-009: Monitoreo de Rendimiento — Instrumentación de Latencia y Perfilado

> **Estado**: COMPLETADO
> **Fecha inicio**: 2026-08-14
> **Fecha cierre**: 2026-08-14
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Instrumenta el pipeline de audio nativo y la cadena de detección YAMNet para medir latencia end-to-end, consumo de memoria y proveer trazas para Perfetto. No modifica la lógica de procesamiento — solo observa.

---

## Objetivo

Quiero poder medir cuánto tarda el audio desde que Oboe lo captura hasta que el ecualizador y limitador lo procesan, y cuánto tarda desde la captura hasta que YAMNet emite una alerta visual. Necesito marcas de tiempo de alta precisión en C++ y Kotlin, trazas compatibles con Perfetto para perfilar en dispositivo real, y una pantalla de diagnóstico para ver métricas en vivo sin necesidad de conectar al PC.

## Por Qué

| Problema | Solución |
|----------|----------|
| No hay forma de medir la latencia del pipeline DSP (AudioProcessor + VolumeLimiter) en dispositivo real | Timestamps `std::chrono::steady_clock` en `onAudioReady` + estadísticas acumuladas |
| No hay trazas para Perfetto — no se puede perfilar con System Tracing | `ATrace_beginSection`/`ATrace_endSection` en los puntos clave del callback de audio |
| No se sabe cuánto tarda la detección YAMNet end-to-end (JNI buffer copy → resample → classify) | `System.nanoTime()` en `startDetectionLoop` y `processAudioBuffer` |
| No hay visibilidad del footprint de memoria nativo en runtime | `Debug.getNativeHeapAllocatedSize()` expuesto en pantalla de métricas |

**Valor**: Datos objetivos para optimizar latencia y detectar regresiones de rendimiento antes de publicar en Play Store.

## Qué

### Criterios de éxito
- [x] `ATrace` sections en `onAudioReady` visibles en Perfetto System Trace
- [x] Estadísticas de latencia DSP (min/max/avg) acumuladas en C++ y expuestas vía JNI
- [x] Timestamps de detección YAMNet (JNI copy + resample + classify) medidos en Kotlin
- [x] Pantalla de métricas accesible desde Settings que muestre stats en vivo
- [x] Footprint de memoria nativa visible en la pantalla de métricas
- [x] `./gradlew assembleDebug` compila sin errores
- [x] No se modifica la versión de AGP ni se agregan dependencias nuevas
- [x] La instrumentación NO afecta el rendimiento del audio thread (zero-alloc en hot path)

---

## Contexto

### Código existente a consultar

- `app/src/main/cpp/oboe_engine.cpp` — `onAudioReady`: callback de audio que ejecuta `processor_.process()` + `limiter_.process()` + buffer writes. Este es el hot path donde insertar ATrace + chrono.
- `app/src/main/cpp/oboe_engine.h` — `NaturaSonicEngine` con miembros `processor_`, `limiter_`, buffers. Agregar struct de stats aquí.
- `app/src/main/cpp/audio_processor.cpp` — `process()` que lee snapshot atómico y aplica amplification + noise gate + EQ biquad.
- `app/src/main/cpp/native-lib.cpp` — JNI bridge. Agregar `nativeGetLatencyStats` aquí.
- `app/src/main/cpp/CMakeLists.txt` — Ya linkea `android` y `log`. `ATrace` está en `<android/trace.h>` (parte del NDK, no requiere dependencia extra).
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — `startDetectionLoop()` con polling cada 1000ms de `getYamnetAudioBuffer()`.
- `app/src/main/java/com/naturasonic/app/detection/SoundAlertDetector.kt` — `processAudioBuffer()` con `classifier.classify()`.
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — Kotlin wrapper de JNI. Agregar `getLatencyStats()` aquí.
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — Agregar navegación a pantalla de métricas.
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — Routes + composable.

### Gotchas conocidas

- **Zero-alloc en `onAudioReady`**: El callback de audio es real-time. No se puede hacer `new`, `malloc`, `std::string`, ni locks largos. `ATrace_beginSection` es lock-free (atómico sobre mmap). `std::chrono::steady_clock::now()` es vdso en ARM — zero syscall.
- **ATrace overhead**: `ATrace_isEnabled()` es barato (~1 instrucción). Las secciones solo se registran cuando System Tracing está activo; cuando no, el overhead es ~0.
- **`Debug.getNativeHeapAllocatedSize()`**: Disponible sin permisos extra. Retorna bytes del heap nativo (mallinfo).
- **AGP y dependencias congelados**: NO se agregan dependencias. `<android/trace.h>` ya está en el NDK.

### Modelo de datos

No hay cambios al schema de Room ni a ningún entity. Las métricas viven en memoria (C++ struct + Kotlin StateFlow).

---

## Directiva de Stack heredada

> Compatibilidad con Praxis: **REPLACE** (proyecto Android nativo).

### KEEP
- Pipeline C++ congelado (solo se agregan timestamps, no se modifica lógica)
- Room/DataStore congelados
- CMakeLists.txt: no se agregan dependencias

### ADD / REPLACE / REMOVE / CONFIG
- Ninguno

---

## Supuestos heredados

- [x] `<android/trace.h>` disponible en NDK con `target_link_libraries(... android)` (ya linkeado)
- [x] `std::chrono::steady_clock` disponible con C++17 (ya configurado en CMake)
- [x] `Debug.getNativeHeapAllocatedSize()` disponible desde API 1 (min SDK 29)
- [x] `onAudioReady` es el callback del output stream (verificado en oboe_engine.cpp)

---

## Fuera de Alcance

- Profiling automatizado con benchmarking framework (macrobenchmark/microbenchmark)
- Battery Historian analysis (requiere `adb bugreport` — es un procedimiento externo, no código)
- Thermal throttling detection (requiere `PowerManager.getThermalStatus()` API 29+ — candidato para PRP futuro)
- Perfetto SDK embedido (usamos ATrace nativo, que Perfetto ya consume)
- Guardar historial de métricas en Room

---

## Plan de implementación

### Fase 1: Instrumentación C++ — ATrace + latency counters ✅
- **Objetivo**: Insertar `ATrace_beginSection`/`ATrace_endSection` en `onAudioReady` para Perfetto. Acumular stats de latencia (min/max/avg de los últimos N frames) en un struct lock-free. Exponer via JNI.
- **Archivos tocados**: `oboe_engine.h`, `oboe_engine.cpp`, `native-lib.cpp`, `OboeAudioEngine.kt`
- **Validación**:
  - [x] ATrace section `"NaturaSonic::DSP"` insertada en `onAudioReady`
  - [x] `LatencyStats` struct con min/max/avg/count en array preasignado de 256 frames
  - [x] JNI `nativeGetLatencyStats` retorna float[4] al Kotlin
  - [x] `./gradlew assembleDebug` compila (3 ABIs)

### Fase 2: Instrumentación Kotlin — PerformanceTracker + detección timing ✅
- **Objetivo**: Crear `PerformanceTracker` singleton que acumule métricas de detección YAMNet y memoria. Instrumentar `startDetectionLoop` y `processAudioBuffer`.
- **Archivos tocados**: nuevo `PerformanceTracker.kt`, `AudioService.kt`, `SoundAlertDetector.kt`
- **Validación**:
  - [x] Timing de clasificación YAMNet medido por ciclo (`ProcessTiming` data class)
  - [x] Latencia JNI buffer copy medida con `System.nanoTime()`
  - [x] Memory stats (native heap, Java heap) via `Debug.getNativeHeapAllocatedSize()` + `Runtime`
  - [x] DSP stats y memory stats refreshed cada ciclo de detección (1s)
  - [x] `./gradlew assembleDebug` compila

### Fase 3: Pantalla de métricas + navegación ✅
- **Objetivo**: `PerformanceScreen` accesible desde Settings con métricas en vivo. Auto-refresh periódico.
- **Archivos tocados**: nuevo `PerformanceScreen.kt`, nuevo `PerformanceViewModel.kt`, `NavGraph.kt`, `SettingsScreen.kt`
- **Validación**:
  - [x] Pantalla muestra latencia DSP (min/max/avg µs), latencia detección (JNI/resample/classify ms), memoria nativa/Java MB
  - [x] Métricas actualizadas cada ciclo de detección (1s) vía StateFlow
  - [x] Navegación Settings → Rendimiento → Back funcional
  - [x] `./gradlew assembleDebug` compila

### Fase 4: Validación end-to-end + cierre ✅
- **Objetivo**: Build limpio, lint, criterios de éxito cumplidos.
- **Validación**:
  - [x] `./gradlew assembleDebug` exitoso (BUILD SUCCESSFUL, 3 ABIs)
  - [x] `./gradlew lint` sin errores
  - [x] Todos los 8 criterios de éxito cumplidos

---

## Resumen de archivos creados/modificados

### Archivos nuevos (4)
- `app/src/main/java/com/naturasonic/app/performance/PerformanceTracker.kt` — Singleton con StateFlows de DspStats, DetectionStats, MemoryStats
- `app/src/main/java/com/naturasonic/app/ui/screens/performance/PerformanceViewModel.kt` — HiltViewModel que expone StateFlows del tracker
- `app/src/main/java/com/naturasonic/app/ui/screens/performance/PerformanceScreen.kt` — Compose screen con 3 cards de métricas

### Archivos modificados (6)
- `app/src/main/cpp/oboe_engine.h` — `LatencyStats` struct, `latencyHistoryUs_[256]`, `totalFrameCount_` atomic
- `app/src/main/cpp/oboe_engine.cpp` — ATrace + chrono en `onAudioReady`, `getLatencyStats()` implementation
- `app/src/main/cpp/native-lib.cpp` — `nativeGetLatencyStats` JNI function
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — `getLatencyStats()` wrapper + external decl
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — Injection de PerformanceTracker, timing en detection loop
- `app/src/main/java/com/naturasonic/app/detection/SoundAlertDetector.kt` — `ProcessTiming` data class, timing en `processAudio48kHz`
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — Route PERFORMANCE + composable
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — Botón "Rendimiento" + param `onNavigateToPerformance`

---

## Aprendizajes

1. **`ATrace_beginSection` no requiere cambios en CMakeLists**: `<android/trace.h>` ya está disponible con el `android` target library que ya estaba linkeado. Zero configuración.
2. **Array preasignado para latency stats es suficiente**: No se necesita `std::mutex` ni allocations. Un array fijo de 256 floats con un write position entero, leído desde otro thread, es safe en la práctica porque los reads son informativos (no críticos si leen un valor parcial de un float).
3. **`ProcessTiming` data class como return type es limpio**: En lugar de inyectar PerformanceTracker en SoundAlertDetector (más acoplamiento), hacer que `processAudio48kHz` retorne sus propios timings y que AudioService los reporte al tracker mantiene la dependencia unidireccional.
4. **Refresh de métricas piggybacked en el detection loop**: En vez de crear un timer separado para refrescar stats, aprovechar el loop de detección que ya corre cada 1s para hacer `refreshDspStats()` + `refreshMemoryStats()` evita un coroutine adicional.

---

## Anti-patrones

- No hacer allocations en `onAudioReady` — zero-alloc o el audio glitchea
- No usar `std::mutex` para las stats de latencia — usar atomics o arrays preasignados
- No agregar dependencias de profiling (no Perfetto SDK, no macrobenchmark) — ATrace nativo es suficiente
- No persistir métricas — son efímeras, viven en memoria

---

*PRP auto-flip PENDIENTE → APROBADO → EN PROGRESO → COMPLETADO.*
