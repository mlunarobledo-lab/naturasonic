# PRP-024: Sistema Inteligente de Atenuación de Transitorios (Look-Ahead Peak Limiter)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-23
> **Proyecto**: NaturaSonic

---

## Origen

> Planificación directa, sin brief previo. Las 7 fases del brief `@docs/BRIEF-naturasonic.md` están todas en estado `COMPLETADO`. Este PRP extiende la protección auditiva del pipeline DSP más allá del alcance original del brief.
> Las secciones heredadas (Directiva de Stack, Supuestos, Fuera de Alcance, Aprendizajes heredados) se derivan del contexto acumulado del proyecto, no de un brief activo.

---

## Objetivo

Quiero que NaturaSonic proteja al usuario contra impactos acústicos transitorios (puertas cerrándose, cubiertos cayendo, aplausos súbitos, estallidos) que atraviesan el pipeline DSP amplificados y llegan al oído antes de que el VolumeLimiter reactivo existente pueda atenuarlos. La protección debe ser imperceptible durante el audio normal y solo activarse ante picos destructivos, sin introducir distorsión armónica ni artefactos audibles.

## Por Que

| Problema | Solución |
|----------|----------|
| El `VolumeLimiter` actual es reactivo — detecta el pico DESPUÉS de que pasa por el buffer de salida. Con attack de 1ms y un frame de 256 muestras (~5.3ms a 48kHz), el primer sample del transiente llega al auricular sin atenuar | Look-Ahead Peak Limiter con delay line de ~2ms: el algoritmo escanea las muestras entrantes y PRE-computa la curva de ganancia antes de que el transiente alcance la salida, eliminando el primer ciclo de sobrepaso |
| Los transitorios amplificados por el AudioProcessor (amplification + EQ con gains positivos) pueden superar el umbral de 85 dB durante los primeros microsegundos — suficiente para causar molestia o daño en un PSAP que se usa horas al día | La rampa de atenuación comienza ANTES del pico (gracias al look-ahead), con attack en microsegundos y release adaptativo que evita pumping |
| El VolumeLimiter existente usa envelope following simple que no distingue transitorios breves de incrementos sostenidos de volumen — aplica la misma curva a ambos, lo que genera pumping audible en transitorios rápidos seguidos de release lento | TransientLimiter separado con parámetros optimizados para transitorios: attack ultra-rápido (< 100µs) + release corto adaptativo (50-200ms), complementario al VolumeLimiter que sigue protegiendo contra niveles sostenidos |

**Valor**: Necesito que NaturaSonic cumpla con el estándar de protección auditiva esperado en un PSAP profesional. El limitador de volumen actual es una red de seguridad para niveles sostenidos, pero los impactos acústicos transitorios son el vector de daño más común en usuarios de amplificación personal — proteger contra ellos es un diferenciador directo frente a apps PSAP competidoras.

## Que

### Criterios de éxito
- [ ] Un transiente que exceda el threshold configurado se atenúa suavemente ANTES de alcanzar el buffer de salida — cero overshoot en la señal limitada
- [ ] La latencia adicional introducida por el delay line es ≤ 2ms (96 samples a 48kHz) — dentro del budget de latencia total del pipeline (< 20ms)
- [ ] Durante audio normal (sin transitorios), el TransientLimiter es transparente — gain = 1.0, sin coloración ni artefactos
- [ ] El attack es ≤ 100µs (≤ 5 samples a 48kHz) — suficientemente rápido para atrapar el frente de onda de cualquier transiente
- [ ] El release es adaptativo (50-200ms) — evita pumping sin dejar la ganancia comprimida demasiado tiempo tras un evento breve
- [ ] El usuario puede activar/desactivar el protector desde Settings → "Protector de transitorios"
- [ ] El threshold es ajustable por el usuario (rango -20 a 0 dBFS, default -6 dBFS)
- [ ] `./gradlew assembleDebug` compila sin errores
- [ ] `./gradlew lint` sin warnings nuevos

### Comportamiento esperado

El usuario activa el protector de transitorios desde Settings → "Protector de transitorios". Desde ese momento, todo el audio que sale del AudioProcessor pasa por el TransientLimiter antes de llegar al VolumeLimiter. El procesamiento es invisible — el usuario no percibe diferencia durante conversaciones normales, música o sonidos ambiente.

