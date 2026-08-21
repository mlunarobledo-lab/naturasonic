# PRP-018: Pipeline de Análisis y Diagnóstico de Voz (Voice Health Analytics)

> **Estado**: EN PROGRESO
> **Fecha**: 2026-08-17
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la Fase 3 (Motor PSAP y Procesamiento de Señal) con análisis de calidad vocal en tiempo real, aprovechando el pipeline Oboe existente a 48kHz con un ring buffer secundario dedicado y procesamiento asíncrono en thread C++.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic pueda analizar la voz del usuario en tiempo real y mostrar indicadores de fatiga vocal — específicamente Jitter (variabilidad del período de pitch) y Shimmer (variabilidad de amplitud entre ciclos consecutivos). Estos son marcadores estándar en fonoaudiología para evaluar la estabilidad de la voz. El análisis se ejecuta localmente en C++ sobre el audio capturado, sin enviar nada a la nube. Los resultados se muestran en una pantalla de Compose con gráficos dinámicos de barras y tendencias, permitiendo al usuario monitorear su salud vocal durante sesiones de uso prolongado.

## Por Qué

| Problema | Solución |
|----------|----------|
| NaturaSonic amplifica y procesa audio pero no ofrece retroalimentación sobre la calidad de la propia voz del usuario | Voice Health Analytics extrae métricas de estabilidad vocal (Jitter/Shimmer) del audio ya capturado |
| Usuarios con fatiga vocal (profesores, cantantes, speakers) no tienen herramientas accesibles para monitorear su voz en tiempo real | Análisis local continuo con indicadores visuales que alertan de degradación vocal |
| Las herramientas de análisis de voz existentes requieren grabación + análisis offline y software especializado de pago | Procesamiento en pipeline C++ integrado al stream existente — resultados en tiempo real sin pasos adicionales |
| No hay forma de detectar deterioro vocal progresivo durante una sesión | Gráfico de tendencia temporal muestra la evolución de Jitter/Shimmer a lo largo de la sesión |

**Valor**: Monitoreo de salud vocal en tiempo real — NaturaSonic evoluciona de amplificador a plataforma de bienestar auditivo y vocal completa.

## Qué

### Criterios de éxito
- [x] `VoiceAnalyzer` clase C++ con detección de pitch por autocorrelación (YIN simplificado) y cálculo de Jitter% y Shimmer%
- [x] Ring buffer secundario (`voiceBuffer_`) de 2s (96000 muestras a 48kHz) en `NaturaSonicEngine`, alimentado desde `onAudioReady`
- [x] Thread dedicado de análisis con mutex propio — no bloquea el audio thread
- [x] Struct `VoiceMetrics` (pitchHz, jitterPercent, shimmerPercent, isVoiced) expuesto vía JNI
- [x] Polling JNI getter `nativeGetVoiceMetrics()` desde Kotlin — patrón análogo a `getLatencyStats()`
- [x] `VoiceHealthScreen` Compose con barras de Jitter/Shimmer (umbrales normal/alerta/crítico), gráfico de tendencia temporal Canvas, indicador de pitch actual
- [x] Ruta `VOICE_HEALTH` en NavGraph, accesible desde Settings → "Salud vocal"
- [x] Build exitoso sin cambios en dependencias nativas ni AGP

### Comportamiento esperado

1. El usuario navega a Settings → "Salud vocal" → se abre `VoiceHealthScreen`.
2. Si el AudioService está activo, las métricas se actualizan automáticamente cada ~500ms.
3. El usuario habla normalmente mientras usa la app. El VoiceAnalyzer detecta segmentos voiced (con pitch) y calcula Jitter y Shimmer sobre ventanas de 2s.
4. Barras horizontales muestran Jitter% y Shimmer% con colores por rango:
   - Verde (normal): Jitter < 1%, Shimmer < 3%
   - Amarillo (alerta): Jitter 1-2%, Shimmer 3-6%
   - Rojo (crítico): Jitter > 2%, Shimmer > 6%
5. Gráfico de tendencia muestra los últimos 60 puntos de medición (~30s de historia).
6. Indicador de pitch muestra la frecuencia fundamental actual en Hz cuando se detecta voz.
7. Si no se detecta voz (silencio o ruido sin pitch), muestra "Sin voz detectada" y las barras se atenúan.
8. Las métricas NO se persisten en Room — son puramente de sesión (volátiles).

### Casos borde

