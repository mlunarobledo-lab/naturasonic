# PRP-008: Historial de Alertas Críticas — UI de consulta con filtros reactivos

> **Estado**: COMPLETADO
> **Fecha inicio**: 2026-08-14
> **Fecha cierre**: 2026-08-14
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Complementa la Fase 4 (Detección de Alertas con YAMNet/TFLite, PRP-005 cerrado) con la capa de consulta y visualización del historial persistido. La entidad `AlertEvent` y su DAO ya existen desde PRP-005; `SoundAlertDetector` ya persiste alertas en Room automáticamente. Este PRP construye la UI de historial sobre esa infraestructura.

---

## Objetivo

Quiero ver un historial de todas las alertas sonoras que NaturaSonic ha detectado — sirenas, timbres, llantos, alarmas de humo — con la hora exacta, el nivel de confianza, y poder filtrar por tipo de sonido. La pantalla actual muestra una tarjeta que desaparece a los 5 segundos; quiero un lugar donde volver a consultar qué pasó.

## Por Qué

| Problema | Solución |
|----------|----------|
| Las alertas detectadas se muestran 5s y desaparecen — el usuario no puede revisar qué pasó mientras no miraba el teléfono | Pantalla de historial con LazyColumn que muestra todas las alertas persistidas |
| No hay forma de filtrar por tipo de sonido (solo sirenas, solo alarmas de humo) | FilterChips reactivos por `AlertSoundClass` con query Room dinámico |
| El DAO tiene `getRecent(limit)` pero no soporta filtrado por clase ni rango de fechas | Queries adicionales en `AlertEventDao` con soporte Flow |
| No hay repositorio que encapsule la lógica de acceso al historial | `AlertHistoryRepository` siguiendo el patrón de `AudioProfileRepository` |

**Valor**: El usuario tiene un registro completo de detecciones que puede consultar en cualquier momento — útil para seguridad personal (saber que sonó una alarma mientras dormía) y para validar que la detección funciona correctamente.

## Qué

### Criterios de éxito
- [x] Pantalla de historial accesible desde HomeScreen via navegación
- [x] LazyColumn muestra alertas ordenadas por fecha descendente con agrupación por día
- [x] FilterChips permiten filtrar por tipo de alerta (todos, sirena, timbre, etc.)
- [x] Cada item muestra: icono de tipo, nombre, confianza (%), hora relativa
- [x] Flow reactivo: nuevas detecciones aparecen automáticamente sin refresh manual
- [x] `./gradlew assembleDebug` compila sin errores
- [x] No se modifica la versión de AGP ni se agregan dependencias nuevas
- [x] No se modifica el schema de Room (v1 sin migración)

### Comportamiento esperado

El usuario abre HomeScreen y ve un nuevo ActionCard "Historial de alertas". Al tocarlo, navega a AlertHistoryScreen. Ve una fila de FilterChips horizontales (Todos, Sirena, Timbre, Bebé, Alarma de humo, Claxon, Cristal, Ladrido). Debajo, LazyColumn con headers de fecha ("Hoy", "Ayer", "12 ago") y filas de alertas con icono, nombre del sonido, confianza en porcentaje, y hora. Si selecciona "Sirena", solo ve las sirenas. Si no hay alertas, ve un empty state. Si llega una nueva detección mientras está en la pantalla, aparece en la lista automáticamente.

### Casos borde

- **Sin alertas**: empty state con mensaje e icono
- **Filtro sin resultados**: empty state contextual ("No hay sirenas detectadas")
- **Muchas alertas (100+)**: LazyColumn con paginación implícita (Room emite el Flow completo, LazyColumn virtualiza)
- **Alerta llega mientras se ve el historial**: Flow emite automáticamente, LazyColumn se actualiza sin scroll manual
- **Rotación de pantalla**: ViewModel retiene estado de filtro

---

## Contexto

### Código existente a consultar

