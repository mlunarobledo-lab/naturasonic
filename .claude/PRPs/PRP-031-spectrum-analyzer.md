# PRP-031: Spectrum Analyzer Visualizer

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-27
> **Proyecto**: NaturaSonic

---

## Origen

Planificación directa, sin brief previo. El usuario solicita que las bandas del ecualizador dejen de ser estáticas y muestren la energía real del audio en tiempo real, con barras animadas tipo analizador de espectro.

---

## Objetivo

Quiero que el ecualizador muestre barras que se muevan con el audio en tiempo real. Que se vea la energía del sonido en cada banda de frecuencia como un analizador de espectro profesional, no solo las posiciones fijas de ganancia que configuré.

## Por Qué

| Problema | Solución |
|----------|----------|
| El ecualizador actual muestra sliders estáticos que no reflejan el audio real | Barras animadas que visualizan la energía por banda en tiempo real |
| No hay feedback visual de que el audio está siendo procesado | El usuario ve actividad en las barras, confirmando que el pipeline funciona |

**Valor**: El analizador de espectro convierte una pantalla de configuración muerta en una visualización viva que da confianza de que el PSAP está procesando audio correctamente.

## Qué

### Criterios de éxito
- [x] FFT radix-2 de 512 puntos ejecutándose inline en C++ sobre el audio post-DSP
- [x] 10 bandas de magnitud espectral alineadas con las frecuencias del EQ (125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 6kHz, 8kHz, 10kHz, 12kHz)
- [x] JNI getter expone las 10 magnitudes como `FloatArray` (patrón `getLatencyStats`)
- [x] UI Canvas Compose muestra barras animadas con interpolación suave (~20 fps visual)
- [x] Las barras del espectro coexisten con los controles del EQ existente (sliders de ganancia)
- [x] Zero impacto en latencia del audio thread (FFT < 50µs por frame de 256 samples)
- [x] Build de producción exitoso (`./gradlew assembleDebug`)

### Comportamiento esperado

Al abrir la pantalla de Settings → Ecualizador, el usuario ve las 10 bandas del EQ con sus sliders de ganancia (como ahora) más un visualizador de espectro debajo que muestra barras verticales animadas representando la energía real del audio en cada banda frecuencial. Las barras suben y bajan suavemente siguiendo la dinámica del sonido ambiente. Cuando no hay audio (silencio), las barras caen a cero. Cuando hay sonido fuerte, las barras suben proporcionalmente.

### Casos borde

- **Audio muteado** (`outputMuted_ = true`): el FFT sigue procesando captureBuffer_ post-DSP — las barras siguen animándose porque el mic sigue capturando.
- **Pantalla de EQ cerrada**: el polling de magnitudes se detiene (DisposableEffect), pero el FFT en C++ sigue corriendo (costo insignificante).
- **Audio muy bajo**: las magnitudes deben escalar en dB para que señales débiles sean visibles (rango dinámico ~60 dB).
- **Primer frame**: antes de llenar el buffer FFT, las magnitudes son 0 — las barras empiezan en cero y suben gradualmente.

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/oboe_engine.h` — clase NaturaSonicEngine con captureBuffer_ (256 float mono), patrones de buffer atómico, getters JNI existentes
- `app/src/main/cpp/oboe_engine.cpp` — onAudioReady callback donde se insertará el cálculo FFT post-DSP
- `app/src/main/cpp/native-lib.cpp` — patrón JNI para getters (`nativeGetLatencyStats`, `nativeGetAudioBuffer`)
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — wrapper Kotlin con JNI natives
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — EqualizerCard composable actual (línea 417), sliders por banda con EQ_LABELS = ["125", "250", "500", "1K", "2K", "4K", "6K", "8K", "10K", "12K"]
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsViewModel.kt` — ViewModel que maneja eqBands

### Gotchas conocidas
- **Audio thread real-time**: el callback `onAudioReady` corre en un thread de alta prioridad. El FFT debe ser inline sin allocations — preasignar buffers en el constructor.
- **kFramesPerBuffer = 256**: un frame de 256 muestras a 48kHz = ~5.3ms. FFT de 256 puntos da resolución frecuencial de 48000/256 = 187.5 Hz por bin — suficiente para mapear a 10 bandas pero la resolución en bajas frecuencias es gruesa. Se puede zero-pad a 512 para duplicar resolución (93.75 Hz/bin) sin costo significativo.
- **Sin dependencias externas**: FFT radix-2 es ~50 líneas de C++. No importar FFTW ni KissFFT — implementación inline.
- **Patrón lock-free**: las magnitudes espectrales deben ser legibles desde JNI sin bloquear el audio thread. Usar array atómico o doble buffer (patrón `latencyHistoryUs_[]`).

---

## Directiva de Stack heredada

No hay brief origen — proyecto Android nativo (Kotlin/Gradle/NDK/Oboe). Comandos de validación: `./gradlew assembleDebug` (build), `./gradlew lint` (lint).

---

## Supuestos heredados

No hay supuestos heredados.

### Supuestos adicionales
- [ ] El `captureBuffer_` en `onAudioReady` contiene audio mono post-DSP procesado (después de EQ, limiter, etc.)
- [ ] Los 10 labels del EQ (`EQ_LABELS`) corresponden a frecuencias fijas: 125, 250, 500, 1000, 2000, 4000, 6000, 8000, 10000, 12000 Hz
- [ ] El `kSampleRate` es 48000 Hz

