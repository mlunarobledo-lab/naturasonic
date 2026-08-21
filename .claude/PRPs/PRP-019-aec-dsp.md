# PRP-019: Cancelación de Eco Acústico Nativo (AEC — Acoustic Echo Cancellation)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-20
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`, Fase 3 (Motor PSAP y Procesamiento de Señal). El brief identifica `AcousticEchoCanceler` y `NoiseSuppressor` como AudioEffects nativos del sistema, y menciona complementar con AEC de openMHA o WebRTC AEC3 cuando el AEC del sistema sea insuficiente. Este PRP implementa un filtro adaptativo NLMS propio en C++ como AEC por software, con la opción de usar el `AcousticEchoCanceler` del sistema como alternativa.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic cancele el eco acústico producido cuando el audio amplificado que sale por los auriculares se filtra de vuelta al micrófono del dispositivo. Necesito tres opciones configurables: un filtro adaptativo NLMS propio implementado en C++ dentro del pipeline de audio (máximo control, funciona en cualquier dispositivo), el AcousticEchoCanceler nativo del sistema Android (cuando esté disponible en el hardware), y desactivado. El AEC debe procesar el audio capturado ANTES de que entre al pipeline DSP (amplificación, noise gate, EQ) para que Whisper, YAMNet y el Voice Analyzer reciban señal limpia de eco.

## Por Qué

| Problema | Solución |
|----------|----------|
| En PSAP con auriculares, el audio amplificado se filtra del speaker del auricular al micrófono del teléfono, creando un loop de retroalimentación (eco) que degrada la calidad del audio y puede generar feedback audible | Filtro adaptativo NLMS que estima y resta la componente de eco de la señal capturada usando la señal de salida como referencia |
| El AEC del sistema Android (`AcousticEchoCanceler`) no está disponible en todos los dispositivos, y cuando lo está, su calidad varía por fabricante | AEC por software propio garantiza cancelación de eco consistente independientemente del hardware, con fallback al AEC del sistema cuando el usuario lo prefiera |
| Whisper, YAMNet y VoiceAnalyzer reciben audio contaminado con eco, degradando la precisión de transcripción, detección de alertas y métricas de voz | AEC procesa la señal ANTES del pipeline DSP — todos los consumidores downstream reciben audio limpio |
| No hay forma de que el usuario configure o desactive el AEC según su entorno (con/sin auriculares, speaker externo, etc.) | Pantalla de configuración con 3 modos (Desactivado / Software / Sistema) persistida en DataStore |

**Valor**: Cancelación de eco completa cierra el loop de calidad de audio PSAP — NaturaSonic pasa de amplificador pasivo a sistema de audio inteligente que se auto-corrige.

## Qué

### Criterios de éxito
- [x] `AecFilter` clase C++ con algoritmo NLMS adaptativo: buffer de referencia circular, pesos adaptativos, step size configurable
- [x] Integración en `onAudioReady`: AEC procesa `captureBuffer_` ANTES de `processor_.process()`, referencia alimentada DESPUÉS de procesamiento+limitador
- [x] Modo AEC atómico en `NaturaSonicEngine` (OFF=0, SOFTWARE=1, SYSTEM=2) con setter JNI `nativeSetAecMode(int)`
- [x] Session ID de Oboe expuesto vía JNI para `AcousticEchoCanceler` del sistema
- [x] `AudioSessionManager` existente reutilizado para crear/destruir `AcousticEchoCanceler` según modo y disponibilidad, con degradación elegante a software si no está disponible
- [x] `AecSettingsScreen` Compose con card selector (3 modos), indicador de disponibilidad del AEC del sistema, persistido en DataStore
- [x] Ruta `AEC_SETTINGS` en NavGraph, accesible desde Settings → "Cancelación de eco"
- [x] Build exitoso sin cambios en dependencias nativas ni AGP

### Comportamiento esperado

1. Por defecto, AEC está desactivado (modo OFF) — el pipeline funciona como hasta ahora.
2. El usuario navega a Settings → "Cancelación de eco" → abre `AecSettingsScreen`.
3. El usuario selecciona uno de los 3 modos:
   - **Desactivado**: Sin procesamiento AEC. El pipeline DSP recibe la señal cruda del micrófono.
   - **Software (NLMS)**: El filtro adaptativo C++ estima la componente de eco usando la señal de salida como referencia y la resta de la señal capturada. Los pesos del filtro convergen en ~500ms a las condiciones acústicas del entorno.
   - **Sistema (Android API)**: Se activa `AcousticEchoCanceler` del sistema sobre la sesión de audio de Oboe. Si no está disponible, se muestra un mensaje y se activa automáticamente el modo Software como fallback.
4. La preferencia se persiste en DataStore. Al reiniciar AudioService, el modo se restaura.
5. El cambio de modo es inmediato — no requiere reiniciar los streams de Oboe (excepto AEC del sistema que se adjunta/desadjunta vía AudioEffect).

### Casos borde

- **AEC del sistema no disponible**: `AcousticEchoCanceler.isAvailable()` retorna false. La UI muestra "No disponible en este dispositivo" y deshabilita la opción, sugiriendo el modo Software.
- **Output muteado (BT desconectado)**: No hay señal de salida → no hay eco → el filtro NLMS no se alimenta con referencia y produce zero-correction (pasa la señal sin modificar). Correcto: si no hay output, no hay eco que cancelar.
- **Auriculares con cancelación de ruido activa (ANC)**: El ANC de los auriculares reduce la fuga acústica. El filtro NLMS converge a pesos cercanos a cero si no detecta eco — no degrada la señal.
- **Cambio de entorno acústico**: Los pesos NLMS se adaptan continuamente. Al cambiar de entorno (ej: de una sala cerrada a exterior), el filtro reconverge en ~500ms.
- **AudioService detenido**: Los pesos del filtro se pierden (volátiles). Al reiniciar, el filtro reconverge desde cero.
- **Eco mode activo**: AEC no se throttlea — opera sample-a-sample en el audio thread.
- **Session ID de Oboe = 0**: Oboe puede retornar session ID 0 si el stream se abre sin session asignada. En ese caso, `AcousticEchoCanceler` no se puede adjuntar → fallback a software.

---

## Contexto

### Documentación externa
- [Android AcousticEchoCanceler API](https://developer.android.com/reference/kotlin/android/media/audiofx/AcousticEchoCanceler) — AudioEffect del sistema para cancelación de eco, requiere session ID del AudioRecord/AudioTrack
- [NLMS Algorithm (Wikipedia)](https://en.wikipedia.org/wiki/Least_mean_squares_filter#Normalized_least_mean_squares_filter_(NLMS)) — Filtro adaptativo normalizado LMS: convergencia rápida, estabilidad numérica, complejidad O(N) por sample
- [WebRTC AEC3 design](https://webrtc.googlesource.com/src/+/refs/heads/main/modules/audio_processing/aec3/) — Referencia de diseño industrial para AEC en tiempo real (no se importa como dependencia, solo referencia)

### Código existente a consultar
- `app/src/main/cpp/audio_processor.h` — EqSnapshot double-buffer pattern, integración de parámetros en el pipeline
- `app/src/main/cpp/audio_processor.cpp` — `process()` pipeline: amplification → noise gate → EQ. AEC debe ejecutarse ANTES de este chain
- `app/src/main/cpp/oboe_engine.cpp` — `onAudioReady`: punto de integración del AEC entre `read()` y `processor_.process()`
- `app/src/main/cpp/oboe_engine.h` — `NaturaSonicEngine`: agregar `AecFilter`, modo atómico, getter de session ID
- `app/src/main/cpp/native-lib.cpp` — Patrones JNI: setter simple (`nativeSetNoiseGateMode`), getter numérico (`nativeGetLatencyStats`)
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — Kotlin JNI wrappers
- `app/src/main/java/com/naturasonic/app/audio/AudioSessionManager.kt` — Posible referencia de gestión de AudioEffect sessions
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore: patrón `intPreferencesKey` + Flow + setter (ver `noiseGateMode`)
- `app/src/main/java/com/naturasonic/app/ui/screens/anc/AncControlScreen.kt` — Patrón de pantalla con selector de modos (RadioButton o similar)
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — Lifecycle integration: observer con `combine` + `collect`

### Gotchas conocidas
- **NLMS en audio thread debe ser O(N) estricto**: El callback de Oboe tiene budget de ~5ms. Con 1024 taps y ~256 frames por callback, el coste es ~256K operaciones — aceptable pero hay que validar con ATrace.
- **La referencia llega con un frame de delay**: El output del frame actual alimenta la referencia para el AEC del frame SIGUIENTE. Este delay de 1 frame (~5ms a 256 frames/48kHz) es menor que el delay acústico real del eco (>10ms típico con auriculares), por lo que el filtro NLMS lo compensa naturalmente en los primeros taps.
- **InputPreset::Unprocessed y AcousticEchoCanceler**: Oboe usa `InputPreset::Unprocessed` que desactiva el preprocesamiento del HAL. El `AcousticEchoCanceler` es un AudioEffect de nivel superior que puede adjuntarse manualmente al session ID. En algunos dispositivos funciona, en otros no (el HAL puede ignorar el effect). Siempre ofrecer fallback a software.
- **El AecFilter escribe y lee en el audio thread (single-writer)**: Los pesos del filtro se actualizan in-place durante `onAudioReady`. No hay competencia de threads — solo el audio callback toca el estado del filtro. El único parámetro que se cambia desde otro thread es el `aecMode_` (atómico).
- **El modo AEC es un `std::atomic<int>` simple, NO un campo de EqSnapshot**: No necesita el patrón double-buffer porque: (1) es un valor escalar sin interdependencia con otros parámetros, (2) no afecta coeficientes biquad, (3) se lee una vez al inicio de cada callback.

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Toda la infraestructura nativa actual
- Todas las dependencias — congeladas
- AGP 8.7.3 — congelado
- NDK / CMake / Oboe — congelados
- Pipeline Oboe C++ — se extiende, no se reemplaza

### ADD
- Ninguna dependencia nueva — NLMS implementado en C++ estándar, AcousticEchoCanceler es API del framework Android

### REMOVE / REPLACE / CONFIG
- Ninguno

### Refinamientos a la Directiva durante este PRP
- Ninguno anticipado.

---

## Supuestos heredados

- [x] El dispositivo tiene Android 10+ (API 29+) como mínimo
- [x] AudioService foreground service corriendo durante uso activo
- [x] Pipeline Oboe a 48kHz mono funcional

### Supuestos adicionales (específicos de este PRP)
- [ ] La fuga acústica (acoustic leakage) del auricular al micrófono es suficiente para generar eco audible — si no hay fuga, el filtro NLMS converge a cero y pasa la señal sin modificar (no genera artefactos)
- [ ] El budget de CPU del audio callback (5ms) soporta 1024 operaciones NLMS por sample (256K ops por frame de 256 muestras)
- [ ] `AcousticEchoCanceler.isAvailable()` refleja correctamente la capacidad real del dispositivo

---

## Fuera de Alcance heredado

- Audiograma clínico
- Versión iOS
- Backend en la nube
- Integración con audífonos clínicos FDA
- VoIP / llamadas de voz

### Fuera de Alcance adicional (específico de este PRP)

- **AEC estéreo**: El pipeline es mono — AEC opera en 1 canal. AEC multi-canal es scope futuro.
- **Filtro de partición por bloques (PBFDAF)**: El NLMS opera en dominio temporal. Implementar AEC en dominio frecuencial (FFT-based) para filtros muy largos (>4096 taps) queda fuera de scope — 1024 taps son suficientes para eco de auriculares.
- **Detección automática de eco (DTD — Double-Talk Detection)**: En v1, el NLMS opera continuamente. Una DTD sofisticada que pause la adaptación durante double-talk es optimización futura.
- **Persistencia de pesos del filtro**: Los pesos NLMS son volátiles (sesión). Guardar/restaurar pesos entre sesiones de audio es scope futuro.
- **Ajuste de taps/step-size por el usuario**: Los parámetros del filtro son fijos internamente. Una pantalla avanzada de "sintonización AEC" es scope futuro.

---

## Aprendizajes heredados

- **2026-08-13 (PRP-007)**: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe. Aplicable: AEC mode NO usa este patrón (es un scalar atómico simple sin interdependencias). El filtro NLMS es single-writer (solo audio thread).
- **2026-08-03 (CLAUDE.md)**: openMHA no es viable como dependencia directa. Aplicable: los algoritmos AEC se implementan directamente en C++ sobre el pipeline Oboe, no se importa WebRTC AEC3 ni openMHA como dependencia.
- **2026-08-17 (PRP-017)**: Offsets espaciales EQ deben integrarse en computeEqCoefficients. Lección general: el procesamiento debe ocurrir en el punto correcto del pipeline. Aplicable: AEC ANTES del chain DSP, referencia DESPUÉS.
- **2026-08-17 (PRP-016)**: APIs @SystemApi requieren reflexión. AcousticEchoCanceler NO es @SystemApi — es API pública desde API 16. No requiere reflexión.

---

## Plan de implementación

> IMPORTANTE: solo FASES aquí. Las subtareas se generan al ENTRAR a cada fase.

### Fase 1: AecFilter C++ + Integración en onAudioReady
- **Objetivo**: Crear clase `AecFilter` (aec_filter.h/cpp) con algoritmo NLMS adaptativo: buffer de referencia circular de salida, pesos adaptativos (1024 taps), step size 0.05. Integrar en `onAudioReady` de NaturaSonicEngine: `aecFilter_.process()` ANTES de `processor_.process()`, `aecFilter_.feedReference()` DESPUÉS de VolumeLimiter. Agregar `std::atomic<int> aecMode_` en NaturaSonicEngine con setter `setAecMode(int)`. Agregar al CMakeLists.txt.
- **Validación**: Build C++ exitoso. Con AEC_SOFTWARE activo, el filtro procesa sin artefactos (no introduce clipping ni distorsión). Con AEC_OFF, el pipeline se comporta idénticamente al estado actual.

### Fase 2: JNI Bridge + Kotlin Wrappers + SystemAecManager
- **Objetivo**: Exponer `setAecMode(int)` y `getAudioSessionId()` vía JNI. Kotlin wrappers en OboeAudioEngine. Crear `SystemAecManager` singleton que gestione `AcousticEchoCanceler` del sistema: detección de disponibilidad, creación/destrucción según modo, fallback a software si no disponible.
- **Validación**: JNI setAecMode invocable desde Kotlin. SystemAecManager reporta disponibilidad correctamente.

### Fase 3: AecSettingsScreen + ViewModel + DataStore + Navegación
- **Objetivo**: Crear `AecSettingsScreen` Compose con RadioButton group (Desactivado / Software NLMS / Sistema Android), indicador de disponibilidad del AEC del sistema, card informativa. Crear `AecSettingsViewModel`. Agregar `aecMode` a DataStore (intPreferencesKey). Ruta `AEC_SETTINGS` en NavGraph. Botón "Cancelación de eco" en SettingsScreen.
- **Validación**: Pantalla renderiza. Selección persiste en DataStore. Cambio de modo propaga al engine.

### Fase 4: Integración AudioService + Validación Final
- **Objetivo**: AudioService observa `aecMode` de DataStore y propaga a engine + SystemAecManager. Gestión de lifecycle del AcousticEchoCanceler (create en startAudio, release en stopAudio). Validación de build y performance.
- **Validación**:
  - [ ] `./gradlew assembleDebug` exitoso
  - [ ] No hay cambios en dependencias nativas ni AGP
  - [ ] AEC software no introduce latencia observable (ATrace < 50µs para 256 frames con 1024 taps)
  - [ ] Pipeline se comporta idénticamente con AEC_OFF vs sin el PRP

---

## Aprendizajes

> Esta sección crece con cada error.

### 2026-08-20: AudioSessionManager existente cubre SystemAecManager
- **Error**: El PRP planificaba crear un `SystemAecManager` nuevo. Al inspeccionar el codebase, `AudioSessionManager` ya gestiona `AcousticEchoCanceler` con `setAecEnabled(sessionId, enabled)`, detección de disponibilidad (`isAecAvailable`), y release limpio.
- **Fix**: Se reutilizó `AudioSessionManager` en lugar de crear una nueva clase. El ViewModel y AudioService llaman directamente a `audioSessionManager.setAecEnabled()`.
- **Aplicar en**: Antes de crear wrappers nuevos para AudioEffects del sistema, verificar si `AudioSessionManager` ya los cubre.

---

## Anti-patrones

- No usar double-buffer para AEC mode — es un scalar atómico, no un struct multi-campo como EqSnapshot
- No poner los pesos del filtro NLMS fuera del audio thread — son single-writer, el audio callback es el único que los modifica
- No importar WebRTC AEC3 ni openMHA como dependencia — implementar NLMS directamente
- No reiniciar streams de Oboe para cambiar el modo AEC — el switch debe ser inmediato
- No asumir que `AcousticEchoCanceler` funciona en todos los dispositivos con InputPreset::Unprocessed
- No hacer AEC sobre la señal post-EQ — debe ser ANTES del pipeline DSP, sobre la señal cruda capturada

---

*PRP auto-aprobado 2026-08-20. Ejecución completada 2026-08-20 por bucle-agentico.*