- `app/src/main/java/com/naturasonic/app/data/local/entity/AlertEvent.kt` — Entity con `id`, `soundClass`, `confidence`, `detectedAt`. Enum `AlertSoundClass` con 7 clases + `fromYamnetIndex()`
- `app/src/main/java/com/naturasonic/app/data/local/dao/AlertEventDao.kt` — DAO con `getRecent(limit)` Flow, `insert()`, `deleteOlderThan()`. Falta: query por soundClass, query con filtro combinado
- `app/src/main/java/com/naturasonic/app/data/local/AppDatabase.kt` — Database v1 con 3 entities. No se modifica schema
- `app/src/main/java/com/naturasonic/app/detection/SoundAlertDetector.kt` — Ya inserta en Room vía `scope.launch { alertEventDao.insert(...) }` en Dispatchers.Default
- `app/src/main/java/com/naturasonic/app/data/repository/AudioProfileRepository.kt` — Patrón a seguir: `@Singleton`, `@Inject constructor(dao)`, métodos delegando al DAO
- `app/src/main/java/com/naturasonic/app/di/AppModule.kt` — Hilt module. `AlertEventDao` ya se provee. No necesita cambios (el Repository se inyecta con `@Inject constructor`)
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — Routes object + NavHost. Agregar ruta ALERT_HISTORY
- `app/src/main/java/com/naturasonic/app/ui/screens/home/HomeScreen.kt` — `SoundAlertCard` + `AlertSoundClass.displayName()` + `.icon()` ya existen como extensiones privadas. Se necesitan como funciones accesibles desde AlertHistoryScreen
- `app/src/main/java/com/naturasonic/app/ui/screens/home/HomeViewModel.kt` — Patrón de ViewModel: `@HiltViewModel`, `combine()` de Flows, `stateIn()`

### Gotchas conocidas

- **`AlertSoundClass.displayName()` y `.icon()` son extensiones privadas en HomeScreen.kt**: Se necesitan extraer a la entity o a un archivo compartido para reutilizarlas en AlertHistoryScreen
- **`SoundAlertDetector` inserta en `Dispatchers.Default`**, no en `Dispatchers.IO`: funciona porque Room suspend functions ya manejan su propio dispatcher, pero vale notar
- **La tabla `alert_log` no tiene índice por `soundClass`**: Para la escala actual (<10K registros) un full-scan con WHERE es aceptable. Si crece, agregar `@Index` en una migración futura

### Modelo de datos

No hay cambios al schema de Room. La table `alert_log` ya tiene todos los campos necesarios. Las queries de filtrado usan WHERE sobre columnas existentes.

---

## Directiva de Stack heredada

> Compatibilidad con Praxis: **REPLACE** (proyecto Android nativo).

### KEEP
- Room v1 schema congelado (no hay migración)
- Pipeline nativo C++ congelado
- Hilt DI existente
- Navigation Compose existente

### ADD
- Ninguna dependencia nueva

### REPLACE / REMOVE / CONFIG
- Ninguno

---

## Supuestos heredados

- [x] `AlertEvent` entity existe en Room con campos `soundClass`, `confidence`, `detectedAt`
- [x] `AlertEventDao` existe con `getRecent()` Flow + `insert()` suspend
- [x] `SoundAlertDetector` persiste detecciones automáticamente
- [x] Hilt provee `AlertEventDao` via `AppModule`
- [x] `AlertSoundClass` enum tiene 7 clases con `key` string y `yamnetIndex`

---

## Fuera de Alcance

- Exportar historial a CSV/PDF
- Notificaciones push por alertas (candidato para PRP futuro)
- Sincronización en la nube del historial
- Paginación explícita con Paging 3 (LazyColumn virtualiza suficiente para v1)
- Gráficas/estadísticas de alertas por día/semana
- Eliminar alertas individuales desde la UI

---

## Plan de implementación

