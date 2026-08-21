# PRP-021: Sistema de Análisis de Paisaje Sonoro y Dosimetría Auditiva dBA

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-21
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Feature de bienestar auditivo que implementa medición de nivel sonoro ambiental en dBA (ponderación A-weighting según IEC 61672) y dosimetría auditiva acumulativa con límites OSHA (90 dBA / 8h, exchange rate 5 dB) y NIOSH (85 dBA / 8h, exchange rate 3 dB) para prevenir fatiga auditiva. Complementa la protección existente del VolumeLimiter (85 dB SPL cap) con monitoreo continuo de exposición ambiental.

---

## Objetivo

> Quiero que mis usuarios puedan ver en tiempo real el nivel de ruido ambiental al que están expuestos (en decibelios A-ponderados, dBA), y que la app acumule la dosis de ruido diaria contrastándola contra los estándares de protección auditiva de la OSHA y la NIOSH. Si la exposición supera los umbrales seguros, la app debe alertar al usuario para que tome medidas de protección. Esto cierra el ciclo de bienestar auditivo: NaturaSonic no solo amplifica y mejora audio, sino que también protege activamente contra la sobreexposición.

## Por Qué

| Problema | Solución |
|----------|----------|
| El usuario no sabe a qué nivel de ruido está expuesto en su entorno | Medidor dBA en tiempo real con lectura continua |
| No hay forma de saber si la exposición acumulada del día es peligrosa | Dosímetro con TWA (Time Weighted Average) dual OSHA/NIOSH |
| Sin alertas proactivas, el usuario puede sufrir fatiga auditiva sin darse cuenta | Alertas de alta prioridad al superar 50%, 80% y 100% de la dosis permitida |
| Los datos de exposición no persisten entre sesiones | Historial en Room con muestras periódicas y tendencia diaria |

**Valor**: Los usuarios tienen una herramienta continua de salud auditiva que va más allá de la amplificación — mide, acumula, advierte y registra su exposición al ruido. Esto refuerza el posicionamiento de NaturaSonic como plataforma de bienestar auditivo integral, no solo un PSAP.

## Qué

### Criterios de éxito
- [x] DosimetryAnalyzer C++ computa dBA en tiempo real con A-weighting IIR a 48kHz
- [x] El analizador se alimenta del audio raw (pre-processing) en `onAudioReady` para medir ruido ambiental real
- [x] Kotlin DosimetryManager acumula TWA con cálculo dual OSHA (90 dBA / 8h / ER 5 dB) y NIOSH (85 dBA / 8h / ER 3 dB)
- [x] La dosis se resetea diariamente a medianoche
- [x] Entity DosimetrySample en Room con migration v4→v5
- [x] Muestras persistidas cada 30s mientras la app está activa
- [x] SoundscapeAnalyticsScreen con gráfico Canvas de tendencia dBA del día
- [x] Indicadores visuales de dosis consumida OSHA y NIOSH (barras de progreso)
- [x] Alertas de alta prioridad (notificación) al superar 50%, 80% y 100% de dosis
- [x] Pantalla accesible desde Settings
- [x] Build compila sin errores (`./gradlew assembleDebug`)

### Comportamiento esperado

El usuario abre NaturaSonic y el medidor de dBA comienza a funcionar automáticamente como parte del pipeline de audio (no requiere activación separada). En la pantalla principal puede ver el nivel actual de dBA. Navega a "Paisaje sonoro" desde Settings o un acceso directo en la pantalla principal y ve:

1. **Nivel actual**: número grande con color semafórico (verde <70, amarillo 70-85, naranja 85-90, rojo >90 dBA).
2. **Gráfico de tendencia**: línea Canvas con las lecturas dBA de las últimas horas del día, con líneas horizontales de referencia a 85 y 90 dBA.
3. **Dosis acumulada**: dos barras de progreso — OSHA (con referencia 90 dBA / 8h) y NIOSH (con referencia 85 dBA / 8h). La barra muestra % consumido con colores que cambian según el nivel de riesgo.
4. **Alertas**: si la dosis supera 50%, 80% o 100%, la app emite notificación de alta prioridad con patrón de vibración. Las alertas pasadas se muestran como cards en la pantalla.

El sistema opera continuamente mientras el pipeline de audio está activo. Las lecturas se persisten cada 30s para poder mostrar historial entre sesiones.

### Casos borde