Cuando ocurre un transiente (una puerta se cierra, alguien aplaude cerca del micrófono, un cubierto cae sobre una mesa), el TransientLimiter lo detecta 2ms antes de que alcance la salida gracias al delay line. En esos ~96 samples de anticipación, calcula la reducción de ganancia necesaria para que el pico no supere el threshold y aplica una rampa suave descendente. El usuario percibe el transiente atenuado de forma natural, como si estuviera más lejos — no como un recorte abrupto ni como silencio.

Tras el transiente, el release adaptativo restaura la ganancia a 1.0 en 50-200ms dependiendo de la duración del evento. Si hay transitorios repetidos rápidos (aplausos), el release se mantiene más largo para evitar pumping. Si es un único evento breve (portazo), el release es rápido para recuperar el nivel normal.

La pantalla TransientLimiterScreen muestra:
1. **Toggle** de activación/desactivación del protector
2. **Slider de Threshold** (-20 a 0 dBFS, default -6 dBFS) con indicación visual del nivel actual
3. **Card informativa** explicando qué hace el protector en lenguaje accesible

### Casos borde

- **TransientLimiter + VolumeLimiter encadenados**: el TransientLimiter opera ANTES del VolumeLimiter. Ambos coexisten — el TransientLimiter atrapa picos rápidos, el VolumeLimiter asegura el techo de 85 dB sostenido. No se cancelan ni interfieren porque operan en dominios temporales diferentes (µs vs ms).
- **Transiente durante noise gate activo**: el noise gate atenúa primero; si el transiente sobrevive al gate (es más fuerte que el umbral de voz), el TransientLimiter lo atrapa. Orden: NoiseGate → EQ → TransientLimiter → VolumeLimiter.
- **Latencia acumulada**: el delay line añade ~2ms. Sumado al pipeline existente (buffer de captura ~5.3ms), la latencia total sigue dentro del budget de 20ms del PSAP.
- **Transiente más largo que el delay line**: un evento sostenido > 2ms (ej: bocina larga) será atrapado por el release lento que mantiene la ganancia reducida mientras el nivel siga alto. Al normalizarse, el release adaptativo restaura gradualmente.
- **Threshold muy bajo (-20 dBFS)**: el limiter se activará frecuentemente, comprimiendo el rango dinámico. Esto es intencional si el usuario lo configura así — es equivalente a un compresor suave. El default (-6 dBFS) solo atrapa transitorios genuinamente fuertes.
- **Desactivado (toggle off)**: el delay line se bypasea completamente. No hay latencia adicional ni procesamiento cuando está desactivado. El captureBuffer pasa directo de AudioProcessor a VolumeLimiter.
- **Audio silence / near-silence**: gain permanece en 1.0, cero procesamiento audible. El costo computacional es mínimo (comparación + asignación por sample).
- **Pipeline de audio con AEC**: el TransientLimiter opera DESPUÉS del AudioProcessor y ANTES de la referencia de AEC. La referencia del AEC ve la señal ya limitada, lo que mejora la convergencia del filtro NLMS.

---

## Contexto

### Documentación externa
- Algoritmos de look-ahead peak limiting — técnica estándar en mastering de audio y procesadores de broadcast (Waves L2, FabFilter Pro-L). El concepto: retardar la señal por N samples y usar ese tiempo para pre-computar la curva de ganancia.
- IEC 61672 (A-weighting) — ya implementado en DosimetryAnalyzer. El TransientLimiter opera en dominio lineal (dBFS relativo al full-scale digital), no en dBA — la protección es contra picos de amplitud digital, no contra nivel de presión sonora calibrado.

