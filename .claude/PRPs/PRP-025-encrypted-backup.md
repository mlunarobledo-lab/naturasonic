# PRP-025: Sistema de Guardado y Sincronizacion en la Nube Cifrada (Encrypted Cloud Backup)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-23
> **Proyecto**: NaturaSonic

---

## Origen

> Planificacion directa, sin brief previo. Extiende la infraestructura offline-first de PRP-011 (CloudSyncApi stub + ProfileSyncWorker) con cifrado end-to-end usando la passphrase AES-256 de PRP-023 (KeyStoreManager) y una UI de gestion de backups.

---

## Objetivo

Quiero que NaturaSonic permita al usuario respaldar todos sus datos (perfiles de audio, audiogramas, historial de alertas, metricas vocales, muestras de dosimetria) en la nube de forma cifrada e2e. El backup se cifra localmente con la passphrase AES-256 del KeyStoreManager antes de salir del dispositivo — el backend (simulado por ahora) nunca ve datos en claro. El usuario puede activar backup automatico, ver la fecha del ultimo respaldo, y disparar un guardado manual.

## Por Que

| Problema | Solucion |
|----------|----------|
| PRP-011 creo la infraestructura de sync (CloudSyncApi + ProfileSyncWorker + SyncManager) pero solo sincroniza perfiles de audio sin cifrar — las 5 entities restantes no se respaldan | BackupWorker nuevo que serializa las 6 entities Room a JSON y las cifra con AES-256-GCM antes de subir |
| La DB local esta cifrada con SQLCipher (PRP-023), pero si el usuario cambia de dispositivo pierde todos sus datos — no hay mecanismo de export/restore | El backup cifrado permite restaurar en otro dispositivo si tiene la misma passphrase (futuro: pairing flow) |
| El ProfileSyncWorker existente envia datos en claro al CloudSyncApi — si se conecta un backend real, los datos del usuario viajan sin proteccion | El nuevo BackupWorker cifra ANTES de entregar al CloudSyncApi, garantizando e2e encryption |

**Valor**: Completar el pipeline offline-first que PRP-011 dejo como stub. El cifrado e2e es un diferenciador critico para un PSAP que maneja datos de salud auditiva.

## Que

### Criterios de exito
- [ ] BackupWorker serializa las 6 entities Room a JSON y cifra el payload con AES-256-GCM usando la passphrase del KeyStoreManager
- [ ] El backup se entrega al CloudSyncApi (stub — log del tamano del payload cifrado + timestamp)
- [ ] Backup automatico configurable via DataStore (default: desactivado)
- [ ] Backup manual disparable desde CloudBackupScreen
- [ ] La fecha del ultimo backup exitoso se persiste y se muestra en la UI
- [ ] CloudBackupScreen accesible desde Settings con toggle, fecha del ultimo backup, boton manual
- [ ] `./gradlew assembleDebug` compila sin errores

### Comportamiento esperado

El usuario accede a Settings → "Copia de seguridad". Ve un toggle "Backup automatico" (desactivado por defecto), la fecha del ultimo respaldo (o "Nunca" si no hay), y un boton "Respaldar ahora". Al presionar el boton, se dispara el BackupWorker que: (1) consulta las 6 tablas Room, (2) serializa a un JSON unificado, (3) cifra con AES-256-GCM usando la passphrase del KeyStoreManager, (4) entrega el payload cifrado al CloudSyncApi, (5) si exito, actualiza la fecha del ultimo backup en DataStore. Con backup automatico activado, se programa un PeriodicWorkRequest cada 24h con restriccion de red + bateria no baja.

### Casos borde

- **CloudSyncApi stub retorna false**: BackupWorker retorna Result.retry() — WorkManager re-intenta con backoff exponencial
- **BD vacia (primer uso)**: el backup serializa arrays vacios — payload minimo cifrado (~100 bytes)
- **Backup manual durante backup automatico en curso**: ExistingWorkPolicy.REPLACE cancela el anterior y ejecuta el nuevo
- **Passphrase no disponible (reinstalacion)**: si KeyStoreManager no tiene passphrase, BackupWorker la genera antes de cifrar (getOrCreateDatabasePassphrase)
- **Sin red + backup manual**: el WorkRequest se encola con NetworkType.CONNECTED y se ejecuta cuando haya red

---

## Contexto

