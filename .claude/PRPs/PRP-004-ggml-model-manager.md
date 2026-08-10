# PRP-004: Gestor de Modelos GGML — extracción desde assets con StateFlow

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-10
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md` (Fase 4). Complementa PRP-002/003 con gestión automática de modelos GGML desde assets del APK.

---

## Objetivo

Crear un gestor automático de modelos GGML que verifica existencia en almacenamiento interno, extrae desde assets del APK en primer inicio con I/O buffered, expone 4 estados claros (Uninitialized, Copying, Ready, Error) via StateFlow a Compose, y suministra la ruta absoluta a `nativeInitWhisper`.

## Que

### Criterios de éxito
- [ ] `GgmlModelManager` verifica modelo en `context.filesDir` antes de copiar
- [ ] Extracción desde assets con InputStream/OutputStream buffered (16KB)
- [ ] `ModelState` sealed interface con 4 estados: Uninitialized, Copying(progress), Ready(path), Error(message)
- [ ] StateFlow<ModelState> observable desde TranscriptionViewModel y Compose
- [ ] Ruta absoluta del modelo llega a `nativeInitWhisper` del backend C++
- [ ] `aaptOptions { noCompress("bin") }` en build.gradle.kts
- [ ] WhisperTranscriptionEngine delega gestión de modelo al manager
- [ ] Build en 3 ABIs pasa sin errores

---

## Directiva de Stack heredada

### KEEP
- Gradle/AGP 8.7.3, Kotlin 2.0.21, compileSdk 36 (CONGELADOS)
- Hilt para DI, StateFlow para estado reactivo
- `libnaturasonic.so` unificada (PRP-003)

### ADD
- `GgmlModelManager.kt` en `transcription/`
- Directorio `assets/models/whisper/` con `.gitkeep`
- `aaptOptions.noCompress("bin")` en build.gradle.kts

---

## Plan de implementación

### Fase 1: GgmlModelManager — sealed states + asset extraction
### Fase 2: Integración Engine + ViewModel + UI + build config
### Fase 3: Validación

---

## Aprendizajes

### 2026-08-10: openFd fallback necesario para assets comprimidos
- **Error**: `AssetFileDescriptor.getLength()` solo funciona con assets no comprimidos. Para assets comprimidos, `openFd()` lanza excepción.
- **Fix**: Usar `openFd()` para obtener tamaño con try/catch, fallback a `WhisperModel.sizeMb * 1024 * 1024` para progreso estimado. Config `aaptOptions { noCompress("bin") }` previene compresión de modelos.
- **Aplicar en**: Cualquier asset grande que se extraiga a almacenamiento interno.

### 2026-08-10: initializeModel debe ser suspend por la extracción de assets
- **Error**: `initializeModel()` era una función síncrona. Con el manager, `ensureModel()` es suspend (I/O de archivos).
- **Fix**: Convertir `initializeModel()` a suspend y lanzar via `scope.launch` en los call sites (`selectModel`, `init`).
- **Aplicar en**: Cualquier inicialización que dependa de I/O o red debe ser suspend desde el inicio.
