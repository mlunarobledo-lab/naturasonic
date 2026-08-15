# PRP-013: Sistema de Ajuste de Calibración Audiométrica (Audiograma Local)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-15
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la funcionalidad del ecualizador (Fase 3 del brief) con calibración personalizada basada en umbrales auditivos del usuario.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic me permita hacer un test rápido de audición en la propia app — que reproduzca tonos a distintas frecuencias y yo ajuste el volumen hasta apenas escucharlos. Con esos resultados, la app automáticamente calibra mi ecualizador para compensar las frecuencias que no escucho bien, usando una fórmula audiológica (Half-Gain). Así la amplificación se adapta a MI audición, no a un preset genérico.

## Por Qué

| Problema | Solución |
|----------|----------|
| Los presets de EQ genéricos no se adaptan a la pérdida auditiva individual — un usuario puede tener pérdida en agudos pero no en graves, y el preset le amplifica todo igual | Test audiométrico local que mide umbrales por frecuencia y genera ganancias personalizadas vía fórmula Half-Gain |
| Sin calibración, el usuario tiene que ajustar manualmente 10 sliders del ecualizador sin saber qué frecuencias necesita amplificar | El audiograma traduce automáticamente los umbrales a las 10 bandas del AudioProcessor vía interpolación, eliminando la necesidad de ajuste manual |
| Los audiogramas clínicos cuestan dinero y el resultado no se puede importar directamente a la app | Auto-test gratuito integrado en la app; el resultado se aplica con un toque al perfil de audio activo |

**Valor**: Amplificación personalizada a la audición real del usuario — la diferencia entre un PSAP genérico y uno que realmente compensa sus frecuencias débiles.

## Qué

### Criterios de éxito
- [x] AudiogramTestScreen reproduce tonos puros a 250, 500, 1000, 2000, 4000, 8000 Hz con control fino de volumen
- [x] El usuario puede ajustar el volumen en pasos de 5 dB (rango 0–80 dB HL) hasta encontrar su umbral por frecuencia
- [x] Los umbrales se persisten en Room como `AudiogramRecord` (nueva entity, migration v2→v3)
- [x] La fórmula Half-Gain traduce 6 umbrales audiométricos a 10 bandas del EQ con interpolación para bandas sin test directo
- [x] El resultado se aplica al AudioProcessor vía `nativeApplyProfile` existente
- [x] Build exitoso (`./gradlew assembleDebug`) sin cambios en dependencias nativas ni AGP
- [x] Disclaimer visible: "Esta prueba NO es un audiograma clínico"

### Comportamiento esperado

1. Usuario navega a Settings → "Calibrar mi audición".
2. Pantalla de bienvenida con disclaimer PSAP y botón "Empezar test".
3. Test paso a paso: 6 frecuencias × 2 oídos = 12 mediciones.
4. Para cada frecuencia: se reproduce un tono puro vía AudioTrack; el usuario sube el volumen hasta apenas escucharlo; confirma con botón.
5. Al completar: vista del audiograma (gráfico simple) + botón "Aplicar a mi ecualizador".
6. Al aplicar: fórmula Half-Gain calcula ganancias → `nativeApplyProfile` → perfil guardado en Room.
7. El audiograma se persiste como `AudiogramRecord` para futuras re-calibraciones.

### Casos borde

- **Sin auriculares BT conectados**: el test usa el speaker — advertir que los resultados no representan la escucha amplificada. Idealmente los auriculares deben estar conectados.
- **Resultados anteriores**: al entrar a la pantalla, mostrar el último audiograma si existe, con opción de re-hacer el test.
- **Umbrales extremos**: si todos los umbrales son ≤ 10 dB (audición normal), informar que no necesita calibración especial.
- **Interrupción del test**: si el usuario sale a mitad del test, los resultados parciales se descartan.
- **AudioService activo durante test**: el ToneGenerator usa AudioTrack independiente del pipeline Oboe — ambos pueden coexistir.

---

## Contexto

