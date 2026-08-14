# PRP-011: Cloud Backup Offline-First — Respaldo de Perfiles EQ con WorkManager

> **Estado**: COMPLETADO
> **Fecha inicio**: 2026-08-14
> **Fecha cierre**: 2026-08-14
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md` (sección KEEP: "Supabase como backend para sync de perfiles entre dispositivos"). Implementa la infraestructura local de sincronización offline-first con dirty tracking, Room migration, y WorkManager Worker inyectado con Hilt. La capa cloud queda como interfaz abstracta — el backend concreto (Supabase/Firebase) se enchufa después con un solo swap de binding.

---

## Objetivo

Diseñar un mecanismo de sincronización offline-first que tome los perfiles de ecualización de Room y los respalde en la nube. Las consultas del motor de audio siempre se resuelven contra Room local. Un Worker de WorkManager (inyectado con Hilt) sube los cambios pendientes cuando hay conectividad. Campos `isSynced` y `lastModified` en la entidad de Room para control de conflictos.

## Por Qué

| Problema | Solución |
|----------|----------|
| Los perfiles EQ solo viven en Room — se pierden con uninstall o factory reset | Respaldo cloud con dirty tracking (`isSynced` + `lastModified`) |
| No hay infraestructura para sync background sin bloquear UI/audio | WorkManager con constraint `NetworkType.CONNECTED` + `@HiltWorker` |
| Room v1 no tiene campos de control de sync | Migración v1→v2 con ALTER TABLE |
| No hay abstracción de backend cloud — hardcodear un servicio bloquea futuras opciones | `CloudSyncApi` interface + binding Hilt → swap single-point |

**Valor**: Infraestructura completa de sync offline-first lista para enchufar cualquier backend. Los perfiles del usuario sobreviven cambios de dispositivo.

## Qué

### Criterios de éxito
- [x] Room migración v1→v2 sin pérdida de datos (ALTER TABLE ADD COLUMN)
- [x] `AudioProfile` con campos `isSynced: Boolean` y `lastModified: Long`
- [x] `AudioProfileRepository` marca `isSynced = false` y actualiza `lastModified` en cada write
- [x] `ProfileSyncWorker` (@HiltWorker) consulta perfiles dirty y los envía vía `CloudSyncApi`
- [x] `SyncManager` programa sync con WorkManager constraint `NetworkType.CONNECTED`
- [x] `CloudSyncApi` interface + stub implementación (backend real pendiente)
- [x] `./gradlew assembleDebug` compila sin errores
- [x] `./gradlew lint` sin errores

---

## Contexto

### Código existente

- `app/src/main/java/com/naturasonic/app/data/local/entity/AudioProfile.kt` — Entity actual: id, name, mode, eqBands, amplificationLevel, noiseSuppressionEnabled, aecEnabled, isDefault, createdAt. Sin campos de sync.
- `app/src/main/java/com/naturasonic/app/data/local/AppDatabase.kt` — Room v1, 3 entities, `exportSchema = true`.
- `app/src/main/java/com/naturasonic/app/data/local/dao/AudioProfileDao.kt` — CRUD básico, no tiene queries de sync.
- `app/src/main/java/com/naturasonic/app/data/repository/AudioProfileRepository.kt` — save/update/delete/setAsDefault/ensureDefaultProfiles. No marca dirty flags.
- `app/src/main/java/com/naturasonic/app/di/AppModule.kt` — Provides DAOs desde AppDatabase.
- `app/src/main/java/com/naturasonic/app/NaturaSonicApp.kt` — @HiltAndroidApp, no implementa Configuration.Provider.
- `gradle/libs.versions.toml` — Hilt 2.52, Room 2.6.1. No tiene WorkManager ni hilt-work.

### Gotchas conocidas

- **Room migration obligatoria**: v1→v2 con `ALTER TABLE`. Si no hay migration, Room destruye la DB (fallbackToDestructiveMigration no está activado, ni debe estarlo).
- **WorkManager + Hilt**: Requiere `Configuration.Provider` en Application + `HiltWorkerFactory`. Default initializer debe deshabilitarse en AndroidManifest.
- **`hilt-compiler` de AndroidX es diferente de `hilt-android-compiler` de Dagger**: Ambos necesarios como KSP processors para `@HiltWorker`.
- **Dependencias nuevas mínimas**: `work-runtime-ktx` + `hilt-work` + `hilt-compiler` (AndroidX). Son AndroidX estándar, no afectan el pipeline C++ ni AGP.

### Modelo de datos

**AudioProfile v2** (campos nuevos en negrita):

| Campo | Tipo | Default | Nota |
|-------|------|---------|------|
| id | Long | autoGenerate | PK |
| name | String | — | |
| mode | String | — | AudioMode.key |
| eqBands | String | — | JSON serialized |
| amplificationLevel | Float | — | |
| noiseSuppressionEnabled | Boolean | — | |
| aecEnabled | Boolean | — | |
| isDefault | Boolean | false | |
| createdAt | Long | currentTimeMillis | |
| **isSynced** | **Boolean** | **false** | **Dirty flag — false = pending upload** |
| **lastModified** | **Long** | **currentTimeMillis** | **Timestamp de última modificación local** |

**Migración SQL**:
```sql
ALTER TABLE audio_profiles ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0;
ALTER TABLE audio_profiles ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0;
```

---

## Directiva de Stack heredada

> Compatibilidad con Praxis: **REPLACE** (proyecto Android nativo).

### KEEP
- Pipeline C++ congelado
- AGP 8.7.3 congelado
- Room schema exportado

### ADD (mínimo, aprobado implícitamente por la request del usuario)
- `androidx.work:work-runtime-ktx:2.9.1` — WorkManager
- `androidx.hilt:hilt-work:1.2.0` — @HiltWorker
- `androidx.hilt:hilt-compiler:1.2.0` — AndroidX Hilt KSP processor

### REPLACE / REMOVE / CONFIG
- Ninguno

---

## Supuestos heredados

- [x] Room v1 con `AudioProfile` entity (verificado)
- [x] Hilt 2.52 + KSP configurado (verificado)
- [x] `exportSchema = true` en AppDatabase (verificado — migration schema se auto-genera)
- [x] WorkManager compatible con minSdk 29 (WorkManager soporta API 14+, verificado por docs)

---

## Fuera de Alcance

- Backend cloud real (Supabase/Firebase/REST) — requiere credenciales (c1) y brief propio
- Sync bidireccional (cloud → local) — este PRP solo sube (backup)
- Sync de transcripciones o alert events — solo perfiles EQ
- Conflicto resolution avanzado (merge de campos individuales) — last-write-wins con `lastModified`
- UI de estado de sincronización (candidato para PRP futuro)

---

## Plan de implementación

### Fase 1: Dependencies + Room migration v1→v2 ✅ COMPLETADO
- **Objetivo**: Agregar WorkManager + hilt-work al version catalog y build.gradle.kts. Agregar `isSynced` y `lastModified` a `AudioProfile`. Migration v1→v2. Queries nuevas en DAO.
- **Archivos tocados**: `libs.versions.toml`, `app/build.gradle.kts`, `AudioProfile.kt`, `AppDatabase.kt`, `AudioProfileDao.kt`, `AppModule.kt`
- **Validación**:
  - [x] Migration v1→v2 sin fallbackToDestructiveMigration
  - [x] Campos nuevos en entity con defaults correctos
  - [x] Queries `getUnsyncedProfiles()` y `markAsSynced()` en DAO
  - [x] `./gradlew assembleDebug` compila

### Fase 2: WorkManager + CloudSyncApi + ProfileSyncWorker ✅ COMPLETADO
- **Objetivo**: Configurar Hilt + WorkManager (Configuration.Provider + disable default init). Crear CloudSyncApi interface, stub, ProfileSyncWorker, SyncManager.
- **Archivos tocados**: `NaturaSonicApp.kt`, `AndroidManifest.xml`, nuevo `CloudSyncApi.kt`, nuevo `StubCloudSyncApi.kt`, nuevo `ProfileSyncWorker.kt`, nuevo `SyncManager.kt`, `AppModule.kt`
- **Validación**:
  - [x] @HiltWorker con @AssistedInject compila
  - [x] WorkManager configurado con HiltWorkerFactory
  - [x] SyncManager programa trabajo con network constraint
  - [x] `./gradlew assembleDebug` compila

### Fase 3: Integración en AudioProfileRepository + validación end-to-end ✅ COMPLETADO
- **Objetivo**: AudioProfileRepository marca dirty flags en writes y schedules sync. Build, lint, criterios cumplidos.
- **Archivos tocados**: `AudioProfileRepository.kt`
- **Validación**:
  - [x] `save()`, `update()`, `setAsDefault()` marcan `isSynced=false` y actualizan `lastModified`
  - [x] Cada write dispara `syncManager.scheduleSync()`
  - [x] `./gradlew assembleDebug` exitoso
  - [x] `./gradlew lint` sin errores
  - [x] Todos los criterios de éxito cumplidos

---

## Resumen de archivos creados/modificados

| Archivo | Acción |
|---------|--------|
| `gradle/libs.versions.toml` | ADD: workManager, hiltWork versions + libs |
| `app/build.gradle.kts` | ADD: hilt-work, hilt-compiler, work-runtime-ktx |
| `AudioProfile.kt` | ADD: isSynced, lastModified fields |
| `AppDatabase.kt` | MOD: version 2, MIGRATION_1_2 |
| `AudioProfileDao.kt` | ADD: getUnsyncedProfiles(), markAsSynced() |
| `AppModule.kt` | ADD: provideCloudSyncApi, provideWorkManager |
| `NaturaSonicApp.kt` | MOD: implements Configuration.Provider + HiltWorkerFactory |
| `AndroidManifest.xml` | ADD: provider to disable default WorkManager init |
| `CloudSyncApi.kt` | NEW: interface para backend cloud |
| `StubCloudSyncApi.kt` | NEW: implementación stub (log-only) |
| `ProfileSyncWorker.kt` | NEW: @HiltWorker CoroutineWorker |
| `SyncManager.kt` | NEW: Singleton que programa sync via WorkManager |
| `AudioProfileRepository.kt` | MOD: dirty flags + scheduleSync en writes |

---

## Aprendizajes

- **Zero errores en 3 fases**: La planificación upfront de dependencies y gotchas (Room migration, WorkManager+Hilt wiring) eliminó iteraciones. El patrón "stub interface + binding swap" es sólido para backends no definidos.
- **`Configuration.Provider` property syntax**: En Kotlin, `override val workManagerConfiguration` (property, no fun) es la forma correcta para Hilt+WorkManager con `@HiltWorkerFactory`. El approach `override fun getWorkManagerConfiguration()` era el patrón viejo pre-2.6.
- **Seeds sin dirty flags**: `ensureDefaultProfiles()` NO marca `isSynced=false` porque los seeds son datos locales por defecto, no cambios del usuario. El sync solo se dispara con writes explícitos del usuario (save/update/setAsDefault).

---

## Anti-patrones

- No usar `fallbackToDestructiveMigration` — destruye datos del usuario
- No hardcodear un backend cloud — usar interfaz para swap limpio
- No bloquear el hilo principal ni el audio thread con sync
- No hacer sync síncrono en el repository — delegar a WorkManager
- No re-inventar scheduling — WorkManager maneja retry, backoff, constraints nativamente

---

*PRP-011 COMPLETADO. Auto-flip EN PROGRESO → COMPLETADO.*
