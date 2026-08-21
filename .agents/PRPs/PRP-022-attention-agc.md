# PRP-022: Sistema de Ajuste Dinamico de Ganancia Basado en la Atencion (Attention-Based AGC)

> **Estado**: PENDIENTE
> **Fecha**: 2026-08-21
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la vision PSAP del brief con un controlador
> inteligente que conecta reactivamente los motores ML (Whisper + YAMNet) con el nucleo DSP C++
> para ajustar la ecualizacion en funcion de lo que el entorno auditivo requiere.

---

## Objetivo

Quiero que NaturaSonic ajuste automaticamente su ecualizacion segun lo que esta pasando en el audio: si hay alguien hablando, que refuerce las frecuencias de inteligibilidad del habla para que se escuche mas claro; si se detecta una alerta critica distante (sirena, alarma, timbre), que atenue el ruido de fondo general para que la alerta resalte. Todo sin que yo tenga que tocar nada — la app escucha, decide, y ajusta.

## Por Que

| Problema | Solucion |
|----------|----------|
| El EQ estatico no distingue contexto: la misma curva aplica cuando hay voz que cuando hay ruido ambiental | AttentionController observa ML en tiempo real y aplica offsets EQ contextuales |
| Las alertas criticas distantes se pierden en el ruido de fondo amplificado | Atenuacion selectiva de bandas no-habla destaca la alerta sobre el ruido |
| El usuario debe cambiar perfil manualmente segun el entorno | Ajuste automatico y transparente — zero interaccion |

**Valor**: Inteligencia adaptativa que diferencia NaturaSonic de un amplificador generico. El dispositivo PSAP responde al entorno como lo haria el sistema auditivo natural: potencia lo importante, suprime lo irrelevante.

## Que

### Criterios de exito
- [ ] `EqSnapshot` extendido con `float attentionGainOffsets[kMaxEqBands]` y `bool attentionAgcEnabled`
- [ ] Offsets integrados en `computeEqCoefficients` como tercer sumando aditivo (base + spatial + attention), clamped a [-12, 12] dB
- [ ] Setters C++ `setAttentionAgcEnabled(bool)` y `setAttentionGainOffsets(float*, int)` con patron copy-modify-swap
- [ ] JNI bridge `nativeSetAttentionAgcEnabled` + `nativeSetAttentionGainOffsets` en native-lib.cpp
- [ ] `OboeAudioEngine.kt` wrapper methods para ambos JNI calls
- [ ] `AttentionController` singleton Hilt observa `WhisperTranscriptionEngine.isTranscribing` + `SoundAlertDetector.latestAlert`
- [ ] Estado SPEECH: cuando Whisper transcribe activamente, boost de bandas 3/4/5 (1kHz-4kHz) con peso diferenciado (70%/100%/80% del speechBoostDb)
- [ ] Estado ALERT: cuando YAMNet detecta alerta con timestamp < 3s, atenuacion de bandas 0-2 y 6-9 por alertAttenuationDb
- [ ] Prioridad: ALERT > SPEECH > IDLE; decay exponencial al transicionar a IDLE (~500ms)
- [ ] DataStore: `attentionAgcEnabled`, `speechBoostDb` (1-6, default 3), `alertAttenuationDb` (1-8, default 4)
- [ ] AudioService observa preferencias y controla lifecycle del AttentionController
- [ ] `AttentionAgcScreen` Compose con toggle, sliders para boost/atenuacion, indicador visual del estado actual (IDLE/SPEECH/ALERT)
- [ ] Pantalla accesible desde Settings
- [ ] Build compila sin errores (`./gradlew assembleDebug`)

### Comportamiento esperado

El usuario activa "Ajuste inteligente" desde Settings. AttentionController arranca y observa los StateFlows de Whisper y YAMNet. Cuando Whisper detecta voz activa (isTranscribing = true con texto actualizandose), el controlador computa offsets positivos en las bandas de inteligibilidad del habla (1kHz, 2kHz, 4kHz) y los propaga al engine C++ via JNI. Los offsets se suman a las gains base del perfil activo (aditivos, no destructivos) en `computeEqCoefficients`. Cuando la voz cesa, los offsets decaen exponencialmente a cero en ~500ms.

