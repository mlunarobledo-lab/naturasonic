# PRP-023: Pipeline de Autenticación Local y Cifrado de Datos de Usuario

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-23
> **Proyecto**: NaturaSonic

---

## Origen

> Planificación directa, sin brief previo. Las 7 fases del brief `@docs/BRIEF-naturasonic.md` están todas en estado `COMPLETADO`. Este PRP extiende el producto más allá del alcance original del brief.
> Las secciones heredadas (Directiva de Stack, Supuestos, Fuera de Alcance, Aprendizajes heredados) se derivan del contexto acumulado del proyecto, no de un brief activo.

---

## Objetivo

Quiero que todos los datos de salud auditiva almacenados localmente en NaturaSonic (audiogramas, métricas vocales, muestras de dosimetría, historial de alertas, transcripciones y perfiles de audio) estén cifrados en reposo mediante Android KeyStore, y que el acceso a la app se pueda proteger opcionalmente con huella dactilar, reconocimiento facial o PIN del dispositivo. Los datos nunca deben ser legibles si alguien accede al almacenamiento del teléfono sin autorización.

## Por Que

| Problema | Solución |
|----------|----------|
| La base de datos Room (`naturasonic.db`) se almacena en texto plano — cualquier persona con acceso físico al dispositivo (robo, pérdida, debug USB) puede extraer audiogramas, métricas vocales, dosimetría y transcripciones del usuario | Cifrado transparente de la base de datos con SQLCipher usando una clave maestra custodiada en Android KeyStore (hardware-backed cuando el dispositivo lo soporta) |
| Las preferencias sensibles (offsets de calibración, perfiles de ecualización personalizados basados en audiogramas médicos) viven en DataStore sin protección | Migración de datos sensibles a EncryptedSharedPreferences respaldadas por KeyStore, o cifrado selectivo de campos antes de persistirlos |
| No hay barrera de acceso a la app — cualquiera que desbloquee el teléfono ve inmediatamente los datos de salud auditiva del usuario | App lock opcional con BiometricPrompt (huella/cara) y fallback a credencial del dispositivo (PIN/patrón/contraseña) |
| Android Auto Backup sube `naturasonic.db` en claro a Google Drive — si la cuenta Google se compromete, los datos de salud quedan expuestos | Exclusión de la clave maestra de los backup rules + cifrado de la base de datos asegura que el backup en la nube contenga datos cifrados inutilizables sin el KeyStore del dispositivo original |

**Valor**: Necesito que NaturaSonic cumpla con las mejores prácticas de protección de datos de salud en el dispositivo. Al manejar datos sensibles de salud auditiva (audiogramas, patrones de voz, exposición a ruido), el cifrado local y la autenticación biométrica son requisitos esperados por los usuarios y por las políticas de Google Play para apps de salud.

## Que

### Criterios de éxito
- [ ] La base de datos Room está cifrada con SQLCipher — un archivo `naturasonic.db` extraído del dispositivo no es legible sin la clave
- [ ] La clave de cifrado de SQLCipher está custodiada en Android KeyStore — no hardcodeada ni almacenada en SharedPreferences
- [ ] La migración de la BD existente (v5, texto plano) a la BD cifrada (v6) es transparente y no pierde datos
- [ ] BiometricPrompt funciona con huella dactilar (Class 3), reconocimiento facial (Class 2) y fallback a credencial del dispositivo
- [ ] El app lock es opcional — el usuario lo activa/desactiva desde Settings → Seguridad
- [ ] Tras activar app lock, la app solicita autenticación al abrirse desde background después de un timeout configurable (1, 5, 15 minutos, o inmediato)
- [ ] La pantalla de Seguridad en Settings muestra el estado del cifrado, opciones de app lock y timeout
- [ ] Los backup rules excluyen la clave maestra cifrada pero incluyen la BD cifrada (portable entre dispositivos del mismo usuario vía backup, pero solo legible con el KeyStore original)
- [ ] `./gradlew assembleDebug` compila sin errores
- [ ] `./gradlew lint` sin warnings nuevos de seguridad

### Comportamiento esperado

El usuario instala la actualización y la primera vez que abre la app, la base de datos se migra silenciosamente de texto plano a cifrado — el proceso es invisible (splash screen normal). Desde ese momento, todos los datos nuevos y existentes están cifrados en reposo.

