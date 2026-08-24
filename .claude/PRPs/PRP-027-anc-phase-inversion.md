# PRP-027: Pipeline de Cancelación de Ruido Activa por Inversión de Fase (ANC Phase Inversion Core)

> **Estado**: EN PROGRESO
> **Fecha**: 2026-08-24
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `docs/BRIEF-naturasonic.md`. Complementa el noise gate adaptativo de PRP-014 (amplitude-based RMS VAD) con cancelación de ruido en el dominio frecuencial — aislamiento de bandas de ruido por filtrado biquad + inversión de fase (negación 180°) + mezcla ponderada.

---

## Objetivo

Quiero que NaturaSonic reduzca activamente el ruido de fondo usando inversión de fase matemática en frecuencias seleccionadas. Los filtros pasa-bajas y pasa-altas aíslan las bandas de ruido típicas (rumble de tráfico/HVAC en graves, siseo de electrónica/viento en agudos), invierten su fase 180° y las suman de vuelta al buffer original — cancelación destructiva. La ganancia de cancelación es ajustable para evitar artefactos. La UI me permite activar/desactivar ANC, controlar la intensidad y conmutar cada filtro de aislamiento.

## Por Qué

| Problema | Solución |
|----------|----------|
| El noise gate (PRP-014) opera en amplitud — atenúa todo cuando la señal cae debajo del umbral, incluyendo voz baja en entornos ruidosos | ANC por inversión de fase opera en frecuencia — substrae componentes de ruido específicas sin afectar las bandas de voz (300Hz-3kHz) |
| Ruido continuo de baja frecuencia (tráfico, metro, HVAC, zumbido eléctrico 50/60Hz) no se cancela con el noise gate porque está siempre presente y eleva el piso de ruido | Filtro LP aísla las componentes <200Hz, las invierte y las sustrae — cancelación selectiva de rumble sin tocar la voz |
| Siseo de alta frecuencia (ventiladores, electrónica, viento) molesta pero no dispara el noise gate porque está mezclado con la señal útil | Filtro HP aísla las componentes >4kHz, las invierte y las sustrae — reducción de hiss sin afectar el rango vocal |
| No hay control fino por banda de frecuencia — el noise gate es todo-o-nada en amplitud | ANC por inversión de fase permite control independiente de graves y agudos con ganancia de cancelación ajustable (0-100%) por filtro |

**Complementariedad con PRP-014**: El noise gate y el ANC por inversión de fase son ortogonales y coexisten. ANC limpia el espectro frecuencial primero (elimina bandas de ruido constante), y el noise gate actúa después sobre la señal ya limpia (atenúa silencios y ruido residual por amplitud). Pipeline: `ANC → noise gate`.

**Valor**: En entornos con ruido constante (transporte, oficina, exterior urbano), el PSAP amplifica ruido junto con voz. ANC reduce el ruido en origen (DSP) antes de amplificar — el usuario escucha voz limpia y amplificada, no voz + ruido amplificado.

## Qué

### Criterios de éxito
- [ ] `AncPhaseInverter` clase C++ con filtros biquad Butterworth LP (50-500Hz) y HP (2-8kHz) independientes
- [ ] Inversión de fase por negación (`-1.0f *`) de la señal filtrada
- [ ] Mezcla controlada: `output = original - LP(original) * lpGain - HP(original) * hpGain`
- [ ] Ganancia de cancelación global (`cancellationGain`, 0.0-1.0) como escalador maestro
- [ ] Parámetros lock-free: `std::atomic<bool>` para enabled/lpEnabled/hpEnabled, `std::atomic<float>` para gains
- [ ] Frecuencias de corte ajustables con recompute de coeficientes biquad en setter (double-buffer atómico para el par de configs)
- [ ] Integrado en `onAudioReady` DESPUÉS de dosimetry y ANTES de `processor_.process()`
- [ ] JNI bridge completo (6 funciones: enabled, gain, lpEnabled, hpEnabled, lpCutoff, hpCutoff)
- [ ] DataStore: 6 keys (ancPhaseEnabled, ancCancellationGain, ancLpEnabled, ancHpEnabled, ancLpCutoff, ancHpCutoff)
- [ ] AudioService observer combina las 6 preferencias y propaga a C++
- [ ] `AncCoreScreen` Compose con toggle, slider de ganancia, toggles de filtro LP/HP con sliders de frecuencia
- [ ] `./gradlew assembleDebug` compila sin errores

