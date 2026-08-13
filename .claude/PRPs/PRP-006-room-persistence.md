# PRP-006: Persistencia local con Room — perfiles de ecualización y configuraciones

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-13
> **Proyecto**: NaturaSonic

---

## Origen

> No hay brief origen — planificación directa. Las secciones heredadas (Directiva de Stack, Supuestos, Fuera de Alcance, Aprendizajes heredados) se derivan del checkpoint de CLAUDE.md y del análisis del codebase actual.

---

## Objetivo

Quiero que los perfiles de ecualización que creo se guarden de verdad — que cuando cierre la app y la vuelva a abrir, todo esté exactamente como lo dejé: mis bandas EQ, mi volumen, mi modo activo, y el perfil que tenía seleccionado. Y que cada modo (Conversación, Entretenimiento, Outdoor, Remote Mic) tenga su propio perfil por defecto que yo pueda personalizar en vez de usar valores fijos que no puedo cambiar.

## Por Qué

| Problema | Solución |
|----------|----------|
| Los ajustes de EQ se pierden al cerrar la app — cada vez hay que reconfigurar | Los perfiles se persisten en Room y se auto-restauran al iniciar |
| Los presets de cada modo son fijos en código — no se pueden personalizar | Cada modo carga su perfil default de Room, editable por el usuario |
| No hay forma de guardar varios perfiles y alternar entre ellos | CRUD completo de perfiles asociados a modos, con selección persistente |

**Valor**: La app se siente "mía" — mis ajustes de audio sobreviven entre sesiones sin tener que reconfigurar nada cada vez que la abro.

## Qué

### Criterios de éxito
- [ ] Al cerrar y reabrir la app, el perfil activo se restaura automáticamente con todas sus bandas EQ, volumen y configuración de noise suppression
- [ ] Cada AudioMode tiene un perfil default en Room que reemplaza los presets hardcodeados de AudioModeManager
- [ ] El usuario puede crear, editar, eliminar y seleccionar perfiles desde Settings
- [ ] Al cambiar de modo, se carga automáticamente el perfil default de ese modo (o el último usado en ese modo)
- [ ] Los perfiles pre-cargados (seed) se crean en la primera instalación con los valores actuales de AudioModeManager
- [ ] Build compila sin errores (`./gradlew assembleDebug`)

### Comportamiento esperado

El usuario abre la app por primera vez. Room se inicializa con 4 perfiles seed (uno por AudioMode) con los valores que hoy están hardcodeados en AudioModeManager. El modo CONVERSATION se activa con su perfil default. El usuario ajusta las bandas EQ en Settings, guarda el perfil. Cierra la app. Al reabrir, ese perfil se carga automáticamente y las bandas EQ se aplican al engine. Si cambia a modo ENTERTAINMENT, se carga el perfil default de ese modo. Si crea un segundo perfil para ENTERTAINMENT, puede alternar entre ambos. El perfil seleccionado por modo se persiste en UserPreferences.

### Casos borde

- **Primera instalación**: Room vacío → ejecutar seed con los 4 perfiles default (uno por modo)
- **Perfil seleccionado eliminado**: revertir al perfil default del modo actual
- **Base de datos corrupta**: fallbackToDestructiveMigration con re-seed automático
- **Bandas EQ con JSON malformado**: deserializar con fallback a FloatArray(10) de ceros
- **Migración futura (version 1→2)**: el schema ya exporta (`exportSchema = true`), preparado para migraciones

---

## Contexto

### Código existente a consultar