### Código existente a consultar
- `app/src/main/cpp/audio_processor.h` — 10 bandas EQ: [125, 250, 500, 1000, 2000, 4000, 6000, 8000, 10000, 12000] Hz. Gains: -12 a +12 dB. Biquad peaking Q=1.0.
- `app/src/main/cpp/audio_processor.cpp` — `applyProfile()` recibe bands[], count, amplification, noiseSuppression vía double-buffer atómico.
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — `applyProfile(bands, amplification, noiseSuppression)` wrapper JNI.
- `app/src/main/java/com/naturasonic/app/audio/AudioModeManager.kt` — `applyProfile(AudioProfile, sessionId)` deserializa JSON EQ bands y aplica.
- `app/src/main/java/com/naturasonic/app/data/local/AppDatabase.kt` — Room v2, 3 entities (AudioProfile, TranscriptionEntry, AlertEvent).
- `app/src/main/java/com/naturasonic/app/data/local/entity/AudioProfile.kt` — `eqBands` almacenado como JSON string de List<Float>.
- `app/src/main/java/com/naturasonic/app/data/repository/AudioProfileRepository.kt` — CRUD + dirty tracking + seed defaults.
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — rutas string-based, patrón hiltViewModel().
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsViewModel.kt` — patrón combine + stateIn para UiState.

### Gotchas conocidas
- `AudioTrack` para tonos puros requiere `AudioTrack.MODE_STATIC` o `MODE_STREAM` con buffer calculado a la frecuencia deseada. MODE_STREAM es más flexible para cambiar frecuencia en runtime.
- El pipeline Oboe y AudioTrack pueden coexistir — AudioTrack usa un stream de output separado del sistema.
- Las ganancias del EQ están clamped a [-12, +12] dB. La fórmula Half-Gain con umbrales > 24 dB HL producirá ganancias > 12 dB que se clampearán — esto es correcto para un PSAP (amplificación leve), no un audífono clínico.
- Room migration v2→v3 necesita `ALTER TABLE` o `CREATE TABLE` — para nueva entity solo es `CREATE TABLE`.

### Fórmula Half-Gain — mapeo audiograma → 10 bandas EQ

**Frecuencias del test audiométrico**: 250, 500, 1000, 2000, 4000, 8000 Hz
**Bandas del AudioProcessor**: 125, 250, 500, 1000, 2000, 4000, 6000, 8000, 10000, 12000 Hz

**Mapeo directo** (6 bandas con test):
| Banda EQ | Freq Hz | Fuente del umbral |
|----------|---------|-------------------|
| 1 | 250 | directo del test |
| 2 | 500 | directo del test |
| 3 | 1000 | directo del test |
| 4 | 2000 | directo del test |
| 5 | 4000 | directo del test |
| 7 | 8000 | directo del test |

**Interpolación/extrapolación** (4 bandas sin test):
| Banda EQ | Freq Hz | Método |
|----------|---------|--------|
| 0 | 125 | = umbral_250 (extrapolación plana) |
| 6 | 6000 | = (umbral_4000 + umbral_8000) / 2 (interpolación lineal) |
| 8 | 10000 | = umbral_8000 (extrapolación plana) |
| 9 | 12000 | = umbral_8000 (extrapolación plana) |

**Half-Gain**: `gain_dB[i] = clamp(threshold_dB_HL[i] * 0.5, 0, 12)`

Nota: solo ganancias positivas (amplificación). Si el umbral es 0–10 dB HL (audición normal), la ganancia es 0–5 dB (boost mínimo o nulo).

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Toda la infraestructura nativa actual
- Todas las dependencias en `build.gradle.kts` — congeladas
- AGP version — congelada

### ADD
- Ninguna dependencia nueva (AudioTrack es API nativa de Android)

### REMOVE / REPLACE / CONFIG
- Ninguno

### Refinamientos a la Directiva durante este PRP
- ToneGenerator usa `android.media.AudioTrack` nativo, no requiere dependencias adicionales.

---

## Supuestos heredados

- [x] El dispositivo Android del usuario tiene Android 10+ (API 29+)
- [x] El dispositivo tiene speaker o auriculares BT conectados para reproducir tonos
- [x] Room database v2 existente con 3 entities funcionales

### Supuestos adicionales (específicos de este PRP)
- [x] AudioTrack puede reproducir tonos puros de 250–8000 Hz sin distorsión perceptible en la mayoría de dispositivos Android
- [x] El `applyProfile` existente acepta las ganancias calculadas por Half-Gain sin cambios en la API C++

---

## Fuera de Alcance heredado

- Audiograma clínico o calibración audiológica certificada
- Streaming Auracast / broadcast LE Audio
- Versión iOS
- Backend en la nube

### Fuera de Alcance adicional (específico de este PRP)
- Calibración por conducción ósea (solo conducción aérea)
- Exportación del audiograma como PDF o imagen
- Importación de audiogramas de otras apps o dispositivos
- Recomendaciones de derivación a especialista basadas en resultados (eso sería acto médico)

---

## Aprendizajes heredados de fases previas

- **2026-08-13**: Double-buffer copy-modify-swap es el patrón para aplicar ganancias al AudioProcessor. `applyProfile()` sobreescribe todos los campos del snapshot de una vez — ideal para aplicar las 10 bandas del audiograma.
- **2026-08-15**: `std::atomic<bool>` para flags independientes en `onAudioReady` (PRP-012). El AudioProcessor ya maneja el thread-safety vía double-buffer.
- **2026-08-03**: Los algoritmos PSAP se implementan directamente en C++ — no hay que tocar el C++ para este PRP porque la fórmula Half-Gain vive en Kotlin y solo llama a `nativeApplyProfile` existente.

---

## Plan de implementación

### Fase 1: Room — AudiogramRecord Entity + DAO + Migration v2→v3
- **Objetivo**: Persistir audiogramas en la base de datos local. Nueva entity `AudiogramRecord` con umbrales por oído (6 frecuencias × 2 oídos). DAO con insert, getLatest, getAll. Migration v2→v3 (CREATE TABLE).
- **Validación**: Migration exitosa. Insert y query funcionan.

### Fase 2: ToneGenerator + AudiogramCalibration (lógica pura Kotlin)
- **Objetivo**: `ToneGenerator` que reproduce tonos puros vía AudioTrack a frecuencias específicas con control de volumen en dB. `AudiogramCalibration` que aplica Half-Gain con interpolación para mapear 6 umbrales → 10 bandas EQ.
- **Validación**: ToneGenerator produce tonos audibles. AudiogramCalibration genera ganancias correctas para inputs de prueba.

### Fase 3: AudiogramTestScreen + AudiogramViewModel (Compose UI)
- **Objetivo**: Pantalla step-by-step para test audiométrico. 6 frecuencias × 2 oídos. Slider de volumen en pasos de 5 dB. Disclaimer PSAP visible. Gráfico de audiograma con resultados. Botón "Aplicar" que invoca nativeApplyProfile.
- **Validación**: La pantalla renderiza. El flujo de test funciona. Los resultados se guardan en Room y se aplican al EQ.

### Fase 4: Navegación e integración con Settings
- **Objetivo**: Agregar ruta AUDIOGRAM_TEST al NavGraph. Agregar botón "Calibrar mi audición" en SettingsScreen que navega a la nueva pantalla.
- **Validación**: Navegación funciona ida y vuelta. El botón es visible en Settings.

### Fase 5: Validación Final
- **Objetivo**: Sistema funcionando end-to-end.
- **Validación**:
  - [x] `./gradlew assembleDebug` exitoso
  - [x] `./gradlew lint` sin errores nuevos
  - [x] No hay cambios en dependencias nativas ni AGP
  - [x] Los 10 gains generados por Half-Gain se aplican al AudioProcessor existente

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

---

## Anti-patrones

- No tocar el AudioProcessor C++ — toda la lógica de calibración vive en Kotlin
- No usar Oboe para reproducir tonos — AudioTrack es independiente y más simple para este caso
- No presentar esto como audiograma clínico — disclaimer obligatorio
- No hardcodear ganancias — siempre derivarlas de los umbrales vía Half-Gain

---

*PRP COMPLETADO — 2026-08-15. Todas las fases ejecutadas y validadas.*