- Micrófono no calibrado: se usa un offset de calibración por defecto para smartphones típicos (~94 dBSPL para señal de referencia a fondo de escala), con opción de ajuste manual en la pantalla
- App en background con pipeline activo: el dosímetro sigue acumulando y puede emitir alertas vía notificación
- Sin datos históricos (primer uso): gráfico vacío con mensaje "Las lecturas empezarán a aparecer aquí"
- Nivel muy bajo (<30 dBA): mostrar como "Silencio" sin acumular dosis significativa
- Pipeline detenido: la acumulación se pausa; al reanudar continúa desde donde quedó (no resetea)
- Cruce de medianoche con la app activa: la dosis se resetea a 0% y se inicia nuevo período de 24h
- Modo eco activo: el dosímetro sigue operando a la misma cadencia (es computacionalmente trivial comparado con YAMNet/Whisper)

---

## Contexto

### Documentación externa
- IEC 61672-1 — Electroacoustics: Sound level meters. Define la curva de ponderación A y los requisitos para medidores de nivel sonoro. Los coeficientes del filtro A-weighting IIR para 48kHz se derivan de esta norma.
- OSHA 29 CFR 1910.95 — Occupational noise exposure. PEL (Permissible Exposure Limit): 90 dBA TWA / 8h con exchange rate 5 dB. Dosis = Σ(Ci/Ti) donde Ti = 8 / 2^((Li-90)/5).
- NIOSH REL (Recommended Exposure Limit) — 85 dBA TWA / 8h con exchange rate 3 dB. Más conservador que OSHA. Dosis = Σ(Ci/Ti) donde Ti = 8 / 2^((Li-85)/3).

### Código existente a consultar
- `app/src/main/cpp/oboe_engine.h` — NaturaSonicEngine con `onAudioReady`, pattern de consumers (VoiceAnalyzer, WhisperBridge, yamnetBuffer)
- `app/src/main/cpp/oboe_engine.cpp:136-210` — `onAudioReady`: leer input → captureBuffer_, luego AEC → processor → limiter → output → feed consumers. DosimetryAnalyzer se alimenta ANTES de processor (raw input)
- `app/src/main/cpp/voice_analyzer.h` / `.cpp` — patrón canónico a seguir: ring buffer con mutex, thread dedicado, `feedAudio()` desde onAudioReady, `getMetrics()` para JNI polling
- `app/src/main/cpp/audio_processor.cpp` — `computeRms` y `applyAdaptiveNoiseGate` ya calculan RMS del audio. La lógica RMS se reutiliza conceptualmente pero DosimetryAnalyzer necesita su propio cálculo con A-weighting
- `app/src/main/java/com/naturasonic/app/data/local/AppDatabase.kt` — Room v4 con 5 entities y migrations v1→v2, v2→v3, v3→v4. La nueva migration v4→v5 agrega `dosimetry_samples`
- `app/src/main/java/com/naturasonic/app/audio/VoiceHealthRepository.kt` — patrón de repositorio que combina polling JNI + StateFlow + persistencia Room. DosimetryRepository seguirá este patrón
- `app/src/main/java/com/naturasonic/app/di/AppModule.kt` — Hilt bindings de DAOs y repositorios
- `app/src/main/java/com/naturasonic/app/data/local/dao/AlertEventDao.kt` — patrón DAO con `getSince()` para queries temporales
- `app/src/main/cpp/native-lib.cpp` — JNI bridge unificado, donde se agregan los getters nativos

### Gotchas conocidas
- El `captureBuffer_` se modifica in-place por processor_ y limiter_ — DosimetryAnalyzer debe capturar los datos ANTES de `processor_.process()` para medir audio raw. Esto requiere copiar los datos del captureBuffer_ a un buffer propio antes del procesamiento, o alimentar con un puntero al buffer antes de la línea 157
- Los smartphones no tienen micrófonos calibrados profesionalmente — las lecturas dBA son aproximadas. Se necesita un offset de calibración configurable por el usuario (DataStore) y un disclaimer explícito
- La fórmula TWA acumula dosis con cada lectura: dose_increment = (measurement_interval / allowed_time) * 100. El `allowed_time` depende del exchange rate (OSHA: 5 dB, NIOSH: 3 dB)
- A-weighting a 48kHz requiere coeficientes biquad específicos para ese sample rate — los coeficientes genéricos a 44.1kHz NO son válidos
- VoiceAnalyzer usa ring buffer de 2s + thread dedicado a 500ms. DosimetryAnalyzer puede usar un ring buffer más corto (~0.5s = 24000 muestras) con análisis cada ~125ms para Leq de corta duración, promediando a 1s para la lectura de usuario
- Disclaimer PSAP: las lecturas dBA son informativas, no calibradas — NO son un sustituto de un dosímetro profesional certificado