Si el usuario navega a Settings → Seguridad, ve:
1. **Estado del cifrado**: indicador que confirma que la BD está cifrada (con icono de candado verde)
2. **Bloqueo de app**: toggle para activar/desactivar la autenticación biométrica al abrir la app
3. **Método de autenticación**: muestra los métodos disponibles en el dispositivo (huella, cara, PIN)
4. **Timeout de bloqueo**: selector con opciones (Inmediato, 1 min, 5 min, 15 min) — cuánto tiempo puede la app estar en background antes de pedir autenticación de nuevo

Cuando el app lock está activo y el usuario vuelve a la app después del timeout, se muestra una pantalla de autenticación a pantalla completa (con el logo de NaturaSonic y BiometricPrompt). Si la autenticación falla 3 veces, se muestra un mensaje pidiendo usar la credencial del dispositivo. El pipeline de audio (servicio foreground) sigue operando en background sin interrupciones — el bloqueo es solo de la UI, no del servicio.

### Casos borde

- **Dispositivo sin biometría registrada**: el fallback es credencial del dispositivo (PIN/patrón/contraseña). Si no hay credencial configurada, el toggle de app lock se deshabilita con mensaje explicativo.
- **Migración de BD interrumpida** (crash/kill durante cifrado): la migración debe ser atómica — se crea la BD cifrada como archivo temporal, se copia toda la data, y solo al final se reemplaza el archivo original. Si el temporal existe al inicio, se retoma o se elimina.
- **App reinstalada** (KeyStore limpio): la BD cifrada existente ya no se puede descifrar. Detectar esta condición al inicio, notificar al usuario que los datos anteriores no son recuperables, y crear una BD nueva vacía. No crashear.
- **Android Auto Backup restaura BD cifrada en dispositivo nuevo**: la BD cifrada se restaura, pero el KeyStore es diferente. Mismo manejo que reinstalación: datos anteriores irrecuperables, BD nueva.
- **Dispositivo rooteado**: el cifrado sigue protegiendo los datos a nivel de archivo. El KeyStore puede no ser hardware-backed en algunos dispositivos rooteados — no es responsabilidad de la app garantizar seguridad en dispositivos comprometidos, pero el cifrado sigue siendo una barrera.
- **Cloud Sync existente** (StubCloudSyncApi): el sync sube AudioProfiles ya descifrados en memoria — el cifrado es transparente para la capa de sync. Cuando se implemente un backend real, la data viaja descifrada desde Room (vía DAO) y la protección en tránsito será responsabilidad del transport layer (TLS).
- **Pipeline de audio durante lock screen**: AudioService (foreground service) opera independientemente. El app lock solo afecta la UI Compose — si el usuario tiene la app bloqueada, el audio sigue procesándose, las alertas siguen detectándose y las notificaciones siguen emitiéndose.
- **Export PDF con BD cifrada**: `WellnessReportGenerator` lee datos vía DAOs que acceden a la BD ya descifrada en memoria — el cifrado es transparente.

---

## Contexto

### Documentación externa
- Android KeyStore System — almacenamiento de claves criptográficas respaldado por hardware (TEE/StrongBox)
- `androidx.security:security-crypto` — EncryptedSharedPreferences y EncryptedFile respaldados por KeyStore con AES256-GCM
- `net.zetetic:android-database-sqlcipher` — cifrado transparente AES-256 para SQLite (compatible con Room)
- `androidx.biometric:biometric` — BiometricPrompt unificado (Class 2/3 + fallback a credencial del dispositivo)
- `androidx.sqlite:sqlite` — SupportSQLiteOpenHelper.Factory requerido para integrar SQLCipher con Room

