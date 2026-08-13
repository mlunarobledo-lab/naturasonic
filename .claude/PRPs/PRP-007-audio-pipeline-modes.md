# PRP-007: Pipeline Avanzado de Modos de Escucha — Enlace Room ↔ Oboe

> **Estado**: EN PROGRESO
> **Fecha**: 2026-08-13
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Complementa la Fase 5 (Modos de Uso) y la Fase 3 (Motor PSAP) con el enlace reactivo entre la capa de persistencia Room (PRP-006) y el motor de audio C++ (Oboe/AudioProcessor). Hereda Directiva de Stack, Supuestos, Fuera de Alcance y aprendizajes acumulados.

---

## Objetivo

Quiero que cuando seleccione un perfil de ecualización guardado en Room, las 10 bandas del ecualizador biquad se actualicen en tiempo real en el pipeline C++ de Oboe — sin glitches, sin cracks, sin latencia perceptible. La UI de Compose debe ser la fuente de verdad del usuario, Room la persistencia, y el AudioProcessor C++ el consumidor final. El flujo debe ser completamente reactivo: mover un slider → Room persiste → JNI envía las ganancias → el Biquad recalcula coeficientes → el audio se modifica en el siguiente frame.

## Por Qué

| Problema | Solución |
|----------|----------|
| `setEqBands` existe en JNI pero el pipeline no protege contra glitches al recalcular coeficientes biquad en el thread de audio | Separar el cálculo de coeficientes del thread de audio con doble-buffer atómico |
| No hay observación reactiva de Room → engine: los cambios de perfil requieren invocación manual explícita | Corrutina que observa el perfil activo via Flow y propaga cambios al engine vía JNI automáticamente |
| `AudioModeManager.applyProfile` deserializa JSON y llama al engine, pero no tiene protección de thread ni rate-limiting para sliders en tiempo real | Debounce en la capa Kotlin + envío atómico thread-safe al engine C++ |

**Valor**: El ecualizador deja de ser un preset estático y se convierte en un instrumento en tiempo real — el usuario mueve un slider y escucha el cambio instantáneamente sin artefactos de audio.

## Qué

### Criterios de éxito
- [ ] Mover cualquiera de las 10 bandas EQ en la UI actualiza el filtro biquad en C++ en < 10ms sin glitch audible
- [ ] Cambiar de perfil en Room actualiza las 10 bandas en el engine automáticamente via Flow → JNI
- [ ] Los coeficientes biquad se recalculan fuera del thread de audio (doble-buffer o swap atómico)
- [ ] Rate-limiting (debounce ~30ms) previene flooding JNI cuando el usuario arrastra un slider rápidamente
- [ ] La amplificación y noise suppression del perfil se aplican junto con las bandas EQ en una sola operación atómica
- [ ] `./gradlew assembleDebug` compila sin errores
- [ ] No se modifica la versión de AGP ni se agregan dependencias nuevas

### Comportamiento esperado

El usuario abre la pantalla de ecualización. Ve 10 sliders (125 Hz – 12 kHz) con los valores del perfil activo cargados desde Room. Arrastra el slider de 2 kHz de +3 dB a +8 dB. En < 10ms, el AudioProcessor C++ recibe el nuevo array de ganancias, recalcula los coeficientes biquad de esa banda, y los aplica al siguiente buffer de audio. El usuario escucha el cambio inmediatamente. Al soltar el slider, Room persiste el valor nuevo. Si cambia a otro perfil (ej: de "Conversación" a "Música Personalizada"), las 10 bandas se actualizan simultáneamente en el engine.

### Casos borde

- **Slider arrastrado a velocidad máxima**: debounce de ~30ms agrupa cambios, evitando 60+ llamadas JNI por segundo
- **Cambio de perfil mientras se arrastra un slider**: el perfil nuevo sobrescribe todas las bandas de golpe, cancelando el debounce pendiente
- **Engine no iniciado**: `OboeAudioEngine.setEqBands()` ya tiene guard `if (engineHandle != 0L)`, no se invoca JNI
- **Perfil con JSON malformado**: `AudioModeManager.applyProfile` ya tiene fallback a `FloatArray(10) { 0f }`
- **Transición de 5 bandas a 10 bandas**: `setEqBands` C++ acepta count variable con `std::min(count, kMaxEqBands)`, compatible con ambos
- **Audio thread starvation**: los coeficientes biquad se pre-calculan en el thread que llama a `setEqBands`, no en `onAudioReady`