- **Silencio / ruido sin pitch**: isVoiced = false, métricas se muestran atenuadas, no se agregan a tendencia.
- **Pitch fuera de rango vocal**: Rango F0 válido: 50-500 Hz. Fuera de ese rango se descarta como artefacto.
- **Audio muteado (output)**: El ring buffer sigue alimentándose del audio procesado — el análisis opera independientemente del estado del output.
- **AudioService detenido**: Las métricas se congelan en el último valor. La pantalla muestra "Servicio de audio inactivo".
- **Eco mode activo**: El análisis de voz no se throttlea por eco mode — el VoiceAnalyzer tiene su propio ciclo de análisis independiente.
- **Ventana insuficiente**: Si el ring buffer tiene menos de 1s de audio voiced, las métricas se marcan como no válidas (isVoiced = false).

---

## Contexto

### Documentación externa
- [Jitter and Shimmer Measurements (NCVS)](https://ncvs.org) — National Center for Voice and Speech: definiciones estándar de Jitter% y Shimmer%
- [YIN pitch detection algorithm](https://audition.ens.fr/adc/pdf/2002_JASA_YIN.pdf) — De Cheveigné & Kawahara, 2002: algoritmo de detección de pitch robusto y eficiente
- [Voice quality analysis parameters](https://en.wikipedia.org/wiki/Jitter_(voice)) — Definiciones estándar de parámetros de calidad vocal

### Código existente a consultar
- `app/src/main/cpp/oboe_engine.h` — yamnetBuffer_ ring buffer pattern (referencia para voiceBuffer_)
- `app/src/main/cpp/oboe_engine.cpp` — onAudioReady: punto de alimentación del ring buffer
- `app/src/main/cpp/whisper_bridge.h` — patrón de thread dedicado + mutex para procesamiento asíncrono
- `app/src/main/cpp/native-lib.cpp` — JNI bridge, patrón de getters (getLatencyStats, getYamnetAudioBuffer)
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — Kotlin JNI wrappers
- `app/src/main/java/com/naturasonic/app/ui/screens/performance/PerformanceScreen.kt` — patrón de pantalla con métricas dinámicas
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — lifecycle integration pattern

### Gotchas conocidas
- **Autocorrelación en 48kHz es costosa**: Para pitch de 50Hz, la ventana de lag es 960 muestras. Optimizar con búsqueda incremental y threshold en diferencia acumulativa normalizada (YIN step 4).
- **El ring buffer se llena desde el audio thread**: Solo escritura rápida (memcpy al ring buffer). El thread de análisis lee con su propio mutex — nunca bloquea el callback de audio.
- **Jitter y Shimmer requieren al menos 3 ciclos voiced consecutivos**: Con pitch mínimo de 50Hz (periodo 20ms) necesitamos al menos 60ms de voz continua. Con buffer de 2s hay margen amplio.
- **F0 por debajo de 50Hz o encima de 500Hz es artefacto**: Clampar el rango de búsqueda de pitch a períodos de 96-960 muestras (a 48kHz).

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
- Ninguna dependencia nueva — todo se implementa con C++ estándar

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
- [ ] El micrófono captura la voz del usuario con SNR suficiente para detección de pitch (>10dB sobre ruido de fondo)
- [ ] La latencia del thread de análisis (autocorrelación sobre 2s de audio) no supera 50ms en dispositivos de gama media

---

## Fuera de Alcance heredado

- Audiograma clínico
- Versión iOS
- Backend en la nube
- Integración con audífonos clínicos FDA
- VoIP / llamadas de voz

### Fuera de Alcance adicional (específico de este PRP)

- **Diagnóstico médico**: Las métricas son indicativas, no diagnósticas. Disclaimer PSAP aplica.
- **Grabación de voz**: No se almacena audio crudo — solo métricas calculadas.
- **Persistencia de métricas entre sesiones**: Las métricas son volátiles (de sesión). Persistir historial de salud vocal es scope futuro.
- **Análisis de formantes / espectrograma completo**: Solo F0 + Jitter + Shimmer. Análisis espectral avanzado es scope futuro.
- **Comparación con normas clínicas por edad/sexo**: Se usan umbrales genéricos estándar.

---

## Aprendizajes heredados

- **2026-08-12 (PRP-005)**: Ring buffer C++ para consumidores Kotlin. Patrón: ring buffer dedicado con mutex propio + JNI getter. Aplicable al voiceBuffer_.
- **2026-08-10 (PRP-003)**: Consumidores pesados de audio integrados a nivel C++ con thread dedicado. WhisperBridge como referencia. Aplicable al VoiceAnalyzer thread.
- **2026-08-17 (PRP-017)**: Offsets espaciales EQ deben integrarse en computeEqCoefficients. Lección: los cálculos que afectan coeficientes deben ejecutarse en el momento correcto del pipeline, no downstream.

---

## Plan de implementación

> IMPORTANTE: solo FASES aquí. Las subtareas se generan al ENTRAR a cada fase.

### Fase 1: VoiceAnalyzer C++ + Ring Buffer Secundario
- **Objetivo**: Crear `VoiceAnalyzer` clase C++ con detección de pitch por autocorrelación YIN simplificado, cálculo de Jitter% (variabilidad de período) y Shimmer% (variabilidad de amplitud). Agregar ring buffer `voiceBuffer_` de 2s en `NaturaSonicEngine`, alimentado desde `onAudioReady`. Thread dedicado de análisis que lee el ring buffer con su propio mutex cada 500ms.
- **Validación**: Build C++ exitoso. VoiceAnalyzer produce VoiceMetrics con valores plausibles para entrada voiced (pitch 80-300Hz, jitter <5%, shimmer <10%).

### Fase 2: JNI Bridge + VoiceHealthRepository Kotlin
- **Objetivo**: Exponer `VoiceMetrics` vía JNI getter `nativeGetVoiceMetrics()` que retorna float array [pitchHz, jitterPercent, shimmerPercent, isVoiced]. Kotlin wrapper en `OboeAudioEngine`. Crear `VoiceHealthRepository` singleton que poll cada 500ms y expone `StateFlow<VoiceMetrics>` con historial circular de 60 puntos para la tendencia.
- **Validación**: JNI getter invocable desde Kotlin. StateFlow emite métricas actualizadas.

### Fase 3: VoiceHealthScreen + ViewModel + Navegación
- **Objetivo**: Crear `VoiceHealthScreen` Compose con barras horizontales coloreadas por rango (normal/alerta/crítico), gráfico de tendencia Canvas (últimos 60 puntos), indicador de pitch Hz, estado de voz detectada. Crear `VoiceHealthViewModel`. Agregar ruta `VOICE_HEALTH` a NavGraph. Agregar botón "Salud vocal" en SettingsScreen.
- **Validación**: Pantalla renderiza. Barras y gráfico responden a datos del StateFlow. Colores cambian según umbrales.

### Fase 4: Integración con AudioService + Validación Final
- **Objetivo**: Integrar lifecycle del VoiceAnalyzer con AudioService (start/stop). Conectar el polling del VoiceHealthRepository con el ciclo del servicio. Validación final del build.
- **Validación**:
  - [ ] `./gradlew assembleDebug` exitoso en 3 arquitecturas
  - [ ] No hay cambios en dependencias nativas ni AGP
  - [ ] Voice analyzer se detiene limpiamente al detener AudioService

---

## Aprendizajes

> Esta sección crece con cada error.

### 2026-08-20: Alignment.Baseline no existe en Compose Row
- **Error**: `Row(verticalAlignment = Alignment.Baseline)` causa `Unresolved reference 'Baseline'`. En Compose, `Row.verticalAlignment` acepta `Alignment.Vertical` (Top, CenterVertically, Bottom) — no existe `Alignment.Baseline`.
- **Fix**: Usar `Alignment.Bottom` para alineación inferior de texto con diferentes tamaños de tipografía.
- **Aplicar en**: Cualquier pantalla Compose que necesite alinear texto de diferente tamaño en un Row.

### 2026-08-20: VoiceAnalyzer ring buffer integrado directamente en la clase (no en NaturaSonicEngine)
- **Error**: El PRP originalmente describía un `voiceBuffer_` en NaturaSonicEngine. En la implementación se decidió que VoiceAnalyzer gestiona su propio ring buffer internamente (como WhisperBridge gestiona su propio buffer), simplificando la integración.
- **Fix**: VoiceAnalyzer tiene su propio `ringBuffer_` de 96000 float con `bufferMutex_` dedicado. NaturaSonicEngine solo llama `voiceAnalyzer_.feedAudio()` desde `onAudioReady` — escritura rápida al ring buffer sin bloqueo.
- **Aplicar en**: Patrón confirmado: cada consumidor pesado de audio gestiona su propio buffer con su propio mutex. NaturaSonicEngine es el dispatcher, no el dueño de los buffers.

---

## Anti-patrones

- No ejecutar autocorrelación en el audio thread — demasiado costoso para el callback de 5ms
- No compartir mutex con el yamnetBuffer_ ni el whisperBridge_ — cada consumidor tiene su propio mutex
- No asumir que todo el audio es voiced — la mayoría del tiempo habrá silencio o ruido
- No reportar métricas sobre segmentos no-voiced — solo calcular Jitter/Shimmer cuando hay pitch estable
- No bloquear el audio thread con el mutex del ring buffer — la escritura debe ser rápida (memcpy)
- No persistir audio crudo — solo métricas calculadas

---

*PRP auto-aprobado 2026-08-17. Ejecución iniciada por bucle-agentico.*