### Código existente a consultar
- `audio_processor.h/cpp` — clase AudioProcessor con EqSnapshot double-buffer. El TransientLimiter NO se integra aquí — es módulo independiente en el pipeline.
- `volume_limiter.h/cpp` — clase VolumeLimiter existente. El TransientLimiter se modela como clase paralela con interfaz similar (`process(float*, int)`) pero algoritmo look-ahead en vez de envelope follower.
- `oboe_engine.h/cpp` — NaturaSonicEngine con el pipeline `onAudioReady`. La inserción del TransientLimiter es entre `processor_.process()` y `limiter_.process()` (líneas 168-169 de oboe_engine.cpp).
- `UserPreferences.kt` — DataStore con 21 keys. Se agregan `transient_limiter_enabled` (Boolean) y `transient_limiter_threshold` (Float, dBFS).
- `NavGraph.kt` — 18 rutas actuales. Se agrega `TRANSIENT_LIMITER`.
- `SettingsScreen.kt` — patrón TextButton + Icon para navegación. Se agrega entrada "Protector de transitorios".
- `oboe_engine.cpp:168-169` — punto exacto de inserción: después de `processor_.process(captureBuffer_.data(), framesToProcess)` y antes de `limiter_.process(captureBuffer_.data(), framesToProcess)`.

### Gotchas conocidas
- **Delay line introduce latencia real**: a diferencia del VolumeLimiter que es zero-latency (reactivo), el look-ahead requiere un delay buffer. A 48kHz, 2ms = 96 samples. Esto añade ~2ms a la latencia end-to-end del pipeline — aceptable dentro del budget de 20ms para PSAP.
- **Bypass sin latencia**: cuando el TransientLimiter está desactivado, NO debe procesar ni retardar el audio. El bypass debe ser zero-latency — un `std::atomic<bool>` que salta el procesamiento completamente.
- **Interacción con AEC**: la referencia del AecFilter se alimenta DESPUÉS del VolumeLimiter (post-pipeline). El TransientLimiter va ANTES del VolumeLimiter, así que la referencia del AEC siempre ve audio ya limitado por ambos. Esto es correcto — el AEC necesita la señal tal como sale al speaker.
- **No usar EqSnapshot double-buffer**: el TransientLimiter solo tiene 2 parámetros (enabled, threshold). `std::atomic<bool>` y `std::atomic<float>` individuales son suficientes — no requiere la complejidad del double-buffer que AudioProcessor usa para 10+ parámetros coherentes.
- **Procesamiento en-el-lugar (in-place)**: el `process()` lee del buffer de entrada, retarda, y escribe de vuelta al mismo buffer. El delay line es interno al TransientLimiter — no requiere un buffer externo adicional.

### Modelo de datos (cambios)

No hay cambios en el schema de Room (las 6 entities y v5 permanecen). Los cambios son:

1. **Nuevos archivos C++**: `transient_limiter.h` y `transient_limiter.cpp` — clase TransientLimiter con delay line circular + look-ahead gain.
2. **`oboe_engine.h/cpp`**: se agrega miembro `TransientLimiter transientLimiter_` + métodos setter delegados + inserción en pipeline.
3. **JNI**: 3 nuevos nativos — `nativeSetTransientLimiterEnabled(bool)`, `nativeSetTransientLimiterThreshold(float)`, `nativeGetTransientLimiterActive() → bool` (indicador visual).
4. **`UserPreferences.kt`**: 2 keys nuevas — `transient_limiter_enabled: Boolean`, `transient_limiter_threshold: Float`.
5. **`NavGraph.kt`**: ruta `TRANSIENT_LIMITER`.
6. **Kotlin**: `TransientLimiterScreen` + `TransientLimiterViewModel` en `ui/screens/transientlimiter/`.

---

## Directiva de Stack heredada

> Derivada del proyecto existente NaturaSonic (brief con compatibilidad REPLACE).

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Ninguno del stack web Praxis

### ADD (extensiones para este PRP)
- Ninguna dependencia nueva. El TransientLimiter es C++ puro (como VolumeLimiter, AecFilter, DosimetryAnalyzer) sin librerías externas.

### REPLACE
- Nada

### REMOVE
- Nada

### CONFIG
- `CMakeLists.txt` — agregar `transient_limiter.cpp` a las fuentes de `libnaturasonic.so`

### Refinamientos a la Directiva durante este PRP
- Zero dependencias nuevas. El TransientLimiter usa las mismas primitivas C++ que el resto del pipeline: `std::atomic`, `std::cmath`, arrays planos. Se modela como clase hermana de `VolumeLimiter` — misma interfaz `process(float*, int)`, mismos patrones de threading.

---

## Supuestos heredados

> Derivados del contexto acumulado del proyecto.