### Comportamiento esperado

El ANC por inversión de fase se activa manualmente desde Settings → "Cancelación activa". Cuando habilitado, el módulo C++ procesa cada frame de audio en `onAudioReady`:

1. Lee el buffer de captura (post-AEC, post-dosimetry)
2. Si LP habilitado: aplica filtro pasa-bajas Butterworth 2do orden al buffer → obtiene componente de graves → invierte fase → resta del original con `lpGain`
3. Si HP habilitado: aplica filtro pasa-altos Butterworth 2do orden al buffer → obtiene componente de agudos → invierte fase → resta del original con `hpGain`
4. La ganancia de cancelación global escala ambas sustracciones: `result = original - (lpFiltered * lpGain + hpFiltered * hpGain) * cancellationGain`
5. El resultado continúa al AudioProcessor (amplificación + noise gate + EQ)

La UI muestra:
- Toggle maestro de ANC
- Slider de ganancia de cancelación (0-100%)
- Card LP: toggle + slider de frecuencia de corte (50-500Hz, default 200Hz)
- Card HP: toggle + slider de frecuencia de corte (2000-8000Hz, default 4000Hz)
- Card informativa explicando la técnica

### Casos borde

- **Ambos filtros desactivados + ANC habilitado**: el módulo ejecuta pero no modifica la señal (no hay componentes filtradas que invertir) — cost: solo el check de enabled, nanosegundos
- **Ganancia de cancelación = 0.0**: bypass completo (no hay sustracción), equivalente a ANC off pero sin el overhead de filtrado — check temprano en `process()`
- **Ganancia = 1.0 con LP y HP activos**: cancelación máxima en graves y agudos — el rango medio (200Hz-4kHz) pasa intacto, lo que preserva la inteligibilidad vocal
- **Cutoff LP > cutoff HP**: solapamiento de bandas — ambos filtros sustraen las mismas frecuencias, cancelación excesiva en la zona de solapamiento. UI clamp: `lpCutoff ≤ hpCutoff` enforced en Kotlin setter
- **AEC + ANC simultáneos**: AEC procesa primero (línea 180-181 de oboe_engine.cpp), ANC procesa después sobre la señal post-echo-cancellation — sin conflicto
- **Noise gate + ANC**: ANC limpia el espectro primero (sustrae bandas de ruido), el noise gate opera después sobre la señal limpia en `processor_.process()` — complementarios
- **Cambio de cutoff en vivo**: recompute de coeficientes biquad en el JNI thread (setter), swap atómico de la config — el audio thread lee la nueva config en el siguiente frame. Transición suave porque los estados del biquad (x1,x2,y1,y2) persisten — no hay glitch
- **Eco mode activo**: ANC no se throttlea — es una operación inline por-muestra dentro del callback, costo fijo ~microsegundos por frame

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/oboe_engine.h` — `NaturaSonicEngine`, miembro `aecFilter_`, `transientLimiter_`, patrón de inclusión de módulos DSP
- `app/src/main/cpp/oboe_engine.cpp` — `onAudioReady` (líneas 158-241), orden de procesamiento, punto de inserción post-dosimetry pre-processor
- `app/src/main/cpp/audio_processor.h/cpp` — `BiquadCoeffs`, `BiquadState`, `computeEqCoefficients`, `processBiquad` — reutilizar o clonar estos tipos para los filtros del ANC
- `app/src/main/cpp/aec_filter.h` — referencia de módulo DSP C++ simple con `process()`, `setEnabled()`, atomics
- `app/src/main/cpp/transient_limiter.h` — referencia de módulo C++ con `std::atomic<bool>` enabled + `std::atomic<float>` threshold
- `app/src/main/cpp/native-lib.cpp` — JNI bridge, patrón de registro de funciones
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — wrapper Kotlin JNI, patrón de external fun
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore, patrón Flow + suspend setter
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — observer coroutines con combine, patrón `startXObserver()`
- `app/src/main/java/com/naturasonic/app/ui/screens/anc/AncViewModel.kt` — ViewModel existente para noise gate, referencia de patrón
- `app/src/main/java/com/naturasonic/app/ui/screens/transientlimiter/TransientLimiterScreen.kt` — referencia de UI con toggle + slider

### Gotchas conocidas
- **Los coeficientes biquad NO son atómicamente swappables**: 5 floats (b0,b1,b2,a1,a2) no caben en un `std::atomic`. Usar double-buffer con `std::atomic<int>` index (mismo patrón que EqSnapshot) para el par LP/HP configs
- **El estado biquad (x1,x2,y1,y2) NO se duplica**: los estados son continuo IIR y persisten entre frames — un solo set de estados por filtro, como `eqStates_` en AudioProcessor
- **`processBiquad` es private en AudioProcessor**: no se puede reutilizar directamente. Clonar la función en `AncPhaseInverter` (3 líneas, no justifica refactorizar a función compartida)
- **Orden en pipeline**: DESPUÉS de dosimetry (que lee raw audio para SPL) y ANTES de `processor_.process()` (que aplica amplificación + noise gate + EQ). El ANC NO debe procesar antes de dosimetry porque alteraría la medición ambiental
- **No confundir con ANC acústico**: esto es cancelación de ruido en el dominio de señal (DSP), no cancelación acústica de ondas de sonido en el aire. El disclaimer en la UI debe ser claro
- **Filtros Butterworth 2do orden**: Q = 0.7071 (1/√2) para respuesta maximally flat. Diseño bilinear transform con pre-warping a 48kHz
- **Re-aplicación tras restart del engine**: `reapplyAllPreferences()` en AudioService ya existe (PRP-026) — debe extenderse con los parámetros ANC

---

## Directiva de Stack heredada

> Derivada del proyecto existente NaturaSonic.

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- AGP 8.7.3, Kotlin 2.0.21, Oboe 1.9.0, todas las dependencias actuales congeladas
- Room v5 sin migraciones nuevas
- Pipeline Oboe C++ intacto — se inserta un nuevo módulo inline, no se modifica la cadena existente

### ADD
- `anc_phase_inverter.h/cpp` — nuevo módulo C++ DSP
- 6 keys en DataStore (`ancPhaseEnabled`, `ancCancellationGain`, `ancLpEnabled`, `ancHpEnabled`, `ancLpCutoff`, `ancHpCutoff`)
- `AncCoreScreen` + `AncCoreViewModel` — nueva pantalla Compose

### REMOVE
- Nada

### CONFIG
- `CMakeLists.txt` — agregar `anc_phase_inverter.cpp` a la lista de sources

---

## Supuestos heredados

- [ ] Pipeline Oboe operativo con `onAudioReady` callback funcionando a 48kHz
- [ ] AudioProcessor con biquad infrastructure (BiquadCoeffs, BiquadState, processBiquad) como referencia
- [ ] AecFilter procesando antes que el nuevo módulo ANC en el pipeline
- [ ] DosimetryAnalyzer leyendo audio raw antes del punto de inserción del ANC
- [ ] AudioService con patrón de observer coroutines + `reapplyAllPreferences()`
- [ ] UserPreferences DataStore con patrón Flow + suspend setter

### Supuestos adicionales (específicos de este PRP)
- [ ] Filtro Butterworth 2do orden implementable con un solo biquad section (confirmed: standard IIR design)
- [ ] Bilinear transform con pre-warping produce coeficientes estables a 48kHz para cutoffs en rango 50Hz-8kHz (confirmed: dentro del rango estable)
- [ ] El overhead de dos filtrados biquad + negación + suma por muestra es despreciable en el audio callback (~nanosegundos por sample, ~microsegundos por frame de 256 muestras)

---

## Fuera de Alcance

- **ANC acústico real**: no cancelamos ondas sonoras en el aire — esto requiere hardware dedicado (altavoz posicionado vs oído, micrófono de referencia externo)
- **Cancelación adaptativa con modelo de ruido aprendido**: la cancelación es por filtrado frecuencial estático — no estima dinámicamente el perfil de ruido (futuro PRP con algoritmos LMS/RLS)
- **Filtro notch para frecuencias específicas** (ej: 50/60Hz hum): podría agregarse como extensión futura, no en este PRP
- **Micrófono de referencia dual**: Android no expone un segundo micrófono como referencia ANC en la API pública — usamos el mismo stream de captura
- **Modificación de Room schema**: sin persistencia — las preferencias ANC van a DataStore

---

## Aprendizajes heredados de fases previas

**2026-08-13: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe**
- Los coeficientes biquad del ANC (LP + HP) no son atómicamente swappables. Se usará double-buffer con `std::atomic<int>` index para la config de filtros (cutoffs + coefficients), mismo patrón que EqSnapshot. Los controles simples (enabled, gain) usan `std::atomic` independientes porque son métricas/flags individuales.

**2026-08-17: Offsets espaciales EQ deben integrarse en computeEqCoefficients, no en applyEqualizer**
- Paralelo: las frecuencias de corte del ANC deben recomputar los coeficientes biquad en el setter (JNI thread), no en `process()` (audio thread). El audio thread solo lee coeficientes pre-computados.

**2026-08-24: SharedFlow(replay=0) para señalizar reinicios engine → AudioService**
- `reapplyAllPreferences()` ya existe. Debe extenderse para incluir los parámetros ANC. Los observers existentes no re-disparan — la re-aplicación explícita cubre el nuevo módulo.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agentico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: AncPhaseInverter C++ + JNI bridge
- **Objetivo**: Crear `anc_phase_inverter.h/cpp` con dos filtros biquad Butterworth 2do orden (LP + HP), inversión de fase, mezcla controlada, y parámetros lock-free. Integrar como miembro de `NaturaSonicEngine`, llamar `process()` en `onAudioReady` entre dosimetry y `processor_.process()`. Agregar al `CMakeLists.txt`. Crear 6 funciones JNI en `native-lib.cpp`. Agregar 6 `external fun` en `OboeAudioEngine.kt` con wrappers.
- **Validación**:
  - [ ] `AncPhaseInverter::process()` ejecuta filtrado + inversión + mezcla por muestra
  - [ ] Coeficientes biquad se recomputan en setters de cutoff, no en audio thread
  - [ ] Double-buffer atómico para config de filtros (2 slots, swap por índice)
  - [ ] 6 funciones JNI registradas y wrappers Kotlin funcionales
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: UserPreferences + AudioService integration
- **Objetivo**: Agregar 6 keys a DataStore (ancPhaseEnabled, ancCancellationGain, ancLpEnabled, ancHpEnabled, ancLpCutoff, ancHpCutoff). Crear observer coroutine en AudioService que combine las 6 preferencias y propague cambios al engine C++. Extender `reapplyAllPreferences()` con los parámetros ANC.
- **Validación**:
  - [ ] 6 Flow + 6 suspend setters en UserPreferences
  - [ ] Observer coroutine en AudioService propaga cambios al engine
  - [ ] `reapplyAllPreferences()` incluye ANC
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: AncCoreScreen UI + ViewModel + NavGraph + Settings entry
- **Objetivo**: Crear `AncCoreViewModel` con UserPreferences. Crear `AncCoreScreen` Compose con toggle maestro, slider de ganancia de cancelación, cards LP/HP con toggle + slider de frecuencia, card informativa. Integrar ruta ANC_CORE en NavGraph y entrada "Cancelación activa" en SettingsScreen.
- **Validación**:
  - [ ] AncCoreScreen renderiza controles funcionales
  - [ ] Slider de ganancia y toggles de filtro controlan el engine en tiempo real
  - [ ] Navegación Settings → Cancelación activa funciona
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 4: Validación final
- **Objetivo**: Sistema funcionando end-to-end con ANC por inversión de fase activo
- **Validación**:
  - [ ] Criterios de éxito cumplidos
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] ANC reduce componentes de ruido en graves/agudos sin degradar voz
  - [ ] Coexistencia con noise gate (PRP-014) verificada
  - [ ] Pipeline order correcto: AEC → dosimetry → ANC → processor → transient → limiter

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

*(vacía — el PRP no ha sido ejecutado)*

---

## Anti-patrones

- No computar coeficientes biquad en `process()` (audio thread) — recomputar en setters (JNI thread) y swap atómico
- No usar mutex/lock en `process()` — solo reads atómicos (double-buffer index load + atomic flag/gain reads)
- No modificar el orden del pipeline existente — insertar ANC como etapa nueva entre dosimetry y processor, no reorganizar
- No duplicar la infraestructura biquad de AudioProcessor — clonar las funciones (son 3 líneas, no justifica un header compartido)
- No confundir con ANC acústico en la UI — disclaimer claro: "reducción de ruido por procesamiento de señal"
- No agregar dependencias nuevas ni modificar AGP/Gradle versions
- No modificar Room schema — DataStore para todas las preferencias ANC

---

*PRP pendiente aprobación. No se ha modificado código.*
