# PRP-003: JNI Bridge unificado — Oboe↔whisper.cpp en C++ nativo

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-10
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Refactoriza la integración whisper.cpp (PRP-002) para eliminar el roundtrip JNI y rutear audio directamente en C++.
> Hereda Directiva de Stack, Supuestos, y aprendizajes de PRP-001 y PRP-002.

---

## Objetivo

Unificar el puente JNI de whisper.cpp dentro de `native-lib.cpp`, conectando el pipeline Oboe directamente con el motor de inferencia whisper en C++. El audio capturado por Oboe fluye al motor whisper sin salir de C++ (sin roundtrip JNI para buffers de audio). El resampling 48kHz→16kHz se ejecuta en C++ nativo.

## Por Que

| Problema | Solución |
|----------|----------|
| Audio actual viaja C++→JNI→Kotlin→JNI→C++ (roundtrip) para llegar a whisper | Ruteo directo C++→C++ elimina 2 cruces JNI por frame de audio |
| Resampling en Kotlin añade latencia y GC pressure | Resampling nativo en C++ es zero-alloc y más eficiente |
| Dos shared libraries separadas (naturasonic + whisper_jni) fragmentan el build | Una sola librería `libnaturasonic.so` con todo el pipeline |

**Valor**: Menor latencia en transcripción. Build más limpio. Arquitectura coherente donde el audio nunca abandona C++ hasta convertirse en texto.

## Que

### Criterios de éxito
- [ ] `WhisperBridge` C++ encapsula ring buffer, resampling nativo y thread de procesamiento
- [ ] `NaturaSonicEngine` alimenta audio a WhisperBridge desde `onAudioReady` sin cruce JNI
- [ ] JNI methods para whisper viven en `native-lib.cpp` (init, start, stop, getText, release)
- [ ] CMake linkea whisper a target `naturasonic` (se elimina target `whisper_jni`)
- [ ] `WhisperTranscriptionEngine.kt` usa la librería `naturasonic` (no `whisper_jni`)
- [ ] Resampling 48→16kHz ejecutado en C++ con filtro anti-alias por promediado
- [ ] Thread dedicado de procesamiento whisper (no bloquea audio callback)
- [ ] Build completo (`./gradlew assembleDebug`) pasa sin errores en las 3 ABIs

---

## Directiva de Stack heredada

### KEEP
- Gradle/AGP 8.7.3, Kotlin 2.0.21, compileSdk 36, minSdk 29 (CONGELADOS)
- CMake 3.22.1 + NDK con C++17
- FetchContent para Oboe 1.9.0 y whisper.cpp v1.6.2
- whisper.cpp v1.6.2 con todas las opciones GPU/accelerator OFF

### ADD
- `whisper_bridge.h` / `whisper_bridge.cpp` (nueva clase C++)

### REMOVE
- `whisper_jni.cpp` (target `whisper_jni` de CMake)

### CONFIG
- Linkear `whisper` al target `naturasonic` en CMakeLists.txt
- Agregar `whisper_bridge.cpp` a sources del target `naturasonic`

---

## Supuestos heredados

- [x] Pipeline Oboe funcional a 48kHz mono (PRP-001 COMPLETADO)
- [x] whisper.cpp v1.6.2 compila con FetchContent (PRP-002 COMPLETADO)
- [x] JNI bridge funcional en native-lib.cpp (PRP-001 COMPLETADO)
- [x] WhisperTranscriptionEngine.kt funcional (PRP-002 COMPLETADO)

---

## Fuera de Alcance

- Streaming token-a-token (whisper procesa segmentos completos)
- Lock-free SPSC ring buffer (mutex con lock breve es suficiente para 256 frames)
- Cambios al pipeline de audio Oboe (solo se agrega tap para whisper)

---

## Plan de implementación

### Fase 1: WhisperBridge C++ — ring buffer + resampling + thread de procesamiento
- **Objetivo**: Clase C++ autónoma que acumula audio 48kHz, resamplea a 16kHz, procesa con whisper en thread dedicado
- **Validación**: Compila sin errores como parte del target naturasonic

### Fase 2: Integración NaturaSonicEngine + JNI + CMake
- **Objetivo**: NaturaSonicEngine alimenta WhisperBridge desde onAudioReady. JNI en native-lib.cpp. CMake unificado
- **Validación**: `./gradlew assembleDebug` pasa

### Fase 3: Adaptación Kotlin + limpieza
- **Objetivo**: WhisperTranscriptionEngine usa bridge unificado. Polling de texto via coroutine. Eliminar whisper_jni.cpp
- **Validación**: Build completo en 3 ABIs. Documentación actualizada

---

## Aprendizajes

### 2026-08-10: Unificación de librería nativa elimina overhead JNI para audio
- **Error**: Dos shared libraries separadas (`libnaturasonic.so` + `libwhisper_jni.so`) forzaban roundtrip JNI para buffers de audio: C++→Kotlin→C++.
- **Fix**: Merge de whisper como dependencia del target `naturasonic` en CMake. `WhisperBridge` C++ recibe audio directo desde `onAudioReady`. Se elimina `whisper_jni` target y `whisper_jni.cpp`.
- **Aplicar en**: Cualquier módulo nativo futuro que consuma audio del pipeline Oboe — integrar en el mismo target CMake, no crear librería separada.

### 2026-08-10: Thread dedicado para inferencia whisper evita bloquear audio callback
- **Error**: `whisper_full()` puede tardar varios segundos en un segmento de 10s. Si se ejecutara en el audio callback, bloquearía el pipeline.
- **Fix**: `WhisperBridge` usa `std::thread` con `condition_variable` para despertar cuando hay suficientes samples (10s a 48kHz). El audio callback solo hace `mutex lock + vector insert` (~256 floats, microsegundos).
- **Aplicar en**: Cualquier procesamiento pesado sobre audio en tiempo real. Patrón: buffer compartido con mutex breve en el callback + thread de procesamiento que consume segmentos.

### 2026-08-10: Polling de texto via coroutine es suficiente para UI de transcripción
- **Error**: N/A — decisión de diseño.
- **Fix**: `WhisperTranscriptionEngine.kt` usa coroutine con `delay(150)` para polling de `nativeGetWhisperText()`. Latencia imperceptible para mostrar subtítulos.
- **Aplicar en**: Cualquier resultado de procesamiento nativo que deba mostrarse en UI — polling via coroutine es más simple y seguro que JNI callbacks desde threads nativos.