### Modelo de datos

```sql
-- Nueva tabla: dosimetry_samples (migration v4→v5)
CREATE TABLE IF NOT EXISTS dosimetry_samples (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    dba REAL NOT NULL,
    leq REAL NOT NULL,
    oshaDosePercent REAL NOT NULL,
    nioshDosePercent REAL NOT NULL,
    peakDba REAL NOT NULL,
    recordedAt INTEGER NOT NULL
);
```

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Room v4 → v5 (migration incremental)
- Pipeline Oboe C++ (onAudioReady como punto de integración)
- VoiceAnalyzer pattern (ring buffer + thread + JNI polling)
- DataStore para preferencias de calibración
- Navigation Compose
- Notification channels existentes (agregar uno para dosimetría si necesario)

### ADD
- Ninguna dependencia nueva (A-weighting es IIR filter implementado directamente en C++)

### REMOVE
- Ninguna

### CONFIG
- `CMakeLists.txt` — agregar `dosimetry_analyzer.cpp` / `.h` a las fuentes de `libnaturasonic.so`

### Refinamientos a la Directiva durante este PRP
- Ninguno descubierto durante el mapeo

---

## Supuestos heredados

- [x] Pipeline Oboe 48kHz mono operativo con `onAudioReady` alimentando consumers
- [x] Room database v4 operativa con 5 entities y 3 migrations
- [x] Hilt configurado para inyección de DAOs, repositorios y managers
- [x] Navigation Compose con NavGraph centralizado
- [x] DataStore para preferencias de usuario (patrón establecido con ecoMode, headTracking, aecMode)
- [x] Notification channels configurados (alertas de fondo vía PRP-010)

### Supuestos adicionales (específicos de este PRP)
- [x] `onAudioReady` permite agregar un consumer más sin impacto significativo en latencia DSP (el cálculo RMS + A-weighting biquad es O(n) trivial sobre ~256 muestras)
- [x] Los micrófonos de smartphones Android modernos (API 29+) tienen sensibilidad suficiente para medir niveles de 30-120 dBA con precisión razonable (±3-5 dB sin calibración)

---

## Fuera de Alcance heredado

- Audiograma clínico o calibración audiológica profesional (convertiría la app en dispositivo médico)
- Integración con dosímetros profesionales externos vía Bluetooth
- Certificación ANSI/IEC para medidor de nivel sonoro (las lecturas son informativas)
- Grabación de audio crudo para análisis posterior
- Análisis espectral completo (FFT) con visualización de bandas de frecuencia — solo dBA ponderado

### Fuera de Alcance adicional (específico de este PRP)
- Calibración automática con tono de referencia (requeriría hardware certificado)
- Ponderación C o Z (solo A-weighting en esta iteración)
- Exportación del historial de dosimetría a PDF (ya existe WellnessReportGenerator — se puede extender en futuro PRP)
- Mapeo geográfico de niveles sonoros (noise map)

---

## Aprendizajes heredados de fases previas

- **2026-08-10**: Consumidores pesados de audio deben integrarse a nivel C++ con thread dedicado — Aplicar a DosimetryAnalyzer: ring buffer + thread + JNI polling, nunca cruzar JNI por frame.
- **2026-08-12**: Ring buffer C++ para consumidores Kotlin — Aplicar: ring buffer dedicado en DosimetryAnalyzer con mutex propio, JNI getter devuelve métricas computadas (no audio raw).
- **2026-08-13**: Double-buffer copy-modify-swap para parámetros DSP thread-safe — No aplica directamente (DosimetryAnalyzer es read-only del audio, no modifica EqSnapshot), pero el patrón de atomics para calibrationOffset sí aplica.
- **2026-08-03**: Comandos de validación son `./gradlew assembleDebug`, no npm.

---

## Plan de implementación

### Fase 1: Motor C++ — DosimetryAnalyzer con A-weighting IIR lock-free
- **Objetivo**: Crear clase `DosimetryAnalyzer` en C++ siguiendo el patrón VoiceAnalyzer (ring buffer + thread dedicado). Implementar filtro IIR A-weighting (biquad cascade) para 48kHz basado en IEC 61672. Computar dBA instantáneo desde RMS ponderada + offset de calibración configurable. Exponer struct `DosimetryData` (instantDba, leq, peakDba). Integrar `feedAudio()` en `onAudioReady` ANTES de `processor_.process()` para capturar audio raw. Agregar JNI bridge (`nativeGetDosimetryData`, `nativeSetCalibrationOffset`, `nativeStartDosimetry`, `nativeStopDosimetry`).
- **Validación**: `DosimetryAnalyzer` compila como parte de `libnaturasonic.so`, `feedAudio` se llama en `onAudioReady` sin regresión de latencia DSP, JNI getters devuelven datos válidos.