---

## Contexto

### Código existente a consultar

- `app/src/main/cpp/audio_processor.h` — `AudioProcessor` con `setEqBands(const float*, int)`, `kMaxEqBands = 10`, `kCenterFreqs[10]`, `computeEqCoefficients()`, arrays `eqGains_[]`, `eqCoeffs_[]`, `eqStates_[]`
- `app/src/main/cpp/audio_processor.cpp` — `setEqBands` escribe `eqGains_[]` y llama `computeEqCoefficients()` sincrónicamente; `applyEqualizer` itera bandas con `processBiquad` en el thread de audio
- `app/src/main/cpp/oboe_engine.h` — `NaturaSonicEngine` con `AudioProcessor processor_` (miembro directo, no puntero); expone `setEqBands` que delega a `processor_`
- `app/src/main/cpp/oboe_engine.cpp` — `onAudioReady` llama `processor_.process()` → `applyEqualizer()` en el thread de audio del output stream
- `app/src/main/cpp/native-lib.cpp` — JNI `nativeSetEqBands` ya existe, copia `jfloatArray` → `float*` y llama `eng->setEqBands(data, len)`
- `app/src/main/java/.../audio/OboeAudioEngine.kt` — wrapper Kotlin con `setEqBands(bands: FloatArray)`, guard `engineHandle != 0L`
- `app/src/main/java/.../audio/AudioModeManager.kt` — `applyProfile` deserializa JSON → `FloatArray`, llama `audioEngine.setEqBands(bands)`
- `app/src/main/java/.../data/local/entity/AudioProfile.kt` — Room entity con `eqBands: String` (JSON array de 10 floats)
- `app/src/main/java/.../data/repository/AudioProfileRepository.kt` — `getById(id)`, `getAllProfiles()`, `getProfilesByMode(mode)`
- `app/src/main/java/.../service/AudioService.kt` — `restoreActiveProfile()` en corrutina, lee perfil de Room al arrancar

### Gotchas conocidas

- **Thread safety de `setEqBands`**: `eqGains_[]` y `eqCoeffs_[]` NO son atómicos ni protegidos por mutex. `setEqBands` escribe los gains y recalcula coeficientes, mientras `applyEqualizer` los lee en `onAudioReady`. Riesgo de datos parciales (tear) si el callback de audio lee mientras se escribe. **Este es el problema central del PRP.**
- **`computeEqCoefficients()` es costoso**: contiene `sin()`, `cos()`, `pow()` por banda. Actualmente se ejecuta en el thread que llama `setEqBands` (thread del caller JNI), NO en el thread de audio — esto es correcto pero el tear sigue presente durante la escritura.
- **Frecuencias centrales fijas**: `kCenterFreqs[]` es `constexpr` con 10 valores fijos (125 Hz – 12 kHz). No se necesita enviarlas por JNI.
- **`eqBandCount_`**: se actualiza en `setEqBands` junto con los gains. Un cambio de 5→10 bandas podría causar que `applyEqualizer` lea `eqBandCount_ = 10` antes de que los nuevos `eqCoeffs_[5..9]` estén calculados.
- **Dependencias y AGP congelados**: NO se agregan dependencias. NO se modifica AGP. Solo se toca código fuente C++ y Kotlin existente.

### Modelo de datos

No hay cambios al schema de Room. El entity `AudioProfile` ya almacena `eqBands` como JSON string con 10 floats. La columna se consume tal cual.

---

## Directiva de Stack heredada

> Compatibilidad con Praxis: **REPLACE** (proyecto Android nativo). La Directiva completa vive en `docs/BRIEF-naturasonic.md`. Solo se listan los componentes relevantes a este PRP:

### KEEP
- Pipeline de audio: Oboe 1.9 + AudioProcessor C++ + VolumeLimiter C++ (congelados)
- Persistencia: Room v1 + DataStore (schema congelado)
- JNI bridge: `native-lib.cpp` con funciones existentes
- CMake + NDK: `CMakeLists.txt` congelado, sin dependencias nuevas

