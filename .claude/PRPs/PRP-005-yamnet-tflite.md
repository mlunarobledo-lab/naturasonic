# PRP-005: Detección de alertas con YAMNet/TFLite

> **Estado**: EN PROGRESO
> **Fecha**: 2026-08-12
> **Proyecto**: NaturaSonic

---

## Origen

> No hay brief origen — planificación directa. Continuación del roadmap acordado en el checkpoint dd9c514 (AGENTS.md). Las secciones heredadas se derivan de CLAUDE.md/AGENTS.md.

---

## Objetivo

> Quiero que NaturaSonic detecte sonidos de alerta del entorno (sirena, timbre, llanto de bebé, alarma de humo, claxon, cristal roto, ladrido) en tiempo real usando YAMNet sobre TensorFlow Lite, y notifique al usuario con vibración y registro persistente.

## Por Qué

| Problema | Solución |
|----------|----------|
| Usuarios con pérdida auditiva no perciben alertas ambientales críticas | Detección automática con YAMNet clasifica 521 sonidos, filtrados a 7 clases de seguridad |
| Necesitan feedback inmediato en situaciones de riesgo | Vibración instantánea + registro en Room para historial |

**Valor**: La detección de alertas es una función core de seguridad — diferencia a NaturaSonic de un amplificador genérico.

## Qué

### Criterios de éxito
- [ ] TFLite Task Audio compila sin conflictos con TFLite core 2.16.1
- [ ] YAMNet model (.tflite) carga desde assets o filesDir con gestor robusto
- [ ] SoundAlertDetector procesa audio del pipeline Oboe y clasifica las 7 alertas
- [ ] Confianza > 0.3 dispara vibración + persistencia en Room
- [ ] Build limpio en las 3 arquitecturas (arm64-v8a, armeabi-v7a, x86_64)

### Comportamiento esperado

El pipeline Oboe captura audio a 48kHz. `SoundAlertDetector` obtiene buffers vía `OboeAudioEngine.getAudioBuffer()`, los resamplea a 16kHz (YAMNet espera 15600 samples = 0.975s a 16kHz), ejecuta inferencia TFLite, y si alguna de las 7 clases supera el umbral de confianza, emite una `DetectedAlert` vía StateFlow, vibra el dispositivo, y persiste un `AlertEvent` en Room.

### Casos borde

- Modelo no disponible en filesDir ni assets → detector inactivo, UI muestra estado "modelo no cargado"
- Buffer de audio menor a 15600 samples → skip silencioso
- Múltiples alertas simultáneas → priorizar la de mayor confianza
- Inferencia tarda más que el intervalo entre buffers → throttle, no acumular

---

## Contexto

### Código existente a consultar
- `app/src/main/java/.../detection/SoundAlertDetector.kt` — implementación base con Interpreter raw
- `app/src/main/java/.../data/local/entity/AlertEvent.kt` — entidad Room + enum AlertSoundClass (7 clases con yamnetIndex)
- `app/src/main/java/.../audio/OboeAudioEngine.kt` — getAudioBuffer() entrega float[] desde C++
- `app/src/main/java/.../transcription/GgmlModelManager.kt` — patrón de gestión de modelos a replicar

### Gotchas conocidas
- YAMNet inferencia es ligera (~10ms en móvil) → mantener en Kotlin, no migrar a C++
- El pipeline Oboe opera a 48kHz; YAMNet espera 16kHz → resampling necesario en Kotlin (decimación 3:1)
- `noCompress` debe incluir "tflite" para que el modelo no se comprima en el APK

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: App Android nativa
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- TFLite core 2.16.1, TFLite support 0.4.4 (ya en proyecto)
- Room, Hilt, StateFlow (ya integrados)
- Pipeline Oboe C++ (no se toca)

### ADD
- `org.tensorflow:tensorflow-lite-task-audio:0.4.4`

### REPLACE
- (ninguno)

### REMOVE
- (ninguno)

### CONFIG
- `aaptOptions.noCompress += "tflite"`

---

## Supuestos heredados