### Código existente a consultar
- `AppDatabase.kt` — Room database v5 con 6 entities y 4 migrations (v1→v5). La migración v5→v6 será la de cifrado.
- `AppModule.kt` — Hilt DI module donde se construye la instancia de Room. Aquí se inyecta el `SupportSQLiteOpenHelper.Factory` de SQLCipher.
- `UserPreferences.kt` — DataStore con 19 keys de preferencias. Las preferencias de seguridad (lockEnabled, lockTimeout) se agregan aquí.
- `AudioProfileRepository.kt` — patrón repository con dirty tracking. El cifrado es transparente para esta capa.
- `backup_rules.xml` / `backup_rules_api31.xml` — reglas de Auto Backup que incluyen `naturasonic.db`. Deben actualizarse para excluir el archivo de clave maestra.
- `NavGraph.kt` — 16 rutas actuales. Se agrega `SECURITY` como ruta nueva.
- `SettingsScreen.kt` — patrón de sección + card de navegación. Se agrega sección "Seguridad y privacidad" con card de navegación a SecurityScreen.

### Gotchas conocidas
- **SQLCipher + Room**: Room necesita un `SupportSQLiteOpenHelper.Factory` customizado que SQLCipher provee (`SupportFactory`). La factory recibe la passphrase como `ByteArray` — nunca como `String` para evitar que quede en el string pool de la JVM.
- **Migración texto plano → cifrado**: SQLCipher no puede migrar in-place. Hay que: (1) abrir la BD sin cifrar, (2) ATTACH la nueva BD cifrada, (3) `SELECT sqlcipher_export('encrypted')`, (4) cerrar ambas, (5) reemplazar el archivo. Esto NO es una Migration de Room — es una operación pre-Room que ocurre antes de `Room.databaseBuilder()`.
- **Android KeyStore + backup**: las claves en KeyStore NO se respaldan con Auto Backup (por diseño de seguridad). Si la BD cifrada se restaura en otro dispositivo, la clave no existe — la BD es irrecuperable. Esto es el comportamiento correcto para datos de salud locales.
- **BiometricPrompt threading**: `BiometricPrompt.AuthenticationCallback` se ejecuta en el main thread. No hacer operaciones pesadas en `onAuthenticationSucceeded()`.
- **`setDeviceCredentialAllowed`** está deprecated desde API 30. Usar `BiometricPrompt.PromptInfo.Builder().setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)` en su lugar.
- **Room schemaVersion**: la versión 6 no agrega tablas ni columnas — la migración es un no-op dentro de Room (el schema no cambia). La operación real de cifrado ocurre fuera del sistema de migrations de Room, antes de construir la instancia.

### Modelo de datos (cambios)

No hay cambios en el schema de Room (las 6 entities permanecen idénticas). Los cambios son:

1. **`AppModule.kt`**: el `Room.databaseBuilder` recibe un `SupportFactory(passphrase)` de SQLCipher en vez del helper default.
2. **`UserPreferences.kt`**: 2 keys nuevas — `security_lock_enabled: Boolean`, `security_lock_timeout: Int` (minutos: 0, 1, 5, 15).
3. **Nuevo archivo**: clave maestra cifrada almacenada vía `EncryptedSharedPreferences` (key = "db_passphrase", value = passphrase AES-256 generada una vez).
4. **`backup_rules*.xml`**: excluir `encrypted_prefs.xml` (o el nombre que use EncryptedSharedPreferences) del backup.

---

## Directiva de Stack heredada

> Derivada del proyecto existente NaturaSonic (brief con compatibilidad REPLACE).

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Ninguno del stack web Praxis

### ADD (extensiones para este PRP)
- **`androidx.security:security-crypto:1.1.0-alpha06`** — EncryptedSharedPreferences para custodiar la passphrase de SQLCipher, respaldada por Android KeyStore (AES256-GCM, MasterKey con KeyGenParameterSpec)
- **`net.zetetic:android-database-sqlcipher:4.5.6`** — cifrado transparente AES-256 para SQLite, compatible con Room vía `SupportFactory`
- **`androidx.sqlite:sqlite:2.4.0`** — `SupportSQLiteOpenHelper.Factory` requerido por SQLCipher para integrarse con Room
- **`androidx.biometric:biometric:1.2.0-alpha05`** — BiometricPrompt unificado con soporte para Class 2 (cara) + Class 3 (huella) + fallback a device credential

### REPLACE
- Room `openHelper` default → `SupportFactory` de SQLCipher (cambio en `AppModule.kt`)

### REMOVE
- Nada