### Codigo existente a consultar
- `sync/CloudSyncApi.kt` — interface con `uploadProfiles(List<AudioProfile>): Boolean`. Se extiende con `uploadBackup(ByteArray): Boolean`
- `sync/StubCloudSyncApi.kt` — stub log-only. Se extiende con el nuevo metodo
- `sync/SyncManager.kt` — encola ProfileSyncWorker. Se extiende para encolar BackupWorker
- `sync/ProfileSyncWorker.kt` — patron @HiltWorker + CoroutineWorker a seguir
- `security/KeyStoreManager.kt` — `getOrCreateDatabasePassphrase(): ByteArray` (32 bytes AES-256)
- `data/local/AppDatabase.kt` — 6 DAOs disponibles para consulta
- `data/preferences/UserPreferences.kt` — DataStore con patron establecido
- `ui/navigation/NavGraph.kt` — 19 rutas. Se agrega CLOUD_BACKUP
- `ui/screens/settings/SettingsScreen.kt` — patron TextButton + Icon para navegacion

### Gotchas conocidas
- **AES-256-GCM requiere IV unico por operacion**: generar 12 bytes random con SecureRandom por cada cifrado; prepend al ciphertext (IV || ciphertext || tag). El descifrado (futuro) lee los primeros 12 bytes como IV
- **javax.crypto.Cipher en Android**: usar `Cipher.getInstance("AES/GCM/NoPadding")` + `GCMParameterSpec(128, iv)` + `SecretKeySpec(passphrase, "AES")`
- **Serializacion JSON de entities Room**: usar kotlinx.serialization (ya en el proyecto — AudioProfileRepository usa `Json.encodeToString`). Las entities necesitan @Serializable
- **PeriodicWorkRequest minimo 15 minutos**: Android enforce este minimo. Para backup diario usar `repeatInterval = 24, TimeUnit.HOURS`
- **No modificar Room schema (v5)**: el PRP NO agrega entities ni migraciones. Todo se resuelve con DAOs existentes + DataStore

### Modelo de datos (cambios)

No hay cambios en Room schema (v5, 6 entities). Los cambios son:

1. **`CloudSyncApi.kt`**: nuevo metodo `uploadBackup(encryptedData: ByteArray): Boolean`
2. **`StubCloudSyncApi.kt`**: implementacion stub del nuevo metodo
3. **`BackupCryptoManager.kt`** (nuevo): cifrado/descifrado AES-256-GCM con IV prepended
4. **`BackupWorker.kt`** (nuevo): @HiltWorker que serializa 6 entities + cifra + sube
5. **`CloudBackupManager.kt`** (nuevo): singleton Hilt que coordina backup manual + automatico
6. **`UserPreferences.kt`**: 2 keys nuevas (autoBackupEnabled: Boolean, lastBackupTimestamp: Long)
7. **`CloudBackupScreen.kt`** + `CloudBackupViewModel.kt` (nuevos): UI de gestion
8. **`NavGraph.kt`**: ruta CLOUD_BACKUP
9. **`SettingsScreen.kt`**: entrada "Copia de seguridad"

---

## Directiva de Stack heredada

> Derivada del proyecto existente NaturaSonic.

### Clasificacion
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### ADD (extensiones para este PRP)
- Ninguna dependencia nueva. `javax.crypto` y `kotlinx.serialization` ya estan disponibles. WorkManager ya esta configurado (PRP-011).

---

## Supuestos heredados

- [ ] Room database v5 con 6 entities (AudioProfile, TranscriptionEntry, AlertEvent, AudiogramRecord, VoiceMetricsEntry, DosimetrySample) con DAOs funcionales
- [ ] KeyStoreManager singleton provee passphrase de 32 bytes via getOrCreateDatabasePassphrase()
- [ ] WorkManager configurado con HiltWorkerFactory en NaturaSonicApp
- [ ] kotlinx.serialization disponible (usado en AudioProfileRepository)
- [ ] CloudSyncApi + StubCloudSyncApi + SyncManager funcionales desde PRP-011

---

## Fuera de Alcance

- **Restore/descifrado de backup**: solo se implementa el guardado cifrado; la restauracion queda para un PRP futuro
- **Backend real**: CloudSyncApi sigue siendo stub; enchufar un backend real (Firebase, S3, Supabase Storage) queda fuera
- **Backup incremental**: cada backup es snapshot completo de las 6 tablas; backup delta/incremental queda fuera
- **Pairing entre dispositivos**: compartir passphrase entre dispositivos para restaurar en otro equipo queda fuera
- **Compresion**: el payload JSON se cifra sin comprimir; GZIP pre-cifrado queda fuera