### Fase 2: DosimetryManager Kotlin + TWA dual OSHA/NIOSH
- **Objetivo**: Crear `DosimetryManager` singleton Hilt que pollea JNI cada ~1s, computa TWA con fórmula OSHA (90 dBA / ER 5 dB) y NIOSH (85 dBA / ER 3 dB), acumula dosis diaria con reset a medianoche, expone `StateFlow<DosimetryState>` (instantDba, leq, peakDba, oshaDosePercent, nioshDosePercent, exposureMinutes). Crear `DosimetryRepository` para persistencia. Integrar con `AudioService` para arranque/parada del analizador y observación de state.
- **Validación**: DosimetryManager computa dosis correctamente (test manual: 90 dBA constante × 8h = 100% OSHA, 85 dBA constante × 8h = 100% NIOSH). StateFlows observables desde Compose.

### Fase 3: Persistencia Room v4→v5 — DosimetrySample + alertas
- **Objetivo**: Crear entity `DosimetrySample` en Room con migration v4→v5. DAO con `insert`, `getSince(timestamp)`, `getLatestSamples(limit)`. DosimetryRepository persiste muestras cada 30s. Implementar alertas de dosis (50%, 80%, 100%) vía notificación de alta prioridad (canal dedicado, patrón PRP-010) + DataStore para umbrales de alerta configurables.
- **Validación**: Build compila, migration v4→v5 aplica sin crash, muestras se persisten y consultan correctamente.

### Fase 4: UI Compose — SoundscapeAnalyticsScreen con Canvas y alertas
- **Objetivo**: Crear `SoundscapeAnalyticsScreen` con: (1) medidor dBA grande con color semafórico, (2) gráfico Canvas de tendencia dBA del día con líneas de referencia 85/90, (3) barras de progreso OSHA/NIOSH con % dosis consumida, (4) cards de alertas activas, (5) opción de calibración manual (slider de offset dB). ViewModel observa DosimetryManager StateFlow. Navegación desde Settings → "Paisaje sonoro" y acceso directo desde HomeScreen. Disclaimer informativo: lecturas son aproximadas, no sustituyen dosímetro profesional.
- **Validación**: Pantalla renderiza correctamente, gráfico Canvas muestra datos reales, colores semafóricos responden al nivel, navegación funcional.

### Fase 5: Validación final
- **Objetivo**: Sistema funcionando end-to-end
- **Validación**:
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] Criterios de éxito cumplidos
  - [ ] Latencia DSP sin regresión (verificar ATrace/Perfetto)
  - [ ] PRP marcado COMPLETADO
  - [ ] CLAUDE.md actualizado si hay aprendizajes transversales

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

### 2026-08-21: DosimetryAnalyzer no necesita thread dedicado — inline en audio thread es suficiente
- **Error**: Se planificó inicialmente un thread dedicado con ring buffer (patrón VoiceAnalyzer), pero A-weighting + RMS es O(n) trivial (3 biquads × ~256 muestras + 1 sqrt/log10 cada 4800 muestras).
- **Fix**: Procesamiento inline en `feedAudio()` sin thread adicional. Resultados escritos con `std::atomic<float>` (lock-free en audio thread), leídos desde JNI thread. Sin mutex.
- **Aplicar en**: Consumidores de audio que solo necesitan métricas escalares (no espectro completo) pueden procesarse inline si el cómputo es O(n) con constante pequeña. El patrón de thread dedicado se reserva para cómputo pesado tipo autocorrelación YIN o inferencia ML.

---

## Anti-patrones

- No añadir dependencias externas para A-weighting o cálculo dBA — implementar como IIR filter directo en C++
- No medir audio post-processing para dosimetría ambiental — el VolumeLimiter cap a 85 dB invalida la lectura ambiental
- No computar FFT completo cuando solo se necesita RMS ponderada — A-weighting IIR es O(n) por muestra, FFT es O(n log n) innecesario
- No presentar lecturas dBA como calibradas profesionalmente — siempre incluir disclaimer
- No acumular dosis cuando el pipeline está detenido — la dosis solo avanza con mediciones reales
- No generar nuevos PRPs durante la ejecución de este PRP
- No commitear secrets

---

*PRP COMPLETADO — 2026-08-21.*
