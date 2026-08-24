# PRP-028: Compresión Dinámica de Rango Amplio (WDRC) Multi-Banda

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-24
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `docs/BRIEF-naturasonic.md`. Complementa la amplificación lineal existente del AudioProcessor (PRP-001 Fase 3) con compresión dinámica nivel-dependiente por banda de frecuencia — el estándar de oro en audífonos reales (WDRC). Usa los datos del audiograma local (PRP-013) para personalizar los parámetros de compresión por banda según el perfil auditivo del usuario.

---

## Objetivo

Quiero que NaturaSonic amplifique el sonido de forma inteligente: los sonidos suaves se amplifican más y los sonidos fuertes se comprimen, banda por banda de frecuencia. Hoy la amplificación es lineal (todo se sube igual), lo que obliga al usuario a elegir entre escuchar los sonidos suaves (pero los fuertes molestan) o proteger de los fuertes (pero los suaves se pierden). Con WDRC, el audio se adapta automáticamente: conversación suave en un restaurante ruidoso se amplifica en las bandas de voz mientras que el ruido de platos se comprime. Si tengo un audiograma guardado, los parámetros de compresión se ajustan automáticamente a mi perfil auditivo.

## Por Qué

| Problema | Solución |
|----------|----------|
| La amplificación lineal actual sube todo por igual — si el gain es alto para escuchar voz suave, los sonidos fuertes se vuelven incómodos o dolorosos | WDRC aplica ganancia variable por nivel: alta para señales débiles (voz lejana), baja para señales fuertes (portazo, claxon). Comfort automático |
| El ecualizador ajusta el balance tonal pero no adapta la dinámica — una banda puede tener el gain correcto para voz normal pero insuficiente para voz suave en la misma frecuencia | WDRC opera independientemente del EQ: cada banda tiene su propio compresor que ajusta la ganancia en función del nivel de entrada, no del balance tonal |
| Sin audiograma, la amplificación es genérica — con audiograma (PRP-013), el half-gain personaliza el EQ pero no la dinámica | WDRC personalizado mapea los umbrales del audiograma a parámetros de compresión por banda: donde el usuario tiene más pérdida, el ratio de compresión es más agresivo y el threshold más bajo |
| En entornos con rango dinámico amplio (conferencia con aplausos, calle con tráfico intermitente), el usuario constantemente ajusta el volumen | WDRC elimina la necesidad de ajuste manual — la ganancia se adapta automáticamente al nivel de entrada en cada banda de frecuencia |

**Valor**: WDRC es el algoritmo que distingue un PSAP serio de un simple amplificador de volumen. Es lo que hace que un audífono de $3000 sea útil: compresión dinámica multi-banda personalizada. NaturaSonic lo implementa en software, gratis, usando el audiograma local del usuario.

## Qué

### Criterios de éxito
- [ ] `WdrcCompressor` clase C++ con 10 bandas (mismas frecuencias centrales que el EQ existente)
- [ ] Cada banda: filtro bandpass biquad + envelope follower (RMS smoothed) + gain computer con knee suave
- [ ] Fórmula de compresión: `outputGain = threshold + (inputLevel - threshold) / ratio` cuando `inputLevel > threshold`; ganancia lineal cuando `inputLevel <= threshold`
- [ ] Parámetros por banda en `WdrcSnapshot`: threshold (-60..0 dBFS), ratio (1.0..10.0), attack (1..100 ms), release (10..1000 ms)
- [ ] Parámetros globales: enabled (bool), makeupGain (0..24 dB), presetIndex (int)
- [ ] Double-buffer atómico (`WdrcSnapshot[2]` + `std::atomic<int>`) — patrón EqSnapshot
- [ ] Coeficientes bandpass biquad pre-computados en setters, no en audio thread
- [ ] 3 presets: SPEECH (énfasis en 500-4000Hz, ratio alto en graves/agudos), MUSIC (ratio bajo uniforme), LOUD_ENV (threshold bajo, ratio alto en todas las bandas)
- [ ] Integración con audiograma: `applyAudiogramProfile(thresholds)` mapea umbrales a parámetros de compresión por banda
- [ ] Integrado en `onAudioReady` DESPUÉS de `ancPhaseInverter_.process()` y ANTES de `processor_.process()`
- [ ] JNI bridge: 7 funciones (enabled, makeupGain, preset, per-band threshold/ratio/attack/release, audiogram apply, get active gains)
- [ ] DataStore: 4 keys (wdrcEnabled, wdrcMakeupGain, wdrcPreset, wdrcCustomParams serializado)
- [ ] AudioService observer combina preferencias WDRC y propaga a C++
- [ ] `reapplyAllPreferences()` extendido con parámetros WDRC
- [ ] `WdrcScreen` Compose con toggle, selector de presets, slider de makeup gain, visualización de ganancia por banda en tiempo real, botón "Aplicar audiograma"
- [ ] `./gradlew assembleDebug` compila sin errores