---

## Aprendizajes heredados de fases previas

**2026-08-03: El proyecto NaturaSonic es Android nativo (no web)**
- Validacion: `./gradlew assembleDebug`. No aplican npm/tsc.

---

## Plan de implementacion

### Fase 1: BackupCryptoManager + CloudSyncApi extension + BackupWorker
- **Objetivo**: Crear `BackupCryptoManager` singleton con cifrado AES-256-GCM (IV random 12B prepended al ciphertext). Extender `CloudSyncApi` con `uploadBackup(ByteArray): Boolean` y su stub. Crear `BackupWorker` (@HiltWorker) que consulta las 6 DAOs, serializa a JSON con kotlinx.serialization, cifra via BackupCryptoManager, y entrega al CloudSyncApi. Agregar queries `getAll()` suspend a los DAOs que no los tengan.
- **Validacion**:
  - [ ] BackupCryptoManager cifra/descifra correctamente (round-trip)
  - [ ] BackupWorker compila y se registra con Hilt
  - [ ] CloudSyncApi extendido sin romper ProfileSyncWorker existente
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: CloudBackupManager + UserPreferences + SyncManager extension
- **Objetivo**: Crear `CloudBackupManager` singleton Hilt que coordina backup manual (`backupNow()`) y automatico (PeriodicWorkRequest 24h). Agregar `autoBackupEnabled` (Boolean, default false) y `lastBackupTimestamp` (Long, default 0) a UserPreferences. Extender SyncManager con `scheduleBackup()` para backup manual y `schedulePeriodicBackup()`/`cancelPeriodicBackup()` para automatico.
- **Validacion**:
  - [ ] CloudBackupManager encola backup manual via SyncManager
  - [ ] Backup periodico se programa/cancela correctamente
  - [ ] DataStore persiste autoBackupEnabled y lastBackupTimestamp
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: CloudBackupScreen UI + ViewModel + NavGraph + Settings entry
- **Objetivo**: Crear `CloudBackupScreen` Compose con toggle backup automatico, display de fecha del ultimo respaldo, boton "Respaldar ahora" con feedback de estado (idle/en progreso/exito/error). Crear `CloudBackupViewModel` con UserPreferences + CloudBackupManager + WorkManager observation. Integrar ruta CLOUD_BACKUP en NavGraph y entrada en SettingsScreen.
- **Validacion**:
  - [ ] CloudBackupScreen renderiza correctamente
  - [ ] Toggle persiste en DataStore
  - [ ] Boton dispara backup y muestra feedback
  - [ ] Navegacion Settings → Copia de seguridad funciona
  - [ ] `./gradlew assembleDebug` exitoso

---

## Aprendizajes

**2026-08-23: StubCloudSyncApi.uploadBackup retorna true para no bloquear UX**
- **Contexto**: A diferencia de `uploadProfiles()` que retorna `false` (PRP-011 pattern), `uploadBackup()` retorna `true` para que el flujo completo funcione y `lastBackupTimestamp` se actualice — mejor UX durante desarrollo sin backend real.
- **Aplicar en**: Nuevos stubs de upload que participen en un flujo con feedback de estado visible al usuario.

**2026-08-23: Reutilizar passphrase de SQLCipher para backup cifrado**
- **Contexto**: La passphrase AES-256 del KeyStoreManager (PRP-023) sirve tanto para SQLCipher como para AES-256-GCM del backup. Misma clave, dos usos — simplifica key management sin reducir seguridad (ambos usan AES-256, la clave nunca sale del dispositivo).
- **Aplicar en**: Cualquier futuro feature que requiera cifrado e2e puede usar la misma passphrase si la amenaza es la misma (datos en reposo/transito, misma trust boundary).

---

## Anti-patrones

- No modificar Room schema ni agregar migraciones — los datos se leen con DAOs existentes
- No enviar datos en claro al CloudSyncApi — siempre cifrar antes
- No reusar IV entre operaciones de cifrado — cada backup genera IV nuevo
- No bloquear el main thread — todo via WorkManager + coroutines
- NO generar nuevos PRPs durante la ejecucion de este PRP

---

*PRP COMPLETADO. Build exitoso (`./gradlew assembleDebug` — 47 tasks, 0 errores). 2026-08-23.*