- `data/local/AppDatabase.kt` — Room database v1, 3 entities, 3 DAOs. Ya inyectado via Hilt
- `data/local/entity/AudioProfile.kt` — Entity con name, mode, eqBands (JSON string), amplificationLevel, noiseSuppressionEnabled, aecEnabled, isDefault, createdAt
- `data/local/dao/AudioProfileDao.kt` — CRUD completo con Flow reactivo. Tiene clearDefaultForMode + getDefaultProfile
- `data/preferences/UserPreferences.kt` — DataStore con currentMode, masterVolume, selectedProfileId, alertDetectionEnabled, etc.
- `audio/AudioModeManager.kt` — Presets hardcodeados por AudioMode en getModeConfig(). applyMode() aplica al engine
- `audio/OboeAudioEngine.kt` — JNI bridge: setEqBands, setAmplification, setNoiseSuppressionEnabled, setVolumeLimitDb
- `ui/screens/settings/SettingsViewModel.kt` — saveProfile/loadProfile/deleteProfile ya implementados pero sin auto-carga ni integración con modos
- `di/AppModule.kt` — Hilt module con provideDatabase + DAOs
- `app/src/main/cpp/audio_processor.h` — 10 bandas EQ biquad, frecuencias [125, 250, 500, 1k, 2k, 4k, 6k, 8k, 10k, 12k], rango -12 a +12 dB

### Gotchas conocidas

- `eqBands` en AudioProfile se serializa como JSON string de List<Float> via kotlinx.serialization. Mantener este formato por compatibilidad
- AudioModeManager tiene presets hardcodeados que deben convertirse en los valores seed de Room — no eliminar hasta que el seed exista
- `selectedProfileId` en UserPreferences usa stringPreferencesKey (no longPreferencesKey) — mantener la conversión toLongOrNull
- Room database version es 1 — cualquier cambio de schema requiere migración o fallbackToDestructiveMigration
- El audio engine opera a 48kHz — los perfiles solo guardan ganancias dB, no frecuencias (fijas en C++)

### Modelo de datos

El schema Room ya existe. No se crean tablas nuevas — se extiende la lógica sobre `audio_profiles` existente:

