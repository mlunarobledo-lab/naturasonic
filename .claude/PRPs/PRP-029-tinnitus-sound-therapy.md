# PRP-029: Terapia Sonora para Tinnitus — Generador de Enmascaramiento

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-24
> **Proyecto**: NaturaSonic

---

## Origen

> Planificación directa, sin brief previo. Las 7 fases del brief `docs/BRIEF-naturasonic.md` están COMPLETADAS. Este PRP extiende NaturaSonic con una feature nueva dirigida al segmento de usuarios con tinnitus (~15% de la población), complementando la amplificación PSAP con terapia de enmascaramiento sonoro.

---

## Objetivo

Quiero que NaturaSonic ofrezca terapia sonora para tinnitus: un generador de sonidos de enmascaramiento (white noise, pink noise, brown noise, tono puro, y notch therapy) que se mezcla con el audio amplificado para aliviar el zumbido percibido. Quiero poder elegir el tipo de sonido, ajustar su volumen de forma independiente a la amplificación PSAP, configurar la frecuencia del tono o del notch para personalizar la terapia, y opcionalmente poner un timer para que se apague solo después de un rato. El generador debe funcionar tanto mixeado con la amplificación como en modo standalone (solo terapia, sin micrófono).

## Por Qué

| Problema | Solución |
|----------|----------|
| El 15% de la población sufre tinnitus (zumbido, pitido, silbido) — NaturaSonic hoy no ofrece ninguna herramienta para este segmento | TinnitusGenerator produce sonidos de enmascaramiento que alivian la percepción del tinnitus mezclándose con el audio de salida |
| Las apps de terapia de tinnitus son independientes — el usuario tiene que usar dos apps si también necesita amplificación | La terapia se integra directamente en el pipeline PSAP: el enmascaramiento se suma al audio amplificado en un solo flujo |
| La notch therapy (eliminar la frecuencia exacta del tinnitus del ruido) es una técnica clínicamente respaldada pero difícil de configurar para el usuario promedio | Selector de frecuencia intuitivo (slider 500–16000 Hz) con preview en tiempo real — el usuario ajusta hasta encontrar su frecuencia de tinnitus |
| El tinnitus empeora en silencio (al dormir, en ambientes tranquilos) — el usuario necesita un generador que funcione sin entrada de micrófono | Modo standalone: el generador produce sonido incluso sin audio de micrófono, ideal para uso nocturno o relajación |

**Valor**: Tinnitus Sound Therapy abre un segmento masivo (500M+ personas globalmente) y convierte NaturaSonic de "amplificador personal" a "plataforma completa de bienestar auditivo". Es feature premium diferenciadora que ningún PSAP competitivo gratuito ofrece integrada.

## Qué

### Criterios de éxito
- [ ] 5 tipos de sonido generados en C++ a 48kHz: white noise, pink noise, brown noise, pure tone, notch noise
- [ ] Volumen de tinnitus independiente del volumen de amplificación (0–100%, default 30%)
- [ ] Frecuencia configurable para pure tone y notch (500–16000 Hz, default 4000 Hz)
- [ ] Notch therapy: pink noise con notch de 1 octava centrado en la frecuencia seleccionada
- [ ] Sonido se mezcla con el audio amplificado después de todo el DSP processing
- [ ] Funciona en modo standalone (sin entrada de micrófono) — genera sonido en el output stream
- [ ] Timer opcional (15, 30, 60, 120 min) con auto-apagado
- [ ] UI con selector de tipo de sonido, slider de volumen, selector de frecuencia, timer
- [ ] Settings persistidos en DataStore, se restauran al reabrir
- [ ] `./gradlew assembleDebug` sin errores

### Comportamiento esperado

El usuario abre Settings → "Terapia de tinnitus". Ve un toggle maestro, un selector de tipo de sonido (cards), un slider de volumen, un selector de frecuencia (visible solo para pure tone y notch), y un timer opcional. Al activar, el sonido se genera en C++ a 48kHz y se mezcla con el output del pipeline Oboe. El volumen del tinnitus es independiente — si el usuario baja la amplificación a cero, el sonido de terapia sigue. Si el usuario activa el timer, el sonido se apaga automáticamente al expirar y muestra una notificación suave. Los settings se persisten en DataStore.

### Casos borde

- **Output muteado por desconexión BT**: el sonido de tinnitus también se silencia (respeta `outputMuted_`) — es audio de salida, no debe sonar por speaker si se desconectaron los auriculares
- **Timer expira mientras la app está en background**: AudioService detiene el generador y emite notificación informativa
- **Frecuencia del notch fuera de rango audible**: slider clampea 500–16000 Hz
- **Cambio de tipo de sonido mientras suena**: transición inmediata sin glitches (el generador resetea fase/estado del filtro)
- **WDRC/ANC/EQ activos al mismo tiempo**: el tinnitus se mezcla DESPUÉS de todo el DSP — no pasa por compresión, EQ, ni ANC

---

## Contexto