- [ ] El pipeline Oboe opera a 48kHz mono con frames de 256 muestras (~5.3ms por callback)
- [ ] El budget de latencia total del pipeline PSAP es < 20ms — el delay line de 2ms cabe holgadamente
- [ ] `VolumeLimiter` existente sigue operando como red de seguridad para niveles sostenidos — el TransientLimiter lo complementa, no lo reemplaza
- [ ] `AudioProcessor::process()` se ejecuta antes en el pipeline — el TransientLimiter recibe audio ya amplificado y ecualizado

### Supuestos adicionales (específicos de este PRP)
- [ ] Un delay line de 96 samples (2ms) proporciona anticipación suficiente para atrapar transitorios acústicos típicos (rise time de 0.5-2ms en portazos, aplausos, impactos metálicos)
- [ ] Attack de ≤ 5 samples (~100µs) es alcanzable con una rampa de ganancia lineal sin artefactos perceptibles a 48kHz
- [ ] El costo computacional por sample (1 comparación + 1 lectura/escritura de delay line + 1 multiplicación de ganancia) es negligible frente al AudioProcessor (10 biquad cascades + noise gate)

---

## Fuera de Alcance heredado

> Derivado del brief original + contexto del proyecto.

- Streaming Auracast / broadcast LE Audio
- Versión iOS
- Backend en la nube / Supabase como servicio de auth
- Audiograma clínico calibrado (solo PSAP)
- Integración con audífonos clínicos FDA

### Fuera de Alcance adicional (específico de este PRP)
- **Compresor multibanda** — el TransientLimiter es un peak limiter de banda completa; la compresión por bandas de frecuencia queda fuera
- **Visualización de waveform** — no se muestra la forma de onda pre/post limiting; solo un indicador binario de activación
- **Auto-threshold adaptativo** — el threshold es manual (fijado por el usuario); un threshold que se adapte al entorno acústico queda fuera
- **Brick-wall limiter** — el TransientLimiter usa ganancia suave (soft-knee implícito por la rampa), no clipping duro
- **Multi-stage limiting** — se implementa un solo stage de look-ahead; técnicas de multi-stage (como L2 de Waves) quedan fuera

---

## Aprendizajes heredados de fases previas

> Aprendizajes transversales de `CLAUDE.md` que aplican a este trabajo.

**2026-08-03: El proyecto NaturaSonic es Android nativo (no web)**
- Los comandos de validación son `./gradlew assembleDebug` (build), `./gradlew lint` (lint). No aplican npm/tsc.
- Aplicar en: todos los criterios de validación de este PRP.

**2026-08-13: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe**
- NO aplica directamente a este PRP. El TransientLimiter solo tiene 2 parámetros (`enabled`, `threshold`) que son `std::atomic` independientes — no requieren coherencia entre sí (a diferencia de los 10+ campos de EqSnapshot que deben cambiar atómicamente).
- Aplicar en: si en el futuro el TransientLimiter acumula 3+ parámetros interdependientes, considerar migrar a double-buffer.

**2026-08-15: applyProfile API cambió de bool a int para noise gate — patrón de migración enum-based**
- No aplica directamente. El TransientLimiter usa `bool` (on/off) + `float` (threshold). Si evoluciona a múltiples modos, seguir este patrón de migración.