```kotlin
// Ya existe en data/local/entity/AudioProfile.kt
@Entity(tableName = "audio_profiles")
data class AudioProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String,           // AudioMode.key
    val eqBands: String,        // JSON: List<Float>, 10 bandas
    val amplificationLevel: Float,
    val noiseSuppressionEnabled: Boolean,
    val aecEnabled: Boolean,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## Directiva de Stack heredada

> No hay brief origen — el proyecto es Android nativo (Kotlin/Gradle/NDK). Directiva operativa:

### Clasificación
- **Tipo**: App Android nativa (PSAP/audífonos)
- **Compatibilidad con Praxis**: REPLACE (Android nativo, no web)

### KEEP
- Room (ya integrado, v2.6.x)
- DataStore Preferences (ya integrado para settings simples)
- Hilt (ya integrado para DI)
- kotlinx.serialization (ya usado para JSON de eqBands)

### ADD
- Nada — toda la infraestructura necesaria ya existe

### REPLACE
- Nada

### REMOVE
- Nada

### CONFIG
- Nada

### Refinamientos a la Directiva durante este PRP
- Ninguno — el stack actual cubre todo lo necesario para la persistencia

---

## Supuestos heredados

> No hay supuestos heredados — el bucle-agéntico verificará solo lo que este PRP declare explícitamente abajo.

### Supuestos adicionales (específicos de este PRP)
- [ ] Room database `naturasonic.db` se crea correctamente con las 3 entities actuales
- [ ] AudioProfileDao CRUD funciona (insert/update/delete/query)
- [ ] OboeAudioEngine.setEqBands() acepta FloatArray de 10 elementos y lo aplica al AudioProcessor C++
- [ ] Los valores hardcodeados en AudioModeManager.getModeConfig() son los defaults correctos para el seed

---

## Fuera de Alcance heredado

> No hay brief origen — Fuera de Alcance vacío. Límites explícitos:

### Fuera de Alcance adicional (específico de este PRP)
- UI de edición de perfiles (pantalla completa de gestión de perfiles con UX pulida) — eso es otro PRP
- Sincronización cloud de perfiles (backup/restore remoto)
- Exportar/importar perfiles como archivo
- Cambios al schema de Room (no se agregan columnas ni tablas nuevas)
- Modificaciones al pipeline de audio C++ (las frecuencias EQ y el procesamiento biquad no cambian)
- Audiogramas o calibración auditiva personalizada

---

## Aprendizajes heredados de fases previas

No hay brief origen — aprendizajes relevantes de CLAUDE.md:

- **2026-08-03**: NaturaSonic es Android nativo. Validación: `./gradlew assembleDebug` (build), `./gradlew lint` (lint). No aplican comandos npm.
- **2026-08-12**: Ring buffer C++ para consumidores Kotlin. Aplica tangencialmente: el patrón de "datos nativos → Kotlin → persistencia" ya está validado.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agéntico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: Repository pattern + seed de perfiles default
- **Objetivo**: Crear AudioProfileRepository como capa entre DAOs y ViewModels. Implementar seed de los 4 perfiles default (uno por AudioMode) con los valores actuales de AudioModeManager, ejecutado en la primera instalación.
- **Validación**:
  - [ ] AudioProfileRepository inyectado via Hilt con métodos CRUD + getActiveProfileForMode
  - [ ] Al crear la base de datos por primera vez, existen 4 perfiles con isDefault=true (uno por modo)
  - [ ] Los valores seed coinciden exactamente con los hardcodeados en AudioModeManager
  - [ ] Build compila: `./gradlew assembleDebug`

### Fase 2: Auto-restauración del perfil activo al iniciar
- **Objetivo**: Al arrancar la app (o al iniciar el audio engine), cargar automáticamente el último perfil seleccionado desde Room y aplicar sus parámetros al OboeAudioEngine.
- **Validación**:
  - [ ] Al abrir la app, el engine arranca con los valores del último perfil usado (no con defaults hardcodeados)
  - [ ] Si no hay perfil seleccionado, se usa el default del modo actual
  - [ ] selectedProfileId en UserPreferences se actualiza al cambiar de perfil

### Fase 3: Integración Perfil ↔ Modo
- **Objetivo**: AudioModeManager lee perfiles de Room en vez de presets hardcodeados. Al cambiar de modo, carga el perfil default (o último usado) de ese modo.
- **Validación**:
  - [ ] Al cambiar modo, el engine aplica el perfil de Room correspondiente
  - [ ] Si un modo no tiene perfiles en Room (caso imposible con seed, pero defensivo), usa el ModeConfig actual como fallback
  - [ ] Los presets hardcodeados de getModeConfig() se mantienen como fallback, no se eliminan

### Fase 4: SettingsViewModel conectado end-to-end
- **Objetivo**: Conectar SettingsViewModel con el Repository para que saveProfile persista en Room, loadProfile aplique al engine Y persista la selección, y los cambios de EQ en tiempo real se reflejen al guardar.
- **Validación**:
  - [ ] Guardar perfil → aparece en la lista de perfiles del modo actual
  - [ ] Cargar perfil → bandas EQ + amplificación + NS se aplican al engine inmediatamente
  - [ ] Eliminar perfil → si era el activo, revertir al default del modo
  - [ ] Editar bandas EQ → guardar → cerrar app → reabrir → bandas restauradas

### Fase 5: Validación final
- **Objetivo**: Sistema de persistencia funcionando end-to-end con build limpio
- **Validación**:
  - [ ] Criterios de éxito cumplidos (todos los checkboxes de la sección "Qué")
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] `./gradlew lint` sin warnings críticos
  - [ ] Flujo completo: crear perfil → editar EQ → cerrar app → reabrir → perfil restaurado
  - [ ] Cambio de modo → perfil correcto cargado desde Room

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

*(Vacía al inicio — se llena durante la ejecución del bucle-agéntico)*

---

## Anti-patrones

- No crear tablas Room nuevas — el schema existente cubre todo lo necesario
- No eliminar los presets hardcodeados de AudioModeManager hasta validar que el seed funciona — mantener como fallback
- No usar Room en el hilo principal (siempre suspend + coroutines)
- No duplicar lógica entre Repository y ViewModel — el ViewModel delega al Repository
- No hardcodear valores de EQ en el Repository — los defaults vienen del seed inicial
- No ignorar errores de deserialización JSON de eqBands — fallback a array de ceros
- NO generar nuevos PRPs durante la ejecución de este PRP

---

*PRP pendiente aprobación. No se ha modificado código.*