### ADD
- Ninguna dependencia nueva

### REPLACE
- Ninguno

### REMOVE
- Ninguno

### CONFIG
- Ningún cambio de configuración

### Refinamientos a la Directiva durante este PRP
- **AGP congelado**: la versión actual de Android Gradle Plugin NO se modifica
- **Sin dependencias nuevas**: todo se resuelve con el SDK estándar y las librerías ya presentes

---

## Supuestos heredados

- [x] Pipeline Oboe funcional a 48kHz mono con `AudioProcessor.process()` en `onAudioReady`
- [x] JNI bridge `nativeSetEqBands` funcional (probado en PRPs previos)
- [x] Room database v1 con entity `AudioProfile` y DAO completo
- [x] `AudioModeManager.applyProfile` capaz de deserializar JSON → FloatArray y enviar al engine
- [x] `AudioService.restoreActiveProfile()` restaura perfil activo al arrancar

### Supuestos adicionales (específicos de este PRP)
- [ ] El costo de `computeEqCoefficients()` (10x sin/cos/pow) es despreciable comparado con la latencia del frame de audio (~5ms a 256 frames/48kHz), permitiendo pre-cálculo en el thread caller
- [ ] Un swap atómico de puntero/índice es suficiente para eliminar tears entre el thread caller y el thread de audio

---

## Fuera de Alcance heredado

- Streaming Auracast / broadcast LE Audio
- Versión iOS
- Backend en la nube / Supabase
- Audiograma clínico o calibración audiológica
- Grabación persistente de audio
- UI de gestión de perfiles (ya existe desde PRP-006; este PRP solo toca la capa de enlace reactivo)

### Fuera de Alcance adicional (específico de este PRP)
- Cambio de frecuencias centrales del EQ (son constexpr fijas)
- Cambio de Q/bandwidth por banda (valor fijo Q=1.0)
- Nuevo tipo de filtro (solo peaking EQ biquad)
- Visualización de respuesta en frecuencia / analizador de espectro
- Crossfade entre perfiles (el cambio es instantáneo, frame-boundary)

---

## Aprendizajes heredados de fases previas

**2026-08-03 — openMHA → implementación directa C++**: Los algoritmos PSAP (biquad EQ, noise gate, volume limiter) se implementaron directamente sobre Oboe, no como dependencia de openMHA. Aplicar en: cualquier extensión del pipeline — implementar sobre los archivos C++ existentes.

**2026-08-10 — Consumidores pesados via C++ thread dedicado**: WhisperBridge demostró el patrón de buffer mutex-protegido + thread dedicado para audio pesado. Aplicar en: el doble-buffer de coeficientes biquad puede seguir un patrón similar (mutex ligero o swap atómico).

**2026-08-12 — Ring buffer C++ para ventanas de audio**: El patrón de ring buffer con mutex dedicado funciona para consumidores que necesitan audio acumulado. Aplicar en: no directamente relevante aquí (los coeficientes EQ no son un stream), pero refuerza que mutex ligeros en C++ son aceptables en el path de audio si están fuera del loop de procesamiento.

**2026-08-13 — Pipeline Oboe opera a 48kHz**: El sample rate es fijo a 48kHz. `computeEqCoefficients()` ya usa `kSampleRate = 48000` en el cálculo de `w0`. No requiere ajuste.

---

## Plan de implementación

> Solo fases. Las subtareas se generan al ENTRAR a cada fase.