### CONFIG
- `backup_rules.xml` — excluir `<exclude domain="sharedpref" path="encrypted_prefs.xml" />` para que la clave maestra no viaje a Google Drive
- `backup_rules_api31.xml` — misma exclusión en formato `<data-extraction-rules>`
- `proguard-rules.pro` — reglas para SQLCipher (`-keep class net.sqlcipher.** { *; }`) si no están incluidas automáticamente
- `libs.versions.toml` — agregar versiones de `security-crypto`, `sqlcipher`, `sqlite`, `biometric`
- `app/build.gradle.kts` — agregar las 4 dependencias nuevas

### Refinamientos a la Directiva durante este PRP
- La passphrase de SQLCipher se genera una sola vez con `SecureRandom` (32 bytes, AES-256) y se almacena vía `EncryptedSharedPreferences` — nunca hardcodeada, nunca en BuildConfig, nunca en DataStore regular.
- La versión de la BD Room permanece en v5 (o sube a v6 con MIGRATION no-op) porque el cifrado ocurre a nivel de archivo SQLite, no a nivel de schema.

---

## Supuestos heredados

> Derivados del contexto acumulado del proyecto.

- [ ] El dispositivo Android del usuario tiene Android 10+ (API 29+) — KeyStore con TEE disponible desde API 23, pero la app ya requiere API 29+
- [ ] Room database v5 (`naturasonic.db`) existe con las 6 entities y las 4 migrations actuales
- [ ] Android KeyStore está operativo en el dispositivo (disponible en >99% de dispositivos con API 23+)
- [ ] El dispositivo tiene al menos una credencial registrada (PIN/patrón/contraseña) para que el app lock funcione — si no, el toggle se deshabilita

### Supuestos adicionales (específicos de este PRP)
- [ ] SQLCipher 4.5.x es compatible con Room 2.6.1 vía `SupportFactory` sin modificaciones adicionales
- [ ] La migración de texto plano a cifrado completa en < 5 segundos para bases de datos típicas (< 10 MB de datos históricos acumulados)
- [ ] `EncryptedSharedPreferences` con `MasterKey.DEFAULT_MASTER_KEY_ALIAS` usa KeyStore hardware-backed cuando está disponible

---

## Fuera de Alcance heredado

> Derivado del brief original + contexto del proyecto.

- Streaming Auracast / broadcast LE Audio
- Versión iOS
- Backend en la nube / Supabase como servicio de auth
- Audiograma clínico calibrado (solo PSAP)
- Cifrado end-to-end para cloud sync (la data viaja descifrada desde Room; TLS es responsabilidad del transport layer futuro)
- Integración con audífonos clínicos FDA

### Fuera de Alcance adicional (específico de este PRP)
- **Auth remota** (login con cuenta Google/email/contraseña) — esto es auth LOCAL, no cloud auth
- **Cifrado de modelos ML** (GGML, TFLite) — son assets públicos descargables, no datos del usuario
- **Cifrado de archivos de audio** (no se graban por diseño — privacidad)
- **Key escrow o recuperación de clave** — si el KeyStore se pierde, los datos son irrecuperables (diseño intencional para datos de salud)
- **Cifrado selectivo de campos** en Room — se cifra toda la BD de forma transparente con SQLCipher, no campos individuales
- **DataStore cifrado** — las preferencias en DataStore no contienen datos de salud (son configuraciones operacionales: modo actual, volumen, thresholds). Solo la passphrase de SQLCipher va a EncryptedSharedPreferences separadas.

---

## Aprendizajes heredados de fases previas

> Aprendizajes transversales de `CLAUDE.md` que aplican a este trabajo.

**2026-08-03: El proyecto NaturaSonic es Android nativo (no web)**
- Los comandos de validación son `./gradlew assembleDebug` (build), `./gradlew lint` (lint). No aplican npm/tsc.
- Aplicar en: todos los criterios de validación de este PRP.

**2026-08-13: Double-buffer copy-modify-swap como patrón canónico para parámetros DSP thread-safe**
- La capa de cifrado es transparente para el pipeline de audio C++ — no interactúa con EqSnapshot ni con el audio thread. No hay impacto en latencia DSP.
- Aplicar en: confirmar que el cifrado de Room no introduce contención en el audio thread (Room opera en Dispatchers.IO, el audio thread es nativo C++).