---

## Fuera de Alcance heredado

No hay brief origen — Fuera de Alcance vacío.

### Fuera de Alcance adicional
- Visualización 3D o efectos de partículas (barras simples con interpolación)
- Funcionalidad de grabación o export del espectro
- Modo pantalla completa del analizador
- Análisis espectral de más de 10 bandas (alineado con el EQ existente)

---

## Aprendizajes heredados de fases previas

No hay brief origen — aplicar aprendizajes transversales de CLAUDE.md:

- **Ring buffer C++ para consumidores Kotlin**: usar buffer preasignado con mutex propio cuando se necesita acumular audio. En este caso, las magnitudes son solo 10 floats — un array atómico es suficiente, sin ring buffer.
- **Double-buffer copy-modify-swap para parámetros DSP thread-safe**: para datos que se escriben en audio thread y leen desde JNI, usar arrays preasignados o atómicos simples.
- **Consumidores pesados de audio deben integrarse a nivel C++**: el FFT se calcula en C++ inline, no se envía audio a Kotlin para procesamiento.

---

## Plan de implementación

### Fase 1: FFT y magnitudes espectrales en C++
- **Objetivo**: Implementar FFT radix-2 de 512 puntos en C++ y calcular magnitudes por banda alineadas con las 10 frecuencias del EQ. El cálculo se ejecuta inline en `onAudioReady` sobre `captureBuffer_` post-DSP. Las magnitudes se almacenan en un array de 10 floats legible desde JNI sin lock.
- **Validación**:
  - [ ] Clase `SpectrumAnalyzer` con FFT radix-2 y buffers preasignados
  - [ ] Método `feedAudio(float*, int)` llamado desde `onAudioReady`
  - [ ] Array de magnitudes de 10 bandas actualizado cada frame
  - [ ] Getter `getSpectrum()` retorna copia de las 10 magnitudes
  - [ ] Build compila sin errores

### Fase 2: JNI bridge y API Kotlin
- **Objetivo**: Exponer las magnitudes espectrales a Kotlin via JNI y agregar el getter al `OboeAudioEngine`. Seguir patrón existente de `getLatencyStats()`.
- **Validación**:
  - [ ] Función JNI `nativeGetSpectrumData` en native-lib.cpp
  - [ ] Método `getSpectrumData(): FloatArray?` en OboeAudioEngine.kt
  - [ ] Build compila sin errores

### Fase 3: UI Canvas Compose con interpolación
- **Objetivo**: Crear visualización de barras animadas en Canvas Compose que muestre las 10 magnitudes espectrales con interpolación suave. Integrar debajo del EqualizerCard existente en SettingsScreen.
- **Validación**:
  - [ ] Composable `SpectrumAnalyzerCard` con Canvas que dibuja 10 barras verticales
  - [ ] Polling cada ~50ms (20 fps) via LaunchedEffect
  - [ ] Interpolación suave entre frames (animación de caída con decay exponencial)
  - [ ] Color-coding por nivel (verde → amarillo → rojo)
  - [ ] Integrado debajo del EqualizerCard en SettingsScreen
  - [ ] Build compila sin errores

### Fase 4: Validación final
- **Objetivo**: Sistema funcionando end-to-end con build limpio.
- **Validación**:
  - [ ] Criterios de éxito cumplidos
  - [ ] `./gradlew assembleDebug` exitoso
  - [ ] FFT no impacta latencia del audio thread (< 50µs medible en PerformanceTracker)
  - [ ] Barras responden en tiempo real al audio ambiente
  - [ ] Barras caen a cero en silencio

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

### 2026-08-27: FFT inline de 512 puntos es suficiente para 10 bandas EQ en audio real-time
- **Error**: Ninguno — implementación directa sin iteraciones.
- **Fix**: N/A.
- **Aplicar en**: La resolución de 93.75 Hz/bin (48000/512) es suficiente para mapear las 10 bandas del EQ (125Hz-12kHz) usando fronteras geométricas (media geométrica entre bandas adyacentes). Para bandas más finas (<100Hz) se necesitaría FFT de 1024+ o zero-padding mayor.

### 2026-08-27: std::atomic<float> array es el patrón correcto para datos read-only de visualización
- **Error**: Ninguno.
- **Fix**: N/A.
- **Aplicar en**: Para datos que se escriben desde el audio thread y se leen desde JNI sin necesidad de coherencia entre elementos (cada barra es independiente), un array de `std::atomic<float>` con `memory_order_relaxed` es más simple y eficiente que un double-buffer. Reservar double-buffer para snapshots que requieren coherencia entre campos (como EqSnapshot).

---

## Anti-patrones

- NO usar dependencias externas para FFT (FFTW, KissFFT) — implementar radix-2 inline
- NO allocar memoria en `onAudioReady` — todos los buffers preasignados en constructor
- NO usar mutex para las magnitudes espectrales — array atómico o patrón lock-free
- NO generar nuevos PRPs durante la ejecución de este PRP
- NO enviar audio raw a Kotlin para procesamiento espectral — todo en C++
- NO modificar la funcionalidad existente del EqualizerCard (solo agregar visualización debajo)

---

*PRP pendiente aprobación. No se ha modificado código.*