### Comportamiento esperado

El WDRC se activa desde Settings → "Compresión dinámica". Cuando habilitado, el módulo C++ procesa cada frame de audio en `onAudioReady`:

1. Lee el buffer de captura (post-ANC, pre-processor)
2. Para cada una de las 10 bandas:
   a. Filtra la señal con un biquad bandpass centrado en la frecuencia de la banda
   b. Calcula el nivel RMS suavizado (envelope follower con attack/release independientes)
   c. Computa la ganancia según la curva de compresión: si nivel < threshold → ganancia lineal (1:1), si nivel > threshold → ganancia reducida por ratio. Knee suave de 6 dB alrededor del threshold para transición gradual
   d. Aplica la ganancia computada al audio filtrado de esa banda
3. Suma las 10 bandas procesadas para reconstruir la señal
4. Aplica makeup gain global para compensar la reducción de ganancia
5. El resultado continúa al AudioProcessor (amplificación master + noise gate + EQ)

La UI muestra:
- Toggle maestro de WDRC
- Selector de preset (SPEECH / MUSIC / LOUD_ENV / CUSTOM)
- Slider de makeup gain (0-24 dB)
- 10 barras verticales mostrando la ganancia aplicada por banda en tiempo real (visual feedback)
- Botón "Aplicar audiograma" (visible si hay audiograma guardado) que ajusta thresholds y ratios automáticamente
- Card informativa explicando WDRC en lenguaje simple

### Casos borde