**2026-08-17: Offsets espaciales EQ deben integrarse en computeEqCoefficients, no en applyEqualizer**
- No aplica. El TransientLimiter opera en el dominio de amplitud (no frecuencia) y no interactúa con los coeficientes biquad del EQ.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agéntico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: TransientLimiter C++ — Delay Line + Look-Ahead Gain
- **Objetivo**: Implementar la clase `TransientLimiter` en C++ con: delay line circular de 96 samples (2ms a 48kHz), escaneo look-ahead del buffer de entrada para detectar picos sobre el threshold, cálculo de curva de ganancia con attack ultra-rápido (≤ 5 samples, ~100µs) y release adaptativo (50-200ms), bypass zero-latency cuando desactivado. La clase sigue el patrón de `VolumeLimiter` — interfaz `process(float*, int)` + setters atómicos.
- **Validación**:
  - [ ] `transient_limiter.h/cpp` compilados correctamente como parte de `libnaturasonic.so`
  - [ ] La clase procesa un buffer de 256 floats sin crash ni NaN
  - [ ] Con enabled=false, el buffer de salida es idéntico al de entrada (zero-latency bypass)
  - [ ] Con enabled=true, el buffer de salida tiene un delay de exactamente 96 samples respecto al input
  - [ ] Señal por debajo del threshold pasa sin atenuación (gain = 1.0)
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: Integración en Pipeline + JNI Bridge + Preferencias
- **Objetivo**: Insertar `TransientLimiter` en el pipeline `onAudioReady` entre `processor_.process()` y `limiter_.process()`. Agregar miembro `transientLimiter_` a `NaturaSonicEngine` con métodos delegados. Crear los 3 JNI nativos (`nativeSetTransientLimiterEnabled`, `nativeSetTransientLimiterThreshold`, `nativeGetTransientLimiterActive`). Agregar las 2 keys a `UserPreferences` (`transient_limiter_enabled`, `transient_limiter_threshold`). Observar preferencias desde `AudioService` y propagar al engine nativo.
- **Validación**:
  - [ ] El pipeline encadena correctamente: AEC → Dosimetry → AudioProcessor → TransientLimiter → VolumeLimiter → output
  - [ ] JNI bridge compila y vincula sin `UnsatisfiedLinkError`
  - [ ] DataStore persiste los valores de enabled y threshold entre reinicios de la app
  - [ ] AudioService observa `combine(transientLimiterEnabled, transientLimiterThreshold)` y propaga cambios al engine
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: TransientLimiterScreen UI Compose + ViewModel
- **Objetivo**: Crear `TransientLimiterScreen` Compose accesible desde Settings → "Protector de transitorios". Incluir: toggle de activación/desactivación, slider de threshold (-20 a 0 dBFS, default -6 dBFS) con display del valor, card informativa explicando el protector en lenguaje accesible. Crear `TransientLimiterViewModel` con `UserPreferences` inyectado vía Hilt. Integrar ruta `TRANSIENT_LIMITER` en `NavGraph.kt` y entrada de navegación en `SettingsScreen.kt`.
- **Validación**:
  - [ ] TransientLimiterScreen renderiza correctamente con toggle, slider y card
  - [ ] Toggle persiste estado en DataStore
  - [ ] Slider muestra valor en dB y persiste
  - [ ] Navegación Settings → Protector de transitorios → back funciona correctamente
  - [ ] `./gradlew assembleDebug` exitoso
  - [ ] `./gradlew lint` sin warnings nuevos

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

**2026-08-23: TransientLimiter como clase hermana de VolumeLimiter — patrón validado**
- El patrón de `process(float*, int)` + `std::atomic` para parámetros simples + clase independiente en el pipeline funciona sin fricción. La integración en `onAudioReady` es una sola línea entre `processor_.process()` y `limiter_.process()`.
- **Aplicar en**: Cualquier futuro módulo DSP con ≤3 parámetros independientes que no requieran coherencia atómica entre sí — seguir este patrón de clase hermana con `std::atomic`, no el double-buffer de EqSnapshot.

**2026-08-23: Delay line circular in-place sin buffer externo — funcional para look-ahead**
- El delay line de 96 samples (2ms a 48kHz) opera correctamente in-place: lee el sample más viejo, escribe el nuevo, avanza el write pointer con módulo. No requiere buffer auxiliar ni doble pasada. La latencia de 2ms es imperceptible en el contexto del pipeline total (~7ms).
- **Aplicar en**: Si se necesitan otros módulos con look-ahead (ej: compresor predictivo, análisis pre-transiente), replicar este patrón de delay line circular con constante de tamaño descriptiva.

---

## Anti-patrones

- No integrar el TransientLimiter dentro de EqSnapshot/AudioProcessor — es módulo independiente como VolumeLimiter
- No usar double-buffer para 2 parámetros atómicos independientes — es over-engineering innecesario
- No hardcodear el tamaño del delay line — usar constante con nombre descriptivo (`kLookAheadSamples`)
- No usar clipping duro (hard clip) — siempre ganancia multiplicativa suave
- No crear patrones nuevos si los existentes funcionan
- No ignorar errores de compilación Kotlin/C++
- No commitear secrets
- NO generar nuevos PRPs durante la ejecución de este PRP

---

*PRP COMPLETADO el 2026-08-23. Todas las fases ejecutadas. Build exitoso.*
