# PRP-002: Integración whisper.cpp — Motor de transcripción premium offline

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-10
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la Fase 4 (Transcripción Offline) con el motor premium whisper.cpp que quedó como stub en PRP-001.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados del brief y PRP-001.

---

## Objetivo

Quiero que NaturaSonic tenga un segundo motor de transcripción offline de alta fidelidad basado en whisper.cpp, disponible como feature premium. El usuario premium puede elegir entre Vosk (ligero, streaming) y Whisper (alta precisión, procesamiento por segmentos) desde la pantalla de transcripción.

## Por Que

| Problema | Solución |
|----------|----------|
| Vosk tiene precisión limitada en ambientes ruidosos o con acentos variados | whisper.cpp (basado en OpenAI Whisper) ofrece precisión superior en reconocimiento de voz |
| No hay motor premium que justifique la suscripción para transcripción | Whisper como feature exclusiva premium diferencia el tier gratuito del pago |
| Las soluciones cloud tienen latencia y requieren internet | whisper.cpp corre 100% offline en el dispositivo |

**Valor**: Motor de transcripción premium que justifica la suscripción. Precisión superior a Vosk en escenarios reales. 100% offline.

## Que

### Criterios de éxito
- [ ] whisper.cpp compila como librería nativa para arm64-v8a, armeabi-v7a, x86_64
- [ ] El puente JNI permite inicializar modelo, procesar audio y obtener texto desde Kotlin
- [ ] Los modelos GGML (tiny ~75MB, base ~142MB) se descargan on-demand con progreso visible
- [ ] El audio de 48kHz del pipeline Oboe se resamplea a 16kHz para whisper.cpp
- [ ] La transcripción produce texto con precisión superior a Vosk en ambiente controlado
- [ ] El motor whisper está gated como feature premium (WHISPER_ENGINE en PremiumFeature)
- [ ] El usuario puede alternar entre Vosk y Whisper desde la UI
- [ ] El historial de transcripciones registra el motor usado (VOSK/WHISPER)
- [ ] Build completo (`./gradlew assembleDebug`) pasa sin errores en las 3 arquitecturas

### Comportamiento esperado

El usuario premium abre la pantalla de transcripción. Ve un selector de motor (Vosk / Whisper). Si elige Whisper y no tiene el modelo descargado, aparece una card de descarga con el tamaño estimado. Tras descargar, el modelo se inicializa vía JNI. Al presionar transcribir, el audio capturado por Oboe se resamplea de 48kHz a 16kHz, se acumula en segmentos y se procesa por whisper.cpp. El texto resultante aparece en los subtítulos con la misma UI existente. Al detener, se guarda en el historial con engine="WHISPER".