- **WDRC enabled sin audio**: envelope followers se estabilizan en el piso de ruido, ganancia sube al máximo (1:1 + makeup) — comportamiento correcto, amplifica lo que llega
- **Ratio = 1.0 en todas las bandas**: compresión desactivada de facto (1:1 = lineal), equivale a bypass con solo makeup gain — válido como preset "OFF" parcial
- **Ratio = ∞ (limiter mode)**: la salida no excede threshold + makeup — ya tenemos TransientLimiter y VolumeLimiter downstream, así que cap ratio a 10.0 para evitar artefactos
- **MakeupGain alto + ratio alto**: señal comprimida + amplificada puede saturar. La señal pasa por TransientLimiter (PRP-024) y VolumeLimiter (85 dB) downstream — protección ya existe
- **Sin audiograma guardado**: botón "Aplicar audiograma" no aparece. Presets usan valores genéricos que funcionan para audición normal
- **Con audiograma**: `applyAudiogramProfile` mapea thresholds por frecuencia: threshold_dBFS = -40 + (hearingThreshold * 0.5), ratio = 1.0 + (hearingThreshold / 20.0). Más pérdida → threshold más bajo + ratio más agresivo
- **Cambio de preset en vivo**: los coeficientes biquad no cambian (bandpass fijo), solo thresholds/ratios/attack/release se actualizan vía double-buffer swap — transición suave sin glitch
- **Interacción con ANC**: ANC limpia bandas de ruido primero, WDRC comprime la dinámica de la señal limpia — complementarios
- **Interacción con Attention AGC**: Attention AGC modifica gains del EQ en AudioProcessor. WDRC opera antes, en un módulo separado. No hay conflicto — WDRC comprime dinámica, AGC ajusta balance tonal según atención
- **Eco mode activo**: WDRC no se throttlea — es per-sample inline, costo fijo ~microsegundos por frame (10 biquads + 10 envelope followers + 10 gain computers + suma)
- **Engine restart (watchdog)**: `reapplyAllPreferences()` re-aplica enabled + preset + makeup gain + custom params si los hay

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/audio_processor.h/cpp` — `BiquadCoeffs`, `BiquadState`, `processBiquad`, `computeEqCoefficients`, `EqSnapshot` double-buffer. Las frecuencias centrales `kCenterFreqs[10]` son la referencia para las bandas WDRC
- `app/src/main/cpp/anc_phase_inverter.h/cpp` — referencia de módulo DSP con double-buffer para FilterConfig, `computeLpCoeffs`/`computeHpCoeffs` bilinear transform. Patrón de módulo C++ más reciente
- `app/src/main/cpp/transient_limiter.h` — referencia de módulo DSP simple con atomics independientes (enabled, threshold)
- `app/src/main/cpp/oboe_engine.h/cpp` — `NaturaSonicEngine`, inclusión de módulos DSP, `onAudioReady` pipeline order (líneas 182-214). Punto de inserción: después de `ancPhaseInverter_.process()` (línea 211), antes de `processor_.process()` (línea 212)
- `app/src/main/cpp/native-lib.cpp` — JNI bridge, patrón de registro de funciones nativas
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — wrapper Kotlin JNI, patrón de `external fun` + métodos públicos
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore, patrón Flow + suspend setter
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — observer coroutines, `reapplyAllPreferences()` (línea 310-329), patrón `startXObserver()`
- `app/src/main/java/com/naturasonic/app/audiogram/AudiogramCalibration.kt` — `computeEqGains()`, `halfGain()`, `interpolateToAllBands()` — referencia de mapeo audiograma → parámetros por banda
- `app/src/main/java/com/naturasonic/app/data/local/entity/AudiogramRecord.kt` — entity Room con `leftThresholds` / `rightThresholds` (String serializado)
- `app/src/main/java/com/naturasonic/app/data/local/dao/AudiogramDao.kt` — acceso a audiogramas guardados
- `app/src/main/java/com/naturasonic/app/ui/screens/transientlimiter/TransientLimiterScreen.kt` — referencia de UI con toggle + slider
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — rutas y patrón de navegación

### Gotchas conocidas
- **Los coeficientes bandpass biquad (5 floats × 10 bandas = 50 floats) no son atómicamente swappables**: usar `WdrcSnapshot` double-buffer con `std::atomic<int>` index — mismo patrón que `EqSnapshot` y `FilterConfig` del ANC
- **BiquadState (x1,x2,y1,y2 × 10 bandas) NO se duplica en el double-buffer**: son estado continuo IIR que persiste entre frames. Un solo set de estados por banda, como `eqStates_` en AudioProcessor
- **`processBiquad` es private en AudioProcessor y en AncPhaseInverter**: clonar la función (3 líneas) en WdrcCompressor. No justifica refactorizar a header compartido por 3 líneas
- **Orden en pipeline**: DESPUÉS de ANC (línea 211) y ANTES de `processor_.process()` (línea 212). WDRC NO debe procesar antes de ANC porque ANC limpia ruido frecuencial que distorsionaría los envelope followers del WDRC
- **El diseño bandpass biquad requiere pre-warping a 48kHz**: bilinear transform con Q calculado para ancho de banda relativo. Bandas extremas (125Hz, 12kHz) necesitan Q ajustado para evitar solapamiento excesivo
- **Suma de bandas = reconstrucción**: la suma de 10 bandpass biquads no reconstruye perfectamente la señal original (hay solapamiento y huecos). Compensar con normalización o aceptar la coloración como parte del procesamiento — en audífonos WDRC la reconstrucción perfecta no es un requisito
- **Envelope follower attack/release**: attack en muestras = `1 - exp(-2.2 / (attackMs * sampleRate / 1000))`, release análogo. Smooth RMS per-sample para estabilidad. Usar dB para el gain computation, lineal para la aplicación
- **Re-aplicación tras restart del engine**: `reapplyAllPreferences()` debe extenderse con los parámetros WDRC (enabled, preset, makeup gain)

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
- `wdrc_compressor.h/cpp` — nuevo módulo C++ DSP
- 4 keys en DataStore (`wdrcEnabled`, `wdrcMakeupGain`, `wdrcPreset`, `wdrcCustomParams`)
- `WdrcScreen` + `WdrcViewModel` — nueva pantalla Compose

### REMOVE
- Nada

### CONFIG
- `CMakeLists.txt` — agregar `wdrc_compressor.cpp` a la lista de sources

---

## Supuestos heredados

- [ ] Pipeline Oboe operativo con `onAudioReady` callback funcionando a 48kHz
- [ ] AudioProcessor con infraestructura biquad (BiquadCoeffs, BiquadState, processBiquad, kCenterFreqs) como referencia
- [ ] AncPhaseInverter procesando antes del punto de inserción del WDRC en el pipeline
- [ ] DosimetryAnalyzer leyendo audio raw antes del punto de inserción del WDRC
- [ ] AudioService con patrón de observer coroutines + `reapplyAllPreferences()`
- [ ] UserPreferences DataStore con patrón Flow + suspend setter

### Supuestos adicionales (específicos de este PRP)
- [ ] Filtro bandpass biquad (peaking con Q ajustado) implementable con un solo biquad section por banda (confirmed: standard IIR design)
- [ ] Bilinear transform con pre-warping produce coeficientes estables a 48kHz para las 10 frecuencias centrales (confirmed: 125Hz-12kHz dentro del rango estable)
- [ ] El overhead de 10 filtrados bandpass + 10 envelope followers + 10 gain computers + suma por muestra es manejable en el audio callback (~microsegundos por frame de 256 muestras)
- [ ] AudiogramDao tiene método para obtener el audiograma activo más reciente

---

## Fuera de Alcance

- **Compresión adaptativa con aprendizaje de entorno**: el WDRC usa parámetros estáticos por preset/audiograma — no estima dinámicamente el tipo de entorno acústico para ajustar automáticamente (futuro PRP con clasificación de escena)
- **WDRC binaural (processing diferente por oído)**: se procesa mono (el pipeline Oboe es mono). Procesamiento binaural requeriría stream stereo — fuera del alcance actual
- **Frequency lowering / transposition**: mover frecuencias altas a rangos audibles es una técnica diferente a WDRC — sería PRP separado
- **Visualización de input/output en tiempo real (gráfico de I/O)**: la UI muestra ganancia por banda, no la curva de compresión completa
- **Modificación de Room schema**: sin persistencia en Room — las preferencias WDRC van a DataStore, los custom params se serializan como JSON string
- **Integración con perfiles de audio (AudioProfile entity)**: el WDRC es independiente del perfil de EQ — se controla por su cuenta desde DataStore

---

## Aprendizajes heredados de fases previas

**2026-08-13: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe**
- Los parámetros del WDRC (10 thresholds + 10 ratios + 10 attack/release + coeficientes biquad) no son atómicamente swappables. Se usará `WdrcSnapshot` double-buffer con `std::atomic<int>` index. Los controles simples (enabled, makeupGain) pueden usar `std::atomic` independientes porque son flags/valores individuales.

**2026-08-17: Offsets espaciales EQ deben integrarse en computeEqCoefficients, no en applyEqualizer**
- Paralelo directo: los coeficientes bandpass del WDRC deben pre-computarse en el setter (JNI thread) cuando cambian las frecuencias centrales o Q. El audio thread solo lee coeficientes pre-computados del snapshot activo.

**2026-08-24: SharedFlow(replay=0) para señalizar reinicios engine → AudioService**
- `reapplyAllPreferences()` ya existe. Debe extenderse para incluir los parámetros WDRC (enabled, preset, makeup gain). Los observers existentes no re-disparan — la re-aplicación explícita cubre el nuevo módulo.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agentico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: WdrcCompressor C++ + JNI bridge
- **Objetivo**: Crear `wdrc_compressor.h/cpp` con 10 bandas bandpass biquad, envelope follower per-band, gain computer con knee suave, y `WdrcSnapshot` double-buffer atómico. 3 presets hardcoded (SPEECH, MUSIC, LOUD_ENV). Método `applyAudiogramProfile()` que mapea thresholds a parámetros de compresión. Integrar como miembro de `NaturaSonicEngine`, llamar `process()` en `onAudioReady` entre ANC y processor. Agregar al `CMakeLists.txt`. Crear funciones JNI en `native-lib.cpp`. Agregar `external fun` en `OboeAudioEngine.kt`.
- **Validación**:
  - [ ] `WdrcCompressor::process()` ejecuta band-split + envelope + gain compute + apply + sum por muestra
  - [ ] Coeficientes bandpass biquad pre-computados en constructor, no en audio thread
  - [ ] Double-buffer atómico para WdrcSnapshot (thresholds, ratios, attack/release coefs por banda)
  - [ ] 3 presets aplicables vía `setPreset(int)`
  - [ ] `applyAudiogramProfile()` mapea 10 thresholds a parámetros de compresión
  - [ ] Funciones JNI registradas y wrappers Kotlin funcionales
  - [ ] Pipeline order correcto en `onAudioReady`: ANC → WDRC → processor
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: UserPreferences + AudioService integration
- **Objetivo**: Agregar 4 keys a DataStore (wdrcEnabled, wdrcMakeupGain, wdrcPreset, wdrcCustomParams). Crear observer coroutine en AudioService que combine las preferencias WDRC y propague cambios al engine C++. Extender `reapplyAllPreferences()` con los parámetros WDRC.
- **Validación**:
  - [ ] 4 Flow + 4 suspend setters en UserPreferences
  - [ ] Observer coroutine en AudioService propaga cambios al engine
  - [ ] `reapplyAllPreferences()` incluye WDRC
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: WdrcScreen UI + ViewModel + NavGraph + Settings entry
- **Objetivo**: Crear `WdrcViewModel` con UserPreferences + AudiogramDao. Crear `WdrcScreen` Compose con toggle maestro, selector de preset (chips), slider de makeup gain, 10 barras verticales de ganancia por banda (feedback visual), botón "Aplicar audiograma" (condicional a existencia de audiograma), card informativa. Integrar ruta WDRC en NavGraph y entrada "Compresión dinámica" en SettingsScreen.
- **Validación**:
  - [ ] WdrcScreen renderiza controles funcionales
  - [ ] Selector de presets aplica parámetros al engine en tiempo real
  - [ ] Barras de ganancia reflejan la compresión activa
  - [ ] Botón audiograma aplica perfil personalizado
  - [ ] Navegación Settings → Compresión dinámica funciona
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 4: Validación final
- **Objetivo**: Sistema funcionando end-to-end con WDRC multi-banda activo
- **Validación**:
  - [ ] Criterios de éxito cumplidos
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] WDRC comprime sonidos fuertes y amplifica sonidos suaves por banda
  - [ ] Presets producen comportamiento audiblemente diferente
  - [ ] Audiograma personaliza compresión correctamente
  - [ ] Coexistencia con ANC (PRP-027), noise gate (PRP-014), EQ, TransientLimiter verificada
  - [ ] Pipeline order correcto: AEC → dosimetry → ANC → WDRC → processor → transient → limiter
  - [ ] `reapplyAllPreferences()` re-aplica WDRC tras engine restart

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

**2026-08-24: Ejecución limpia sin errores de build en las 4 fases**
- Zero-error en todas las fases. El patrón WdrcSnapshot double-buffer compiló sin issues. La infraestructura biquad fue clonada (processBiquad de 3 líneas inline) en vez de abstraída — decisión correcta que evitó acoplamiento con AudioProcessor. Los coeficientes bandpass se pre-computan en constructor y setters, nunca en process(). Pipeline order ANC → WDRC → AudioProcessor mantenido sin conflictos.

---

## Anti-patrones

- No computar coeficientes biquad bandpass en `process()` (audio thread) — pre-computar en constructor y en setters de frecuencia/Q
- No usar mutex/lock en `process()` — solo reads atómicos (double-buffer index load + atomic flag/gain reads)
- No modificar el orden del pipeline existente — insertar WDRC como etapa nueva entre ANC y processor, no reorganizar
- No duplicar la infraestructura biquad de AudioProcessor — clonar `processBiquad` (3 líneas, no justifica header compartido)
- No implementar reconstrucción perfecta de bandpass — la suma de bandas con solapamiento es aceptable para WDRC en PSAP
- No agregar dependencias nuevas ni modificar AGP/Gradle versions
- No modificar Room schema — DataStore para todas las preferencias WDRC
- No generar nuevos PRPs durante la ejecución de este PRP — un PRP = una sola sesión, un solo plan
- No intentar procesamiento binaural — el pipeline es mono

---

*PRP COMPLETADO — 2026-08-24. Todas las fases implementadas y validadas.*