- [x] TFLite core 2.16.1 ya declarado en version catalog — verificado
- [x] TFLite support 0.4.4 ya declarado — verificado
- [x] `SoundAlertDetector.kt` ya existe con estructura base — verificado
- [x] `AlertSoundClass` enum con 7 clases y yamnetIndices correctos — verificado
- [x] `OboeAudioEngine.getAudioBuffer()` disponible para consumidores Kotlin — verificado
- [x] `tensorflow-lite-task-audio:0.4.4` es compatible con TFLite core 2.16.1 — verificado (Fase 1, build exitoso)

---

## Fuera de Alcance heredado

- Migración de YAMNet a C++ (inferencia ligera, Kotlin es suficiente)
- Descarga dinámica del modelo YAMNet desde CDN (se gestiona vía assets o filesDir)
- UI de configuración de alertas (se implementará en PRP posterior)
- Auracast / broadcast LE Audio

---

## Aprendizajes heredados de fases previas

**2026-08-10: Consumidores pesados → C++, ligeros → Kotlin**
- whisper.cpp requería thread dedicado C++ por su peso de inferencia. YAMNet (~10ms) no justifica esa complejidad. Mantener en Kotlin con `getAudioBuffer()`.

**2026-08-10: Pipeline Oboe a 48kHz — resampling por consumidor**
- Cada consumidor resamplea en su propia capa. YAMNet hará decimación 3:1 en Kotlin (no en C++).

**2026-08-03: noCompress para modelos ML**
- Los archivos de modelo (bin, tflite) deben estar en `noCompress` de aaptOptions para evitar corrupción al extraer.

---

## Plan de implementación

> Solo FASES. Las subtareas se generan al ENTRAR a cada fase (bucle-agentico).

### Fase 1: Configurar dependencias TFLite Task Audio ✅
- **Objetivo**: TFLite Task Audio disponible en el proyecto, build limpio
- **Validación**: `./gradlew clean assembleDebug` sin errores
- **Completada**: 2026-08-12 (commit 493a4f3)

### Fase 2: Gestión del modelo YAMNet ✅
- **Objetivo**: Modelo YAMNet accesible desde assets/filesDir con gestor robusto (patrón GgmlModelManager)
- **Validación**: Build exitoso con YamnetModelManager inyectado vía Hilt en SoundAlertDetector
- **Completada**: 2026-08-12

### Fase 3: Migrar SoundAlertDetector a Task Audio API ✅
- **Objetivo**: Reemplazar Interpreter raw por AudioClassifier de Task Audio
- **Validación**: Build exitoso con AudioClassifier inicializado vía createFromFileAndOptions, scoreThreshold=0.3, TensorAudio para input
- **Completada**: 2026-08-12

### Fase 4: Integración con pipeline de audio ✅
- **Objetivo**: SoundAlertDetector recibe audio del pipeline Oboe, detecta alertas, vibra y persiste
- **Validación**: Build exitoso con pipeline completo: ring buffer C++ (48kHz, 1s) → JNI → decimación 3:1 Kotlin → AudioClassifier → alertas
- **Completada**: 2026-08-12

### Fase 5: Validación final
- **Objetivo**: Sistema completo funcionando, build limpio, docs actualizados
- **Validación**:
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] `./gradlew lint` limpio
  - [ ] Compilación nativa en 3 arquitecturas
  - [ ] Criterios de éxito cumplidos
  - [ ] AGENTS.md y CLAUDE.md actualizados

---

## Aprendizajes

> Esta sección crece con cada error durante la ejecución.

### 2026-08-12: JDK Adoptium requiere Windows-ROOT trust store para descargar dependencias
- **Error**: `./gradlew assembleDebug` falla con `PKIX path building failed` al resolver `tensorflow-lite-task-audio:0.4.4` desde Google Maven, Maven Central y Alphacephei. Las dependencias cacheadas funcionaban pero cualquier nueva fallaba por SSL.
- **Fix**: Agregar `-Djavax.net.ssl.trustStoreType=Windows-ROOT` a `org.gradle.jvmargs` en `gradle.properties`. Esto usa el trust store de Windows en lugar del cacerts del JDK Adoptium.
- **Aplicar en**: Cualquier nueva dependencia que se agregue al proyecto. Si se cambia de JDK, verificar que el trust store siga funcionando.

---

*PRP en ejecución — Fase 4 completada, Fase 5 pendiente.*