### Casos borde
- Modelo no descargado → mostrar card de descarga
- Descarga interrumpida → permitir reintentar, limpiar archivos parciales
- Memoria insuficiente para modelo base → ofrecer modelo tiny como alternativa
- Usuario no premium intenta seleccionar Whisper → mostrar card de upgrade

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/CMakeLists.txt` — patrón FetchContent (Oboe)
- `app/src/main/cpp/native-lib.cpp` — patrón JNI existente
- `app/src/main/java/com/naturasonic/app/transcription/VoskTranscriptionEngine.kt` — patrón de motor de transcripción
- `app/src/main/java/com/naturasonic/app/billing/BillingManager.kt` — PremiumFeature.WHISPER_ENGINE
- `app/src/main/java/com/naturasonic/app/ui/screens/transcription/` — UI existente

### Gotchas conocidas
- whisper.cpp espera audio PCM 16-bit a 16kHz mono; nuestro pipeline Oboe corre a 48kHz → necesita resampling 3:1
- whisper.cpp procesa segmentos completos (no streaming token-a-token como Vosk) → UX diferente
- Los modelos GGML son grandes (75-142MB) → descarga separada obligatoria
- FetchContent de whisper.cpp descarga ~200MB de repo en primer build → build inicial lento

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Gradle/AGP 8.7.3, Kotlin 2.0.21, compileSdk 36, minSdk 29 (CONGELADOS por instrucción del usuario)
- CMake 3.22.1 + NDK con C++17
- FetchContent como patrón de gestión de dependencias nativas

### ADD
- whisper.cpp (vía FetchContent, tag estable)

### REPLACE
- (ninguno)

### REMOVE
- (ninguno)

### CONFIG
- Extender CMakeLists.txt existente con target whisper + JNI bridge
- Agregar segunda librería nativa `whisper_jni` al build

---

## Supuestos heredados

- [x] Pipeline de audio Oboe funcional a 48kHz mono (verificado: PRP-001 COMPLETADO)
- [x] Estructura JNI funcional con `native-lib.cpp` (verificado: PRP-001 COMPLETADO)
- [x] Room DB con tabla `transcription_history` y campo `engine` (verificado: TranscriptionEntry.kt)
- [x] BillingManager con PremiumFeature.WHISPER_ENGINE (verificado: BillingManager.kt)
- [x] VoskTranscriptionEngine funcional como referencia de patrón (verificado: lectura del archivo)
- [ ] NDK disponible en el sistema con soporte para las 3 ABIs (verificar empíricamente al compilar)

---

## Fuera de Alcance heredado

- Fine-tuning de modelos Whisper
- Modelos grandes (small/medium/large) — solo tiny y base para mobile
- Streaming token-a-token (whisper procesa segmentos completos)
- Traducción automática (solo transcripción en idioma fuente)

---

## Aprendizajes heredados de fases previas

**2026-08-03 (PRP-001)**: openMHA no es viable como dependencia NDK — los algoritmos PSAP se implementan directamente en C++. Aplica como precedente: cualquier librería grande con build system complejo debe evaluarse antes de FetchContent. whisper.cpp tiene CMake nativo limpio → viable.

**2026-08-03 (PRP-001)**: Vosk se integró vía reflection por no ser dependencia Maven estándar. whisper.cpp se integrará vía JNI directo (C++ nativo), evitando reflection.

---

## Plan de implementación

### Fase 1: Build nativo whisper.cpp con CMake/NDK
- **Objetivo**: whisper.cpp compila como librería estática para las 3 ABIs vía FetchContent
- **Validación**: `./gradlew assembleDebug` pasa sin errores de compilación nativa

### Fase 2: Puente JNI whisper.cpp
- **Objetivo**: JNI bridge funcional que expone init/process/free desde C++ a Kotlin
- **Validación**: Funciones JNI declaradas y compiladas sin errores de linkeo

### Fase 3: Motor Kotlin WhisperTranscriptionEngine
- **Objetivo**: Motor Kotlin completo con descarga de modelos, inicialización JNI, resampling 48→16kHz, procesamiento de audio
- **Validación**: Clase compilable, inyectable via Hilt, siguiendo patrón de VoskTranscriptionEngine

### Fase 4: Integración UI + Premium gating
- **Objetivo**: Selector de motor en TranscriptionScreen, gating premium, descarga de modelos whisper
- **Validación**: UI compila, selector visible, gating funcional

### Fase 5: Validación final
- **Objetivo**: Build completo en 3 ABIs, documentación actualizada
- **Validación**:
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] PRP marcado COMPLETADO
  - [ ] Aprendizajes propagados
  - [ ] Commit + push

---

## Aprendizajes

### 2026-08-10: whisper.cpp v1.6.2 compila limpio con Android NDK vía FetchContent
- **Error**: Ninguno — la integración funcionó al primer intento
- **Fix**: N/A
- **Aplicar en**: Cualquier futuro módulo nativo C/C++ — FetchContent es el patrón probado para deps nativas en este proyecto (Oboe v1.9.0 + whisper.cpp v1.6.2)

### 2026-08-10: whisper_init_from_file está deprecated en whisper.cpp v1.6.2
- **Error**: Warning de deprecación al usar `whisper_init_from_file()`
- **Fix**: Usar `whisper_init_from_file_with_params()` con `whisper_context_default_params()`
- **Aplicar en**: Cualquier actualización futura de la versión de whisper.cpp — revisar deprecaciones en el header

### 2026-08-10: Resampling 48kHz→16kHz necesario para whisper.cpp
- **Error**: whisper.cpp espera audio PCM float32 a 16kHz mono. Nuestro pipeline Oboe opera a 48kHz.
- **Fix**: Implementado resampling por decimación 3:1 con promediado (anti-aliasing simple) en `WhisperTranscriptionEngine.kt`. No es un resampler de calidad audiófila pero es suficiente para transcripción.
- **Aplicar en**: Cualquier otro motor de procesamiento de audio que requiera sample rate diferente al del pipeline Oboe (48kHz)

---