### Fase 1: DAO extendido + Repository ✅
- **Objetivo**: Añadir queries de filtrado al DAO y crear `AlertHistoryRepository` como capa de acceso.
- **Archivos a tocar**: `AlertEventDao.kt`, nuevo `AlertHistoryRepository.kt`
- **Validación**:
  - [x] Query `getAll()` Flow sin filtro
  - [x] Query `getByClass(soundClass)` Flow filtrado por tipo
  - [x] `AlertHistoryRepository` con `@Singleton @Inject constructor(dao)`
  - [x] `./gradlew assembleDebug` compila — BUILD SUCCESSFUL

### Fase 2: ViewModel + Estado UI ✅
- **Objetivo**: `AlertHistoryViewModel` que expone la lista filtrada y el estado de filtros como StateFlow a Compose.
- **Archivos a tocar**: nuevo `AlertHistoryViewModel.kt`
- **Validación**:
  - [x] StateFlow combina filtro seleccionado + lista de alertas agrupadas por `LocalDate`
  - [x] Cambio de filtro re-emite la lista filtrada vía `flatMapLatest`
  - [x] `./gradlew assembleDebug` compila — BUILD SUCCESSFUL

### Fase 3: UI Compose + Navegación ✅
- **Objetivo**: `AlertHistoryScreen` con LazyColumn, FilterChips, headers de fecha, empty state. Integrar en NavGraph y agregar entrada desde HomeScreen.
- **Archivos tocados**: nuevo `AlertHistoryScreen.kt`, `NavGraph.kt`, `HomeScreen.kt`, `AlertEvent.kt` (displayName + fromKey en enum)
- **Validación**:
  - [x] LazyColumn muestra alertas agrupadas por día (headers "Hoy", "Ayer", fecha)
  - [x] FilterChips filtran por tipo de alerta (7 clases + "Todos")
  - [x] Navegación Home → Historial → Back funcional (ruta ALERT_HISTORY + popBackStack)
  - [x] Empty state cuando no hay alertas (contextual con/sin filtro)
  - [x] `displayName` movido al enum `AlertSoundClass`, `icon()` hecho `internal`
  - [x] `./gradlew assembleDebug` compila — BUILD SUCCESSFUL

### Fase 4: Validación end-to-end + cierre ✅
- **Objetivo**: Build limpio, lint sin errores críticos, revisión de criterios de éxito.
- **Validación**:
  - [x] `./gradlew assembleDebug` exitoso — BUILD SUCCESSFUL
  - [x] `./gradlew lint` sin errores — 0 errores
  - [x] Todos los criterios de éxito cumplidos — 8/8

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

**2026-08-14 — displayName como propiedad del enum, no extensión privada**
- **Error**: `displayName()` y `icon()` eran extensiones privadas en HomeScreen.kt — no reutilizables desde AlertHistoryScreen.
- **Fix**: `displayName` movido como propiedad del constructor de `AlertSoundClass`. `icon()` hecho `internal` en HomeScreen para acceso cross-package. `fromKey()` añadido al companion object para resolver `soundClass: String` → `AlertSoundClass?`.
- **Aplicar en**: Cualquier futuro enum con display strings que necesite reutilización cross-screen — definir displayName como propiedad del enum, no como extensión privada.

**2026-08-14 — No se requiere migración Room al usar queries sobre columnas existentes**
- Las queries de filtrado (`WHERE soundClass = :soundClass`) operan sobre columnas ya definidas en el schema v1. No se toca `AppDatabase`, no se incrementa versión, no se escribe migración. El DAO acepta queries nuevas sin impacto en el schema.

---

## Anti-patrones

- No modificar el schema de Room — no hay migración
- No duplicar `displayName()`/`icon()` — extraer a ubicación compartida
- No usar Paging 3 — LazyColumn virtualiza suficiente para la escala actual
- No agregar dependencias nuevas — todo se resuelve con APIs existentes

---

*PRP auto-flip PENDIENTE → APROBADO → EN PROGRESO por invocación del bucle-agentico.*