**Room migrations deben ser explícitas (Migration objects), no destructivas**
- Las migrations v1→v2, v2→v3, v3→v4, v4→v5 siguen el patrón `object : Migration(from, to)` con SQL explícito.
- Aplicar en: la migration v5→v6 (si se usa) debe seguir el mismo patrón. Nota: la migración real de cifrado ocurre pre-Room, no dentro de Room migrations.

**Patrón singleton Hilt consistente en todo el proyecto**
- Todos los managers (`EcoModeManager`, `BatteryMonitor`, `HeadTrackingManager`, etc.) son `@Singleton @Inject constructor`.
- Aplicar en: `SecurityManager` sigue el mismo patrón.

---

## Plan de implementación

> IMPORTANTE: solo definir FASES aquí. Las subtareas se generan al ENTRAR
> a cada fase siguiendo el bucle-agéntico (mapear contexto → generar
> subtareas → ejecutar).

### Fase 1: KeyStore + EncryptedSharedPreferences + Generación de Clave Maestra
- **Objetivo**: Establecer la infraestructura criptográfica base. Agregar las dependencias (`security-crypto`, `sqlcipher`, `sqlite`, `biometric`) al proyecto. Crear un `KeyStoreManager` singleton Hilt que genere (una sola vez) una passphrase AES-256 con `SecureRandom`, la almacene en `EncryptedSharedPreferences` respaldadas por `MasterKey` de Android KeyStore, y la exponga de forma segura como `ByteArray` para consumo interno. Actualizar `libs.versions.toml`, `build.gradle.kts`, y verificar que compila.
- **Validación**:
  - [ ] Las 4 dependencias nuevas resuelven correctamente
  - [ ] `KeyStoreManager` genera y recupera la passphrase consistentemente
  - [ ] La passphrase NO aparece en logs, ni en DataStore, ni en SharedPreferences regulares
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 2: Cifrado de Room DB con SQLCipher + Migración Transparente
- **Objetivo**: Integrar SQLCipher con Room mediante `SupportFactory` en `AppModule.kt`. Implementar la rutina de migración pre-Room que convierte la BD existente (texto plano v5) a cifrada: abrir sin cifrar → ATTACH cifrada → `sqlcipher_export` → reemplazar archivo. Manejar los casos borde (migración interrumpida, BD ya cifrada, KeyStore perdido/reinstalación). Actualizar backup rules para excluir `encrypted_prefs.xml`.
- **Validación**:
  - [ ] BD existente se migra de texto plano a cifrada sin pérdida de datos
  - [ ] Abrir el archivo `naturasonic.db` con un visor SQLite externo falla (datos ilegibles)
  - [ ] Los DAOs siguen operando normalmente tras la migración (insert, query, delete)
  - [ ] Reinstalación limpia (sin datos previos) crea BD cifrada desde cero
  - [ ] `backup_rules*.xml` excluyen `encrypted_prefs.xml`
  - [ ] `./gradlew assembleDebug` exitoso

### Fase 3: App Lock con BiometricPrompt + Autenticación Local
- **Objetivo**: Implementar el flujo de autenticación local. Crear un `SecurityManager` singleton Hilt que gestione el estado de bloqueo (locked/unlocked), el timeout de auto-lock, y la verificación de capacidades biométricas del dispositivo. Crear `LockScreen` Composable a pantalla completa con BiometricPrompt (BIOMETRIC_WEAK | DEVICE_CREDENTIAL). Integrar el check de bloqueo en `MainActivity` (o en el NavHost) para interceptar el acceso a la app tras el timeout. Agregar las preferencias `security_lock_enabled` y `security_lock_timeout` a `UserPreferences`. El pipeline de audio (`AudioService` foreground) NO se interrumpe.
- **Validación**:
  - [ ] Con app lock activado, al volver del background después del timeout, se muestra LockScreen
  - [ ] BiometricPrompt muestra opciones disponibles del dispositivo (huella/cara/PIN)
  - [ ] Autenticación exitosa desbloquea la app y muestra la última pantalla
  - [ ] Autenticación fallida 3 veces muestra mensaje de usar credencial del dispositivo
  - [ ] Con app lock desactivado, no se solicita autenticación
  - [ ] AudioService sigue operando (notificaciones de alertas, audio processing) mientras la UI está bloqueada
  - [ ] Sin credencial configurada en el dispositivo, el toggle de app lock se deshabilita con explicación

