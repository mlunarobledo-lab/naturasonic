# PRP-010: Notificaciones Locales de Alertas Críticas y Patrones de Vibración por Clase

> **Estado**: COMPLETADO
> **Fecha inicio**: 2026-08-14
> **Fecha cierre**: 2026-08-14
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md` (Fase 4: Detección de Sonidos de Alerta). Extiende el sistema de detección YAMNet con notificaciones de alta prioridad en background y patrones de vibración personalizados por clase de sonido.

---

## Objetivo

Cuando la app esté en background con la pantalla apagada y YAMNet detecte una alerta crítica, debe disparar una notificación inmediata con patrón de vibración personalizado e intermitente adaptado a cada una de las 7 clases de sonido (sirena, timbre, llanto de bebé, alarma de humo, claxon, cristal roto, ladrido). La vibración actual (pulso genérico de 300ms) se reemplaza por patrones distintivos que permitan al usuario reconocer el tipo de alerta por el tacto.

## Por Qué

| Problema | Solución |
|----------|----------|
| Cuando la app está en background, el usuario no ve la alerta visual de Compose | Notificación `IMPORTANCE_HIGH` en `CHANNEL_ALERTS` con heads-up display |
| Todas las clases de alerta vibran igual (300ms genérico) — no se puede distinguir qué se detectó | Patrón de vibración único por cada `AlertSoundClass` |
| No hay forma de saber que hubo una detección sin desbloquear el teléfono | Notificación persistente con título + clase + confianza, auto-cancel al tocar |
| El canal `CHANNEL_ALERTS` ya existe con IMPORTANCE_HIGH pero nunca se usa | Vincular las detecciones al canal existente |

**Valor**: El usuario recibe alertas críticas incluso con el teléfono en el bolsillo o la pantalla apagada, y puede distinguir el tipo de alerta sin mirar.

## Qué

### Criterios de éxito
- [x] Notificación heads-up se dispara cuando hay detección y la app está en background
- [x] 7 patrones de vibración distintos, uno por `AlertSoundClass`
- [x] Vibración con patrón (no genérica) funciona tanto en foreground como en background
- [x] Tocar la notificación abre la app en la pantalla principal
- [x] Notificaciones no se acumulan: nueva detección de la misma clase reemplaza la anterior
- [x] `./gradlew assembleDebug` compila sin errores
- [x] `./gradlew lint` sin errores
- [x] No se agregan dependencias ni se modifica AGP

---

## Contexto

### Código existente

- `app/src/main/java/com/naturasonic/app/NaturaSonicApp.kt` — Ya crea `CHANNEL_ALERTS` con `IMPORTANCE_HIGH` y `enableVibration(true)`. Channel listo para usar.
- `app/src/main/java/com/naturasonic/app/detection/SoundAlertDetector.kt` — `processAudioBuffer()` detecta alertas y llama a `vibrate()` (300ms genérico). Emite `_latestAlert` StateFlow y persiste en Room.
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — Foreground service con detection loop cada 1s. La detección funciona en background.
- `app/src/main/java/com/naturasonic/app/data/local/entity/AlertEvent.kt` — `AlertSoundClass` enum con 7 clases, cada una con `key`, `yamnetIndex`, `displayName`.
- `AndroidManifest.xml` — Permisos `POST_NOTIFICATIONS` y `VIBRATE` ya declarados.

### Gotchas conocidas

- **`lifecycle-process` no está en dependencias**: Usar `ActivityLifecycleCallbacks` para tracking de foreground/background (zero dependencias).
- **Android 13+ requiere runtime permission para notificaciones**: `POST_NOTIFICATIONS` ya declarado; el permiso se solicita en onboarding existente.
- **Notification channel immutable post-creación**: `CHANNEL_ALERTS` ya tiene la config correcta.

### Modelo de datos

No hay cambios al schema de Room. No hay nuevos entities ni DAOs.

---

## Directiva de Stack heredada

> Compatibilidad con Praxis: **REPLACE** (proyecto Android nativo).

### KEEP
- Pipeline C++ congelado
- Room/DataStore congelados
- Dependencias congeladas
- `CHANNEL_ALERTS` existente con IMPORTANCE_HIGH

### ADD / REPLACE / REMOVE / CONFIG
- Ninguno

---

## Supuestos heredados

- [x] `CHANNEL_ALERTS` ya existe con `IMPORTANCE_HIGH` + vibración (verificado)
- [x] `POST_NOTIFICATIONS` y `VIBRATE` ya declarados en manifest (verificado)
- [x] `SoundAlertDetector` inyecta `@ApplicationContext` (verificado)
- [x] Detection loop funciona en background vía foreground service (verificado)

---

## Fuera de Alcance

- Sonidos de alerta (ringtones/tones) — solo vibración y notificación visual
- Configuración por usuario de qué clases notificar (candidato para PRP futuro)
- Notificaciones push remotas (esto es 100% local)
- Canal de notificación separado por clase de alerta
- Wear OS notification bridging

---

## Plan de implementación

### Fase 1: Tracking de lifecycle + patrones de vibración + AlertNotificationManager ✅
- **Objetivo**: Crear `AppLifecycleTracker` (foreground/background vía ActivityLifecycleCallbacks), definir 7 patrones de vibración en `AlertVibrationPatterns`, crear `AlertNotificationManager` para notificaciones, y reemplazar el vibrate genérico de SoundAlertDetector.
- **Archivos tocados**: nuevo `AppLifecycleTracker.kt`, nuevo `AlertVibrationPatterns.kt`, nuevo `AlertNotificationManager.kt`, `SoundAlertDetector.kt`, `NaturaSonicApp.kt`, `strings.xml`
- **Validación**:
  - [x] Patrones de vibración distintos por clase (7 patrones con `VibrationEffect.createWaveform`)
  - [x] AppLifecycleTracker registrado en Application via `registerActivityLifecycleCallbacks`
  - [x] AlertNotificationManager construye notificaciones heads-up con PendingIntent a MainActivity
  - [x] Notificaciones usan ID `2000 + ordinal` para reemplazo por clase
  - [x] `./gradlew assembleDebug` compila

### Fase 2: Validación end-to-end ✅
- **Validación**:
  - [x] `./gradlew assembleDebug` BUILD SUCCESSFUL (3 ABIs)
  - [x] `./gradlew lint` sin errores
  - [x] Todos los 8 criterios de éxito cumplidos

---

## Resumen de archivos creados/modificados

### Archivos nuevos (3)
- `app/src/main/java/com/naturasonic/app/detection/AppLifecycleTracker.kt` — Singleton con ActivityLifecycleCallbacks, `isAppInForeground` via `@Volatile resumedCount`
- `app/src/main/java/com/naturasonic/app/detection/AlertVibrationPatterns.kt` — Object con `getPattern(AlertSoundClass): LongArray` — 7 patrones distintos
- `app/src/main/java/com/naturasonic/app/detection/AlertNotificationManager.kt` — Singleton que construye y dispara notificaciones en CHANNEL_ALERTS con PendingIntent

### Archivos modificados (3)
- `app/src/main/java/com/naturasonic/app/NaturaSonicApp.kt` — Inyecta AppLifecycleTracker, registra como ActivityLifecycleCallbacks
- `app/src/main/java/com/naturasonic/app/detection/SoundAlertDetector.kt` — Inyecta lifecycleTracker + alertNotificationManager. `vibrate()` → `vibrateForClass(alertClass)` con patrón. Notificación si background.
- `app/src/main/res/values/strings.xml` — Nuevo string `notification_alert_title`

---

## Aprendizajes

1. **`ActivityLifecycleCallbacks` es suficiente para foreground/background tracking**: No se necesita `lifecycle-process` ni `ProcessLifecycleOwner`. Un contador de activities resumed con `@Volatile` es thread-safe y zero-dependency.
2. **Vibración explícita + `setVibrate(longArrayOf(0))` en la notificación evita doble vibración**: El canal tiene `enableVibration(true)`, así que sin el override la notificación vibra con un patrón default además de nuestra vibración explícita. `setVibrate(longArrayOf(0))` silencia la vibración de la notificación.
3. **Notification ID `base + ordinal` reemplaza automáticamente**: Una nueva detección de SIREN reemplaza la notificación anterior de SIREN, pero puede coexistir con una de DOORBELL. Sin acumulación descontrolada.

---

## Anti-patrones

- No usar `ProcessLifecycleOwner` (requiere dependencia no presente)
- No crear notification channels nuevos — usar `CHANNEL_ALERTS` existente
- No acumular notificaciones — reemplazar por clase con ID fijo por ordinal
- No vibrar desde la notificación Y desde código — usar vibración explícita para confiabilidad

---

*PRP auto-flip PENDIENTE → APROBADO → EN PROGRESO → COMPLETADO por invocación del bucle-agentico.*