Si YAMNet detecta una alerta critica (sirena, alarma de humo, timbre, etc.), el controlador cambia a modo ALERT: atenua las bandas no-habla (125Hz-500Hz y 6kHz-12kHz) para reducir el ruido de fondo y dejar que la alerta destaque. ALERT tiene prioridad sobre SPEECH. Cuando la alerta caduca (>3s sin nueva deteccion), vuelve al estado previo (SPEECH si hay voz, IDLE si no).

El pipeline C++ no sabe nada de ML — solo recibe offsets float y los suma. La inteligencia vive 100% en Kotlin.

### Casos borde

- **Whisper y YAMNet ambos inactivos** (modelo no cargado, eco mode throttle): AttentionController queda en IDLE, offsets en cero — sin overhead.
- **Eco mode activo**: AttentionController respeta los intervalos extendidos de Whisper/YAMNet — solo reacciona cuando los motores producen datos, no tiene su propio polling.
- **Head tracking + attention simultaneos**: Ambos offsets son aditivos en `computeEqCoefficients`. El clamp a [-12, 12] dB previene overflows.
- **Perfil cambia mientras attention esta activo**: El `applyProfile` sobreescribe gains base pero preserva `attentionGainOffsets` y `attentionAgcEnabled` (estan en EqSnapshot, separados de gains).
- **Alerta repetida rapida (multiple detecciones en <3s)**: El timeout se renueva con cada deteccion — el estado ALERT se mantiene.

---

## Contexto

### Codigo existente a consultar
- `app/src/main/cpp/audio_processor.h` — `EqSnapshot` con `spatialGainOffsets` (patron a replicar)
- `app/src/main/cpp/audio_processor.cpp` — `computeEqCoefficients` y `setHeadTrackingAngles` (patron copy-modify-swap)
- `app/src/main/cpp/native-lib.cpp` — JNI bridge existente (seccion head tracking como modelo)
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — wrappers Kotlin del engine
- `app/src/main/java/com/naturasonic/app/transcription/WhisperTranscriptionEngine.kt` — `isTranscribing: StateFlow<Boolean>`, `currentText: StateFlow<String>`
- `app/src/main/java/com/naturasonic/app/detection/SoundAlertDetector.kt` — `latestAlert: StateFlow<DetectedAlert?>`
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — patron de observer/lifecycle para features reactivas
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore preferences

### Gotchas conocidas
- **Los offsets deben integrarse en `computeEqCoefficients`, NO en `applyEqualizer`** (aprendizaje 2026-08-17 en CLAUDE.md: los coeficientes biquad se pre-computan, offsets downstream se ignoran).
- **`applyProfile` sobreescribe todo el snapshot** — los offsets de attention deben preservarse (copiar al writeIdx antes del overwrite, o recomputar desde el AttentionController tras cada applyProfile).
- **WhisperTranscriptionEngine no es singleton Hilt** — se crea via HiltViewModel en TranscriptionViewModel. El AttentionController necesita acceso directo a la instancia del engine. Verificar si se puede inyectar como singleton o si hay que observar indirectamente via un StateFlow compartido.

### Modelo de datos

No hay cambios en Room. Solo DataStore preferences nuevas.

---

## Directiva de Stack heredada

> Copia integra del brief origen.

### Clasificacion
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Ninguno del stack web Praxis

### ADD
- No se agregan dependencias nuevas. Todo se construye con las existentes (Kotlin, Hilt, Compose, Oboe/C++, DataStore).

### REPLACE
- N/A (ya reemplazado en PRP-001)

### REMOVE
- N/A

### CONFIG
- N/A

### Refinamientos a la Directiva durante este PRP
- Dependencias congeladas: no modificar AGP, Gradle plugin, ni versiones de deps base.

---

## Supuestos heredados