### Documentación externa
- Tinnitus sound therapy: enmascaramiento completo (broadband noise), parcial (matching), y notch therapy (Okamoto et al., 2010: "Listening to tailor-made notched music reduces tinnitus loudness")
- Tipos de ruido: white (flat spectral density), pink (1/f, -3 dB/octava), brown (1/f², -6 dB/octava)
- Notch therapy: ruido broadband con notch-filter centrado en la frecuencia percibida del tinnitus — reduce activación cortical en esa banda

### Código existente a consultar
- `app/src/main/cpp/oboe_engine.h/cpp` — punto de inserción en `onAudioReady`, miembro `TinnitusGenerator`
- `app/src/main/cpp/audio_processor.h` — patrón de filtros biquad para notch filter
- `app/src/main/cpp/wdrc_compressor.h` — patrón de generación por muestra en audio thread
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — patrón de observer coroutine
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — patrón DataStore
- `app/src/main/java/com/naturasonic/app/ui/screens/wdrc/WdrcScreen.kt` — patrón UI con toggle + selector + slider

### Gotchas conocidas
- El generador DEBE producir samples a 48kHz — misma tasa que el pipeline Oboe
- Pink noise requiere filtro de conformación espectral (Voss-McCartney o IIR), no se puede generar solo con PRNG
- El notch filter debe tener Q suficiente para una octava de ancho (~Q=1.41 para -3dB a ±½ octava)
- El sonido de tinnitus se suma al output DESPUÉS de VolumeLimiter — debe tener su propio limitador de nivel para no exceder 85 dB cuando se combina con audio amplificado
- Modo standalone requiere que `onAudioReady` genere tinnitus audio incluso cuando `inputStream_->read()` no devuelve datos

### Modelo de datos

No aplica Room — todas las preferencias en DataStore:
- `tinnitusEnabled`: Boolean (default false)
- `tinnitusSoundType`: Int (0=WHITE, 1=PINK, 2=BROWN, 3=PURE_TONE, 4=NOTCH)
- `tinnitusVolume`: Float (0.0–1.0, default 0.3)
- `tinnitusFrequencyHz`: Float (500–16000, default 4000)
- `tinnitusTimerMinutes`: Int (0=off, 15, 30, 60, 120)

---

## Directiva de Stack heredada

> No hay brief origen activo — las 7 fases están COMPLETADAS. Stack del proyecto: Kotlin + Jetpack Compose + Oboe C++ NDK (Compatibilidad Praxis: REPLACE). Ver `CLAUDE.md` del proyecto.

### ADD
- `tinnitus_generator.h/cpp` — nuevo módulo C++ de generación de sonido
- 5 keys en DataStore (tinnitusEnabled, tinnitusSoundType, tinnitusVolume, tinnitusFrequencyHz, tinnitusTimerMinutes)
- `TinnitusScreen` + `TinnitusViewModel` — nueva pantalla Compose

### REMOVE
- Nada

### CONFIG
- `CMakeLists.txt` — agregar `tinnitus_generator.cpp` a la lista de sources

---

## Supuestos heredados

> No hay supuestos heredados formales — todas las fases del brief están COMPLETADAS.

- [ ] Pipeline Oboe operativo con `onAudioReady` callback funcionando a 48kHz
- [ ] `onAudioReady` puede generar output incluso sin datos válidos de inputStream (rama else ya existe con memset zero)
- [ ] AudioService con patrón de observer coroutines + `reapplyAllPreferences()`
- [ ] UserPreferences DataStore con patrón Flow + suspend setter

### Supuestos adicionales (específicos de este PRP)
- [ ] La suma de señal de tinnitus + señal amplificada no requiere re-limitar a 85 dB si el volumen de tinnitus está clampeado internamente a un nivel seguro (< -20 dBFS a volumen 100%)
- [ ] Pink noise generado con filtro IIR de 3 etapas (Paul Kellet) es suficiente para calidad perceptual de terapia
- [ ] Un notch biquad estándar con Q~1.41 produce un notch de ~1 octava adecuado para notch therapy

---

## Fuera de Alcance

- **Frecuency matching automático**: detección automática de la frecuencia del tinnitus del usuario (requeriría audiometría de tinnitus — PRP separado)
- **Binaural beats**: generación de batidos binaurales entre oídos requiere output stereo — el pipeline es mono
- **Sonidos de naturaleza / ambientales**: olas del mar, lluvia, pájaros — son assets de audio pregrabados, no generación procedural
- **Sesiones de terapia guiada**: protocolos clínicos de habituación tipo TRT (Tinnitus Retraining Therapy) con cronograma
- **Persistencia de sesiones en Room**: historial de uso de terapia — no se implementa en este PRP
- **Integración con audiograma para auto-seleccionar frecuencia de notch**: mejora futura

---

## Aprendizajes heredados de fases previas

**2026-08-13: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe**
- Para el TinnitusGenerator, los parámetros son simples (tipo de sonido, volumen, frecuencia) — `std::atomic` individuales son suficientes (no necesita snapshot double-buffer). Solo el notch filter coefficients necesitan protección, y se pueden pre-computar en el setter con mutex simple (no está en audio thread hot path — se computan una vez al cambiar frecuencia).