### Fase 4: SecurityScreen UI + Integración en Settings + Validación Final
- **Objetivo**: Crear `SecurityScreen` Compose accesible desde Settings → "Seguridad y privacidad". Mostrar: estado del cifrado de la BD (icono candado + texto), toggle de app lock, selector de timeout, información sobre métodos biométricos disponibles, y disclaimer sobre irrecuperabilidad de datos si se pierde el KeyStore. Integrar la ruta en `NavGraph.kt` y la sección en `SettingsScreen.kt` siguiendo el patrón visual existente (SectionHeader + Card de navegación). Validación end-to-end completa.
- **Validación**:
  - [ ] SecurityScreen muestra estado correcto del cifrado
  - [ ] Toggle de app lock funciona y persiste en DataStore
  - [ ] Selector de timeout cambia comportamiento del auto-lock
  - [ ] Navegación Settings → Seguridad → back funciona correctamente
  - [ ] `./gradlew assembleDebug` exitoso
  - [ ] `./gradlew lint` sin warnings nuevos
  - [ ] Flujo completo end-to-end: activar lock → background → foreground → biometric prompt → unlock → usar app normalmente

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

**2026-08-23: SQLCipher artifact renombrado — net.zetetic cambió coordenadas Maven**
- **Error**: `net.zetetic:android-database-sqlcipher:4.5.6` no resuelve en Maven. El artefacto se renombró.
- **Fix**: Usar `net.zetetic:sqlcipher-android:4.6.1`. El paquete Java cambió de `net.sqlcipher.database` a `net.zetetic.database.sqlcipher`, y `SupportFactory` pasó a llamarse `SupportOpenHelperFactory`.
- **Aplicar en**: Cualquier proyecto Android que integre SQLCipher con Room — verificar coordenadas Maven actualizadas.

**2026-08-23: BiometricPrompt requiere FragmentActivity, no ComponentActivity**
- **Error**: `BiometricPrompt(activity, executor, callback)` requiere `FragmentActivity`. `MainActivity` extendía `ComponentActivity`, que es su superclase directa pero no cumple el contrato.
- **Fix**: Cambiar `MainActivity : ComponentActivity()` a `MainActivity : FragmentActivity()`. `FragmentActivity` hereda `ComponentActivity`, por lo que `enableEdgeToEdge()`, `setContent {}`, y toda la API Compose siguen funcionando sin cambios.
- **Aplicar en**: Cualquier futuro feature que use APIs que requieran `FragmentActivity` (BiometricPrompt, Fragment-based dialogs, etc.).

**2026-08-23: Cifrado SQLCipher es a nivel de archivo, no Migration de Room**
- **Error conceptual inicial**: considerar la migración plaintext→encrypted como una Room Migration (v5→v6).
- **Fix**: La migración ocurre ANTES de `Room.databaseBuilder()` — es una operación de archivo SQLite (ATTACH + sqlcipher_export + rename atómico). Room nunca ve la BD en texto plano. La versión de Room se mantiene en v5 (el schema no cambió).
- **Aplicar en**: Cualquier escenario de cifrado/descifrado de BD Room. El cifrado NO es una migration de schema.

---

## Anti-patrones

- No hardcodear la passphrase de SQLCipher en BuildConfig, strings.xml, ni código fuente
- No almacenar la passphrase en SharedPreferences regulares ni en DataStore — solo en EncryptedSharedPreferences
- No convertir la passphrase de `ByteArray` a `String` — evitar que quede en el string pool de la JVM
- No bloquear el servicio de audio (AudioService) cuando la UI está bloqueada — el lock es solo de la interfaz
- No asumir que el dispositivo tiene biometría — siempre ofrecer fallback a credencial del dispositivo
- No hacer la migración de cifrado dentro del sistema de Migration de Room — es una operación pre-Room a nivel de archivo SQLite
- No crear patrones nuevos si los existentes funcionan
- No ignorar errores de compilación Kotlin
- No commitear secrets ni passphrases
- NO generar nuevos PRPs durante la ejecución de este PRP

---

*PRP COMPLETADO — 2026-08-23. Build exitoso (`./gradlew assembleDebug`). 4 fases implementadas.*