- [x] Pipeline Oboe con AudioProcessor double-buffer funcional (verificado en PRPs 001-021)
- [x] `spatialGainOffsets` en EqSnapshot integrado en `computeEqCoefficients` (verificado en PRP-017)
- [x] WhisperTranscriptionEngine expone `isTranscribing` y `currentText` como StateFlow (verificado)
- [x] SoundAlertDetector expone `latestAlert` como StateFlow (verificado)
- [x] DataStore `UserPreferences` acepta nuevos campos sin migracion (verificado)

### Supuestos adicionales (especificos de este PRP)
- [ ] WhisperTranscriptionEngine puede inyectarse como singleton o su estado puede observarse desde AttentionController (verificar en Fase 2)

---

## Fuera de Alcance heredado

- No se modifica la logica interna de Whisper ni YAMNet
- No se agrega ML nuevo — se observan los motores existentes
- No se cambia Room schema ni migraciones
- No se modifica el pipeline de audio C++ mas alla de agregar offsets al EqSnapshot
- No se implementa aprendizaje automatico de umbrales (los ajustes son manuales via sliders)

---

## Aprendizajes heredados de fases previas

**2026-08-17: Offsets espaciales EQ deben integrarse en computeEqCoefficients, no en applyEqualizer**
- Aplicar en: Los `attentionGainOffsets` siguen el mismo patron — se suman en `computeEqCoefficients` junto con `spatialGainOffsets`.

**2026-08-13: Double-buffer copy-modify-swap como patron canonico para parametros DSP thread-safe**
- Aplicar en: Los setters `setAttentionAgcEnabled` y `setAttentionGainOffsets` siguen el patron copy-modify-swap canonico.

---

## Plan de implementacion

### Fase 1: Extension EqSnapshot C++ + JNI Bridge
- **Objetivo**: Agregar `attentionGainOffsets[kMaxEqBands]` y `bool attentionAgcEnabled` a EqSnapshot. Setters copy-modify-swap en AudioProcessor. Integracion aditiva en `computeEqCoefficients`. JNI bridge + wrappers Kotlin.
- **Validacion**: `./gradlew assembleDebug` compila limpio. Los nuevos metodos JNI existen y son invocables desde Kotlin.

### Fase 2: AttentionController Kotlin + DataStore
- **Objetivo**: Singleton Hilt que observa StateFlows de Whisper (isTranscribing) y YAMNet (latestAlert). Maquina de estados IDLE/SPEECH/ALERT con prioridad ALERT > SPEECH. Computa offsets por banda y los propaga al engine C++. Preferencias en DataStore (enabled, speechBoostDb, alertAttenuationDb).
- **Validacion**: AttentionController se inyecta en AudioService. Observa cambios de estado de ML engines. Propaga offsets coherentes al engine.

### Fase 3: Integracion AudioService + Lifecycle
- **Objetivo**: AudioService observa `attentionAgcEnabled` de DataStore, arranca/detiene AttentionController. Cleanup en stopAudio/onDestroy.
- **Validacion**: Toggle de attention AGC persiste entre sesiones. Controller arranca/detiene correctamente con el servicio de audio.

### Fase 4: UI Compose — AttentionAgcScreen
- **Objetivo**: Pantalla con toggle, sliders de speechBoostDb y alertAttenuationDb, indicador visual del estado actual (IDLE/SPEECH/ALERT con colores). ViewModel. Navegacion desde Settings.
- **Validacion**: Pantalla accesible desde Settings. Sliders modifican DataStore. Indicador refleja estado real del controller.

### Fase 5: Validacion final + housekeeping
- **Objetivo**: Build limpio, PRP cerrado, CLAUDE.md actualizado.
- **Validacion**:
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] Criterios de exito cumplidos
  - [ ] Aprendizajes propagados

---

## Aprendizajes

> Esta seccion crece con cada error. El conocimiento persiste para futuros PRPs.

---

## Anti-patrones

- No crear patrones nuevos si los existentes funcionan (seguir patron de spatialGainOffsets)
- No ignorar errores de compilacion
- No hardcodear valores (usar constantes companion object)
- No commitear secrets
- No modificar el pipeline de audio mas alla de lo estrictamente necesario para los offsets

---

*PRP pendiente aprobacion. No se ha modificado codigo.*