**2026-08-24: Pipeline WDRC validó que módulos DSP nuevos se integran limpiamente en onAudioReady**
- El patrón de inserción es consistente: nuevo miembro en NaturaSonicEngine, `process()` en la cadena, atomics para control, JNI bridge, observer en AudioService. El TinnitusGenerator sigue el mismo patrón pero con una diferencia clave: se SUMA al output en lugar de procesar in-place el captureBuffer.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agentico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: TinnitusGenerator C++ + JNI bridge
- **Objetivo**: Crear `tinnitus_generator.h/cpp` con generadores de white/pink/brown noise, pure tone (sine wave), y notch noise (pink + biquad notch filter). Atomics para enabled, soundType, volume, frequencyHz. Método `generate(float* output, int numFrames)` que produce samples y los suma al buffer de output. Integrar en `NaturaSonicEngine::onAudioReady` DESPUÉS de VolumeLimiter y ANTES de outputMuted check — suma al output, no procesa captureBuffer. Agregar al `CMakeLists.txt`. Crear funciones JNI en `native-lib.cpp`. Agregar `external fun` en `OboeAudioEngine.kt`.
- **Validación**:
  - [ ] 5 tipos de sonido generados correctamente a 48kHz
  - [ ] Pink noise con conformación espectral Paul Kellet (no white noise re-etiquetado)
  - [ ] Notch filter biquad con Q~1.41 para ancho de ~1 octava
  - [ ] Volumen atómico independiente de la amplificación PSAP
  - [ ] Sonido se suma al output después de VolumeLimiter
  - [ ] Funciona en modo standalone (genera audio incluso sin input válido)
  - [ ] Funciones JNI registradas y wrappers Kotlin funcionales
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: UserPreferences + AudioService integration + Timer
- **Objetivo**: Agregar 5 keys a DataStore. Crear observer coroutine en AudioService que propague cambios al engine C++. Implementar timer Kotlin (coroutine delay) que auto-desactiva el generador al expirar. Extender `reapplyAllPreferences()`.
- **Validación**:
  - [ ] 5 Flow + 5 suspend setters en UserPreferences
  - [ ] Observer coroutine propaga cambios al engine
  - [ ] Timer auto-desactiva el generador al expirar
  - [ ] `reapplyAllPreferences()` incluye tinnitus params
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: TinnitusScreen UI + ViewModel + NavGraph + Settings entry
- **Objetivo**: Crear `TinnitusViewModel` con UserPreferences. Crear `TinnitusScreen` Compose con toggle maestro, selector de tipo de sonido (cards con icono y descripción), slider de volumen (0–100%), selector de frecuencia (slider 500–16000 Hz, visible solo para pure tone y notch), selector de timer (chips: Off, 15, 30, 60, 120 min), indicador de timer activo (countdown), card informativa sobre terapia de tinnitus + disclaimer PSAP. Integrar ruta en NavGraph y entrada "Terapia de tinnitus" en SettingsScreen.
- **Validación**:
  - [ ] TinnitusScreen renderiza todos los controles
  - [ ] Selector de frecuencia visible solo para pure tone y notch
  - [ ] Timer muestra countdown cuando activo
  - [ ] Navegación Settings → Terapia de tinnitus funciona
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 4: Validación final
- **Objetivo**: Sistema funcionando end-to-end con generación de sonidos de tinnitus
- **Validación**:
  - [ ] Criterios de éxito cumplidos
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] 5 tipos de sonido producen audio perceptualmente diferente
  - [ ] Volumen de tinnitus no afecta volumen de amplificación y viceversa
  - [ ] Timer auto-desactiva correctamente
  - [ ] Coexistencia con WDRC, ANC, EQ, TransientLimiter verificada
  - [ ] Output muteado (BT desconectado) silencia tinnitus también
  - [ ] `reapplyAllPreferences()` re-aplica tinnitus settings tras engine restart

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

**2026-08-24: Ejecución limpia sin errores de build en las 4 fases**
- 3 builds consecutivos exitosos (Fase 1: C++ + JNI, Fase 2: DataStore + AudioService, Fase 3: UI Compose)
- TinnitusGenerator se suma al output DESPUÉS de toda la cadena DSP — patrón diferente a todos los demás módulos que procesan captureBuffer in-place
- Timer implementado como coroutine delay que setea tinnitusEnabled=false al expirar — auto-desactiva vía DataStore, el observer propaga al engine

---

## Anti-patrones

- No generar noise con `rand()` de stdlib — usar PRNG de calidad (`std::mt19937` o xorshift) para evitar artefactos espectrales
- No computar coeficientes del notch filter en `generate()` (audio thread) — pre-computar en el setter de frecuencia
- No pasar el sonido de tinnitus por el pipeline DSP (WDRC, EQ, ANC, noise gate) — es sonido generado, no audio capturado
- No modificar captureBuffer con el tinnitus — sumar directamente al output buffer
- No usar mutex en `generate()` — solo atomics para los parámetros simples
- No generar nuevos PRPs durante la ejecución de este PRP
- No agregar dependencias nuevas ni modificar AGP/Gradle versions

---

*PRP COMPLETADO — 2026-08-24*