### Fase 1: Thread-safety del AudioProcessor — doble-buffer de coeficientes biquad
- **Objetivo**: Eliminar el data race entre `setEqBands` (thread caller) y `applyEqualizer` (thread de audio). Implementar un esquema de doble-buffer donde `setEqBands` escribe en el buffer inactivo, recalcula coeficientes, y hace swap atómico del índice. `applyEqualizer` siempre lee del buffer activo sin contención.
- **Archivos a tocar**: `audio_processor.h`, `audio_processor.cpp`
- **Validación**:
  - [x] `setEqBands` escribe gains y coeficientes en buffer[!activeIndex] — `eqSnapshots_[writeIdx]`
  - [x] `computeEqCoefficients` opera sobre el buffer inactivo — `computeEqCoefficients(snap)` recibe `EqSnapshot&` del writeIdx
  - [x] Swap atómico de `activeIndex_` al finalizar el cálculo — `activeEqIndex_.store(writeIdx, memory_order_release)`
  - [x] `applyEqualizer` lee de `eqCoeffs_[activeIndex_]` y `eqGains_[activeIndex_]` — `activeEqIndex_.load(memory_order_acquire)` → `snap.coeffs[]` / `snap.gains[]`
  - [x] `eqStates_[]` permanece único (estado continuo del filtro, no se duplica) — array plano fuera de EqSnapshot
  - [x] `./gradlew assembleDebug` compila sin errores — BUILD SUCCESSFUL (arm64-v8a, armeabi-v7a, x86_64)

### Fase 2: Observación reactiva Room → Engine con debounce
- **Objetivo**: Crear la corrutina de observación que conecta cambios de perfil en Room con el engine C++ via JNI. Cuando el perfil activo cambia (nuevo perfil seleccionado, slider movido y persistido), el Flow emite → debounce ~30ms → `AudioModeManager.applyProfile` → JNI `setEqBands`. Integrar en `AudioService` como observer lifecycle-aware.
- **Archivos a tocar**: `AudioModeManager.kt`, `AudioService.kt`, `UserPreferences.kt`
- **Validación**:
  - [ ] Un `collect` sobre `selectedProfileId` + `AudioProfileRepository` propaga cambios al engine
  - [ ] Debounce de ~30ms agrupa actualizaciones rápidas de slider
  - [ ] El observer se cancela correctamente en `AudioService.stopAudio()`
  - [ ] Cambio de modo (CONVERSATION → ENTERTAINMENT) actualiza selectedProfileId → observer aplica nuevo perfil
  - [ ] `./gradlew assembleDebug` compila sin errores

### Fase 3: Operación atómica de perfil completo (EQ + amplificación + NS)
- **Objetivo**: Garantizar que `applyProfile` envíe todas las propiedades del perfil (bandas EQ, amplificación, noise suppression, AEC) como una unidad atómica desde la perspectiva del engine. Evitar estados intermedios donde el EQ es del perfil nuevo pero la amplificación del viejo.
- **Archivos a tocar**: `audio_processor.h`, `audio_processor.cpp`, `native-lib.cpp`, `oboe_engine.h`, `oboe_engine.cpp`, `OboeAudioEngine.kt`, `AudioModeManager.kt`
- **Validación**:
  - [ ] Nuevo método JNI `nativeApplyProfile(handle, bands, amplification, nsSuppression)` que configure todo en una sola llamada
  - [ ] En C++, `AudioProcessor::applyProfile()` actualiza gains, amplificación y NS en el buffer inactivo, luego hace un solo swap atómico
  - [ ] `AudioModeManager.applyProfile` usa el nuevo método atómico en vez de 3 llamadas separadas
  - [ ] `./gradlew assembleDebug` compila sin errores

### Fase 4: Validación end-to-end y build de producción
- **Objetivo**: Verificar el flujo completo: seleccionar perfil en UI → Room persiste → observer emite → debounce → JNI atómico → AudioProcessor aplica sin glitch. Confirmar build limpio.
- **Validación**:
  - [ ] Criterios de éxito cumplidos (todos los checkboxes de la sección "Qué")
  - [ ] `./gradlew assembleDebug` exitoso
  - [ ] `./gradlew lint` sin errores críticos
  - [ ] Revisión de thread-safety: ningún acceso no-protegido a `eqGains_`/`eqCoeffs_` desde threads concurrentes

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

*(Vacío — se completará durante la ejecución)*

---

## Anti-patrones

- No modificar `kCenterFreqs[]` — son constexpr y fijas por diseño
- No agregar mutex al loop de `onAudioReady` — usar swap atómico de índice
- No cruzar JNI más de lo necesario — agrupar propiedades de perfil en una sola llamada
- No hardcodear ganancias EQ — siempre leer de Room
- No ignorar el debounce — un slider genera 60+ eventos/segundo sin él

---

*PRP pendiente aprobación. No se ha modificado código.*
