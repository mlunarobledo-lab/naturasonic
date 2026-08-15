# Brief: NaturaSonic — App PSAP/Bienestar de Audio para Android

> Fecha: 2026-08-02

## TL;DR

Quiero construir NaturaSonic, una app Android nativa (Kotlin + Jetpack Compose) que funciona como PSAP (Personal Sound Amplification Product) y plataforma de bienestar auditivo. Usa auriculares Bluetooth LE Audio / ASHA como extensión del teléfono para amplificar sonidos, transcribir conversaciones offline, detectar alertas sonoras y mejorar audio de entretenimiento — todo con herramientas open-source (Oboe, Vosk, whisper.cpp, openMHA, YAMNet/TF Lite). Modelo freemium en Google Play Store con protección auditiva integrada (límite 85 dB, bloqueo a 70 dB tras 1 hora), diseño UltraView accesible y cumplimiento legal PSAP.

## Mi Vision

Quiero democratizar el acceso a la amplificación de sonido personal. Hoy, los audífonos cuestan miles de dólares y las apps PSAP existentes son cerradas, limitadas o requieren conexión a internet. NaturaSonic cambia eso: una app gratuita que convierte cualquier par de auriculares Bluetooth LE Audio compatibles en un sistema de amplificación inteligente, con transcripción en tiempo real y detección de sonidos de alerta — todo procesado localmente en el teléfono, sin enviar audio a la nube.

He decidido que la app sea nativa en Kotlin con Jetpack Compose porque necesito acceso directo a las APIs de audio de Android (Oboe/AAudio para baja latencia), al stack Bluetooth LE Audio y ASHA del sistema, y a las APIs de AudioEffect (AcousticEchoCanceler, NoiseSuppressor, Equalizer). Una solución cross-platform no me daría el nivel de control que necesito sobre el pipeline de audio en tiempo real.

El modelo de negocio es freemium: la versión gratuita cubre amplificación básica, cancelación de ruido y detección de alertas. La versión premium desbloquea el ecualizador personalizado avanzado, transcripción continua, modo micrófono remoto para eventos y perfiles de audio por entorno. He investigado que Google Play Billing Library 8.0 es obligatoria desde agosto 2026 para nuevas apps.

La seguridad auditiva es un pilar no negociable: el volumen nunca excederá 85 dB SPL, y después de una hora de escucha continua la app bloqueará automáticamente el volumen a 70 dB con un aviso de precaución. Antes de descargar, la app recomendará verificar la compatibilidad del hardware Bluetooth del dispositivo.

## Contexto e Investigacion

He investigado extensivamente el ecosistema de audio en Android y las herramientas open-source disponibles:

**Protocolos de Conectividad**: Android soporta dos protocolos para streaming de audio a auriculares/audífonos: ASHA (Audio Streaming for Hearing Aids, de Google, desde Android 10) y Bluetooth LE Audio (estándar Bluetooth SIG, con soporte nativo creciente). Android 15+ soporta ambos en paralelo. Auracast (broadcast de LE Audio) queda pendiente para una actualización futura — requiere Android 16 y hardware compatible que aún tiene baja penetración.

**Pipeline de Audio (Oboe + openMHA)**: Google Oboe es la librería C++ estándar para audio de baja latencia en Android. Usa AAudio en API 27+ y OpenSL ES como fallback. Para el procesamiento de señales tipo PSAP (amplificación, filtrado, compresión), openMHA (Open Master Hearing Aid) es la referencia open-source — desarrollada por HörTech gGmbH, proporciona algoritmos de procesamiento de audífonos en tiempo real. La integración se hará vía JNI (C++/Kotlin bridge).

**Cancelación de Eco Acústico (AEC)**: Android provee `AcousticEchoCanceler` y `NoiseSuppressor` como AudioEffects nativos del sistema. Están disponibles en la mayoría de dispositivos desde API 16. Los uso como primera capa; para escenarios donde el AEC del sistema sea insuficiente, puedo complementar con el módulo de AEC de openMHA o con WebRTC AEC3 (open-source de Google).

**Transcripción Offline**: Investigué dos opciones complementarias. Vosk es un toolkit de reconocimiento de voz offline con modelos de ~50 MB, soporte para 20+ idiomas, y binding Java/Kotlin para Android — ideal para transcripción continua en tiempo real con bajo consumo. whisper.cpp es el port C++ de OpenAI Whisper con integración Android vía JNI y Jetpack Compose demostrada — superior en precisión pero más pesado en recursos. He decidido usar Vosk como motor principal (ligero, streaming) y whisper.cpp como opción premium para transcripciones de alta fidelidad.

**Detección de Sonidos de Alerta**: YAMNet sobre TensorFlow Lite corre 100% on-device y clasifica 521 eventos de audio (sirenas, timbres, llanto de bebé, alarmas de humo, bocinas). La detección nunca envía audio fuera del dispositivo — privacidad total.

**Ecualizador**: Android provee la clase `Equalizer` como AudioEffect nativo con bandas configurables. Lo complemento con UI custom en Jetpack Compose (sliders por banda de frecuencia) y presets para diferentes perfiles de amplificación (conversación, música, exterior).

**Regulación PSAP**: Después de investigar la posición de la FDA, confirmo que los PSAPs NO están regulados como dispositivos médicos siempre que el marketing NO afirme tratar pérdida auditiva. NaturaSonic se posiciona como producto de amplificación personal para personas con audición normal que necesitan un boost en situaciones específicas. Incluiré disclaimers legales claros y labeling que cumplan con la guía FDA de 2022 para PSAPs.

**Modelo Freemium y Play Store**: Google Play Billing Library 8.0 es obligatoria para nuevas apps desde agosto 2026. La comisión es 15% para suscripciones después del primer año (30% el primero). Data Safety section es obligatoria en Play Console. Target API level debe ser 35+ para 2026.

## Directiva de Stack Tecnico

> **Esta es la directiva inicial.** El PRP, al planearse, puede refinarla con la realidad actual del codebase. Los ajustes acumulados se documentan en el campo `Ajustes a la Directiva de Stack` de cada fase del `## Alcance por Fases`. Al cerrar el PRP, los ajustes se propagan aqui automaticamente. Esta directiva es un **starting point evolutivo**, no un contrato fijo.

### Clasificacion
- **Tipo**: mobile-android-native
- **Plataforma objetivo**: Android 10+ (API 29+), optimizado para dispositivos con Bluetooth LE Audio (Android 13+/API 33+ para LE Audio nativo)
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Ninguno del stack web Praxis
- Opcional futuro: Supabase como backend para sync de perfiles entre dispositivos (via `supabase-kt`)

### ADD
- **Kotlin 2.0+** con Gradle Kotlin DSL — lenguaje principal
- **Jetpack Compose** + **Material 3** — UI declarativa moderna
- **Oboe 1.9+** (C++) vía JNI — pipeline de audio de baja latencia (AAudio en API 27+, OpenSL ES fallback)
- **openMHA** (C++) vía JNI — algoritmos PSAP: amplificación, compresión dinámica, filtrado por bandas
- **Vosk 0.3.45+** — transcripción offline principal (modelos ~50 MB, streaming, 20+ idiomas)
- **whisper.cpp** vía JNI — transcripción premium de alta fidelidad (on-device, modelos ~150-500 MB)
- **TensorFlow Lite 2.16+** + **YAMNet** — detección de eventos sonoros on-device (521 clases)
- **Hilt** — inyección de dependencias
- **Room** — base de datos local (perfiles de audio, historial de transcripciones)
- **DataStore** — preferencias de usuario (modo oscuro, configuración de ecualizador)
- **Navigation Compose** — navegación entre pantallas
- **ViewModel + StateFlow** — gestión de estado reactivo
- **Google Play Billing Library 8.0** — suscripciones freemium/premium (vinculadas a cuenta Google, portables entre dispositivos)
- **Android Auto Backup** — respaldo automático de Room DB + DataStore a Google Drive (perfiles, configuración, historial). Al cambiar de móvil, los datos se restauran en el nuevo dispositivo al instalar la app
- **Accompanist Permissions** — manejo de permisos en runtime (micrófono, Bluetooth)
- **Coil** — carga de imágenes
- **kotlinx.serialization** — serialización de datos
- **LeakCanary** (debug) — detección de memory leaks en pipeline de audio
- **Baseline Profiles** — optimización de performance en cold start

### REPLACE
- Next.js / React / TypeScript → **Kotlin + Jetpack Compose** (UI nativa Android)
- Tailwind CSS → **Material 3** (design system nativo)
- Supabase Auth → **sin auth en v1** (datos locales; auth opcional en futuro para sync)
- Vercel AI SDK → **TensorFlow Lite + modelos on-device** (IA local, sin cloud)
- Zustand → **ViewModel + StateFlow** (gestión de estado Android)
- Playwright → **Android Instrumented Tests + UI Automator** (testing nativo)

### REMOVE
- `src/`, `package.json`, `tsconfig.json`, `next.config.ts`, `tailwind.config.ts`, `postcss.config.js`
- `.mcp.json` con entries de `next-devtools` / `playwright`
- Todo el scaffold web Praxis

### CONFIG
- `app/build.gradle.kts` — minSdk 29, targetSdk 35, compileSdk 36; NDK para Oboe/openMHA/whisper.cpp vía CMake
- `settings.gradle.kts` — dependencyResolutionManagement con mavenCentral + Google
- `AndroidManifest.xml` — permisos: `RECORD_AUDIO`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`
- `proguard-rules.pro` — reglas para TensorFlow Lite, Room, Hilt, Vosk
- `CMakeLists.txt` — build de Oboe, openMHA, whisper.cpp como librerías nativas
- `gradle.properties` — `android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx4096m` (build con NDK pesado)
- Signing config con Play App Signing (Google gestiona keystore de release)
- `AndroidManifest.xml` — `android:allowBackup="true"` + `android:fullBackupContent="@xml/backup_rules"` para Android Auto Backup (Room DB + DataStore incluidos, modelos ML excluidos)

### Archivos Praxis a eliminar
- `src/`, `package.json`, `tsconfig.json`, `next.config.ts`, `tailwind.config.ts`, `postcss.config.js`, `postcss.config.mjs`

### Archivos nuevos a crear
- `app/src/main/java/com/naturasonic/` — paquete raíz
- `app/src/main/java/com/naturasonic/ui/` — pantallas Compose (Home, Equalizer, Transcription, RemoteMic, Specialists, Settings)
- `app/src/main/java/com/naturasonic/audio/` — pipeline de audio (OboeEngine, PsapProcessor, EchoCanceler, VolumeLimiter)
- `app/src/main/java/com/naturasonic/bluetooth/` — gestión BLE Audio, ASHA
- `app/src/main/java/com/naturasonic/transcription/` — Vosk + whisper.cpp wrappers
- `app/src/main/java/com/naturasonic/detection/` — YAMNet sound event detector
- `app/src/main/java/com/naturasonic/data/` — Room DB + DataStore + repositorios
- `app/src/main/java/com/naturasonic/di/` — módulos Hilt
- `app/src/main/java/com/naturasonic/health/` — nota de salud + intent a Google Maps para especialistas
- `app/src/main/java/com/naturasonic/billing/` — Play Billing integration
- `app/src/main/cpp/` — código nativo (Oboe pipeline, openMHA bridge, whisper.cpp bridge)
- `app/src/main/res/` — recursos Android (strings, drawables, themes)
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`, `build.gradle.kts` (project), `settings.gradle.kts`
- `app/src/main/cpp/CMakeLists.txt`

### IDE / Toolchain externo requerido
- **Android Studio Ladybug** (2024.2+) o más reciente — IDE principal
- **JDK 17+** — requerido por Gradle y Android Studio
- **Android NDK** — compilación de Oboe, openMHA, whisper.cpp (C++17)
- **CMake 3.22+** — build system para código nativo
- **Google Play Developer Account** ($25 único) — publicación en Play Store
- **Keystore de upload** (generar con `keytool`) — firma de releases

## Alcance por Fases

> Esta seccion es el **diario vivo del proyecto** y la fuente de las fases del PRP. El brief siempre se convierte en **un solo PRP** que toma estas fases como su `## Plan de implementacion`. Cada fase mantiene los campos estructurados que `prp` y `bucle-agentico` consultan y actualizan automaticamente.

### Fase 1: Scaffold Android + Pipeline de Audio con Oboe
- **Estado**: COMPLETADO
- **Objetivo high-level**: Crear el proyecto Android nativo con Kotlin + Jetpack Compose, configurar el build con NDK/CMake, integrar Oboe como motor de audio de baja latencia, y establecer el pipeline básico micrófono → procesamiento → auriculares con latencia < 20ms.
- **Depende de**: —
- **Aprendizajes para fases siguientes**: El scaffold web Praxis se eliminó completamente (Fase 0 implícita REPLACE). Oboe se integra vía FetchContent en CMake, no como subdirectorio prebuilt. El pipeline usa callbacks de output stream que leen del input stream, patrón más estable que duplex nativo.
- **Ajustes a la Directiva de Stack**: —
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

### Fase 2: Conectividad Bluetooth (LE Audio + ASHA)
- **Estado**: COMPLETADO
- **Objetivo high-level**: Implementar descubrimiento, emparejamiento y streaming de audio via Bluetooth LE Audio y ASHA protocol. Incluir pantalla de verificación de compatibilidad de hardware antes del primer uso.
- **Depende de**: Fase 1
- **Aprendizajes para fases siguientes**: La detección de LE Audio requiere API 33+ (`BluetoothAdapter.isLeAudioSupported`). ASHA se detecta vía `BluetoothProfile.HEARING_AID`. Ambos se verifican en onboarding para informar al usuario antes de usar la app. PRP-012 (2026-08-15): BluetoothAudioManager robustecido con `connectionState` StateFlow reactivo (Connected/Disconnected/BluetoothOff/NoDevice), monitoreo de ACL + ACTION_STATE_CHANGED, y output mute atómico en C++ (`std::atomic<bool>` en `onAudioReady`) que silencia la salida en < 1 frame ante desconexión BT. AudioService observa el StateFlow con debounce 200ms y ejecuta mute/unmute sin intervención del usuario. WhisperBridge y YAMNet siguen operando con output muteado. BroadcastReceiver en API 33+ requiere flag `RECEIVER_NOT_EXPORTED`.
- **Ajustes a la Directiva de Stack**: —
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

### Fase 3: Motor PSAP y Procesamiento de Señal
- **Estado**: COMPLETADO
- **Objetivo high-level**: Integrar openMHA vía JNI para amplificación inteligente, compresión dinámica y filtrado por bandas de frecuencia. Implementar AcousticEchoCanceler + NoiseSuppressor del sistema. Crear ecualizador personalizado con UI Compose (5-10 bandas, presets por entorno). Implementar limitador de volumen hard a 85 dB SPL y bloqueo automático a 70 dB tras 1 hora continua con aviso de precaución.
- **Depende de**: Fase 1, Fase 2
- **Aprendizajes para fases siguientes**: Se implementaron algoritmos PSAP directamente en C++ (biquad peaking EQ, noise gate, volume limiter) en lugar de integrar openMHA completo. openMHA es una plataforma de investigación con build system complejo (autotools) no optimizado para Android — los módulos esenciales se reimplementaron directamente sobre Oboe con resultado equivalente y build más limpio. PRP-013 (2026-08-15): Audiogram Calibration — test audiométrico local con ToneGenerator (AudioTrack MODE_STATIC, 6 frecuencias × 2 oídos), fórmula Half-Gain (gain = threshold × 0.5, clamped [0,12] dB), interpolación 6 freqs → 10 bandas EQ (extrapolación plana para 125/10k/12k, lineal para 6kHz). Room v2→v3 migration con entity AudiogramRecord. Pantalla step-by-step en Compose con disclaimer PSAP obligatorio, gráfico de audiograma Canvas y botón "Aplicar a mi ecualizador" que llama a nativeApplyProfile existente. Acceso desde Settings → "Calibrar mi audición". PRP-014 (2026-08-15): Noise Gate Adaptativo con 3 modos (OFF/VOICE_FOCUS/AGGRESSIVE). El `bool noiseSuppression` en EqSnapshot se reemplazó por `int noiseGateMode`. `applyAdaptiveNoiseGate` usa estimación de piso de ruido por EMA + detección de voz por ratio RMS (voiceRatio 4.0/2.5) + attack/release suavizado. AncControlScreen en Compose con selector de modos persistido en DataStore. La API `applyProfile` cambió de `bool` a `int` en las 4 capas (C++ → JNI → Kotlin → callers); `setNoiseSuppressionEnabled(bool)` se mantiene como convenience. Room entity AudioProfile NO se modificó — mapeo bool→int en Kotlin.
- **Ajustes a la Directiva de Stack**: openMHA pasa de dependencia directa a referencia de diseño. Los algoritmos PSAP viven en `audio_processor.cpp` y `volume_limiter.cpp` como implementación propia inspirada en openMHA.
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

### Fase 4: Transcripción Offline y Detección de Sonidos de Alerta
- **Estado**: COMPLETADO
- **Objetivo high-level**: Integrar Vosk como motor de transcripción offline principal (streaming, ~50 MB modelo). Agregar whisper.cpp vía JNI como opción premium de alta fidelidad. Implementar botón de autorización para activar transcripción. UI de subtítulos en tiempo real con fuentes personalizables (blanco, negro, amarillo) adaptadas al tamaño de pantalla. Integrar YAMNet sobre TensorFlow Lite para detección de sonidos de alerta (sirenas, timbres, alarmas) con notificaciones visuales/vibración.
- **Depende de**: Fase 1, Fase 3
- **Aprendizajes para fases siguientes**: Los modelos ML (Vosk, YAMNet) se descargan on-demand al primer uso, no van en el APK. El modelo YAMNet TFLite espera input de 15600 muestras (0.975s a 16kHz) — hay que resamplear desde 48kHz del pipeline Oboe. whisper.cpp integrado completamente en PRP-002 (2026-08-10): FetchContent v1.6.2, JNI bridge, descarga de modelos GGML (tiny/base), resampling 48→16kHz, UI con selector de motor y gating premium. PRP-003 (2026-08-10): bridge unificado — audio fluye directamente de Oboe a whisper en C++ sin roundtrip JNI, resampling nativo, thread dedicado de procesamiento. Patrón replicable a cualquier consumidor pesado de audio (YAMNet podría migrarse igual). PRP-004 (2026-08-10): GgmlModelManager con extracción desde assets APK vía I/O buffered, ModelState sealed (Uninitialized/Copying/Ready/Error) expuesto via StateFlow a Compose. noCompress("bin") en AAPT para assets grandes.
- **Ajustes a la Directiva de Stack**: —
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

### Fase 5: Micrófono Remoto y Modos de Uso
- **Estado**: COMPLETADO
- **Objetivo high-level**: Implementar modo micrófono remoto (el teléfono captura audio a distancia y lo envía a los auriculares del usuario, ideal para conferencias/eventos). Crear sistema de modos: Conversación (amplificación + AEC), Entretenimiento (ecualización + mejora de audio), Exterior (detección de alertas + amplificación selectiva), Micrófono Remoto. Cada modo ajusta automáticamente el pipeline de audio.
- **Depende de**: Fase 3, Fase 4
- **Aprendizajes para fases siguientes**: Cada modo configura un ModeConfig con 5 parámetros (amplification, aecEnabled, nsEnabled, alertDetectionEnabled, eqPreset). El AudioModeManager aplica la configuración al pipeline de audio y al detector de alertas de forma coordinada.
- **Ajustes a la Directiva de Stack**: —
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

### Fase 6: Diseño UltraView, Geolocalización y UX Accesible
- **Estado**: COMPLETADO
- **Objetivo high-level**: Aplicar diseño UltraView: UI de alto contraste, tipografía escalable, elementos táctiles grandes, soporte dark/light mode, navegación simplificada para usuarios con dificultades visuales o auditivas. Implementar botón de geolocalización que abra Google Maps con búsqueda de otorrinolaringólogos o audiólogos cercanos según la ubicación del usuario. Agregar botón de nota de salud que despliegue aviso: en caso de molestia en los oídos se sugiere la visita a un médico especialista otorrinolaringólogo o audiólogo, con enlace directo a Google Maps para localizar uno cercano. Agregar onboarding con verificación de compatibilidad de hardware Bluetooth del dispositivo.
- **Depende de**: Fase 1, Fase 2
- **Aprendizajes para fases siguientes**: La geolocalización usa intent `geo:0,0?q=...` que abre Google Maps sin API key ni permisos de ubicación. Si Google Maps no está instalado, fallback a URL web de Google Maps en navegador. El onboarding es de 3 páginas: bienvenida, verificación BT, disclaimer PSAP con checkbox obligatorio.
- **Ajustes a la Directiva de Stack**: —
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

### Fase 7: Monetización, Legal y Distribución en Play Store
- **Estado**: COMPLETADO
- **Objetivo high-level**: Integrar Google Play Billing Library 8.0 con modelo freemium (gratis: amplificación básica, AEC, detección de alertas; premium: ecualizador avanzado, transcripción continua, whisper.cpp, micrófono remoto, perfiles de entorno). Configurar portabilidad entre dispositivos: la suscripción premium se restaura automáticamente vía cuenta Google al cambiar de móvil; Android Auto Backup transfiere perfiles de audio, configuración del ecualizador e historial de transcripciones al nuevo dispositivo (el antiguo pierde los datos al desvincular la cuenta o al factory reset). Implementar disclaimers legales PSAP ("Este producto no es un dispositivo médico. No está diseñado para diagnosticar ni tratar pérdida auditiva. Consulte a un profesional de la salud auditiva."). Agregar labeling en pantallas clave. Completar Data Safety section. Preparar listing de Play Store con recomendación de verificar compatibilidad de hardware pre-descarga.
- **Depende de**: Fase 3, Fase 5, Fase 6
- **Aprendizajes para fases siguientes**: BillingManager usa `BillingClient.ProductType.SUBS` para suscripciones mensuales. La restauración de compras se hace con `queryPurchasesAsync` al iniciar la app. Auto Backup configurado con `backup_rules.xml` (incluye Room DB + DataStore, excluye modelos ML) y `backup_rules_api31.xml` para API 31+.
- **Ajustes a la Directiva de Stack**: —
- **Iniciada**: 2026-08-03
- **Completada**: 2026-08-03

## Supuestos (deben ser verdad)

- [ ] El dispositivo Android del usuario tiene Android 10+ (API 29+) como mínimo para funcionalidad PSAP básica
- [ ] El usuario tiene auriculares Bluetooth compatibles (cualquier perfil: Classic, LE Audio, o ASHA)
- [ ] El micrófono del dispositivo Android es accesible vía Oboe/AAudio sin restricciones del fabricante
- [ ] Oboe + openMHA pueden mantener latencia end-to-end < 20ms en dispositivos de gama media (2022+)
- [ ] Los modelos de Vosk (~50 MB) y YAMNet (~3 MB) caben cómodamente en la memoria de dispositivos con 3+ GB RAM
- [ ] Google Play Billing Library 8.0 está disponible y estable para integración al momento del desarrollo
- [ ] Las APIs de AcousticEchoCanceler y NoiseSuppressor del sistema Android están implementadas en la mayoría de dispositivos objetivo (cobertura estimada > 85%)
- [ ] El desarrollador tiene acceso a Android Studio, NDK, y una cuenta de Google Play Developer ($25)

## Fuera de Alcance (NO construir en este brief)

- Streaming Auracast / broadcast LE Audio (pendiente para una actualización posterior de la app; requiere Android 16+ y hardware con baja penetración actual)
- Versión iOS (esto es Android-only por diseño; iOS requiere un brief separado)
- Backend en la nube / Supabase (toda la funcionalidad es offline-first; backend es futuro para sync opcional)
- Audiograma clínico o calibración audiológica (eso convertiría la app en dispositivo médico regulado)
- Integración con audífonos clínicos certificados FDA (solo PSAP para personas con audición normal)
- Llamadas de voz / VoIP (esta app amplifica audio ambiente, no es un softphone)
- Grabación persistente de audio (la transcripción es en tiempo real; no se almacena audio crudo por privacidad)
- Wear OS companion app (futuro)
- Soporte para idiomas de transcripción con modelos > 500 MB (mantener app ligera)
- Sistema de citas con especialistas (solo geolocalización y datos de contacto, no booking)

## Evaluacion

| Dimension | Nivel | Nota |
|-----------|-------|------|
| Complejidad tecnica | Alta | Pipeline de audio en tiempo real con C++/JNI (Oboe + openMHA), 3 protocolos Bluetooth, 2 motores de transcripción, ML on-device — requiere expertise en audio DSP y Android NDK |
| Riesgo / dependencias externas | Medio | Variabilidad del AEC del sistema por fabricante, latencia del pipeline depende del hardware, compatibilidad BLE Audio varía por dispositivo |
| Esfuerzo estimado | 7 fases | Proyecto ambicioso pero modular — cada fase entrega valor independiente y la app es usable desde Fase 3 |
| Costos externos recurrentes | ~$0-5/mes | App 100% on-device, sin backend ni APIs externas de pago. Único costo: Google Play Developer ($25 único). La geolocalización de especialistas abre Google Maps directamente (sin API key) |

## Fuentes Consultadas

- https://source.android.com/docs/core/connect/bluetooth/asha — Documentación oficial AOSP del protocolo ASHA para streaming de audio a audífonos
- https://www.bluetooth.com/auracast/developers/ — Portal de desarrolladores Auracast de Bluetooth SIG, especificaciones del broadcast audio LE
- https://developer.android.com/games/sdk/oboe — Documentación oficial de Oboe, librería C++ de Google para audio de baja latencia en Android
- https://doxygen.openmha.org/index.html — Documentación de openMHA (Open Master Hearing Aid), plataforma open-source de procesamiento de señales para audífonos
- https://alphacephei.com/vosk/android — Vosk offline speech recognition para Android, guía de integración con modelos ligeros
- https://github.com/ishizuki-tech/WhispersCpp-Android — Implementación de whisper.cpp para Android con JNI + Kotlin/Jetpack Compose
- https://blog.tensorflow.org/2021/09/easy-machine-learning-for-on-device-audio.html — TensorFlow Blog: ML on-device para audio con YAMNet y TF Lite
- https://developer.android.com/reference/kotlin/android/media/audiofx/AcousticEchoCanceler — API Android AcousticEchoCanceler (cancelación de eco acústico nativa)
- https://developer.android.com/reference/kotlin/android/media/audiofx/NoiseSuppressor — API Android NoiseSuppressor (supresión de ruido nativa)
- https://developer.android.com/google/play/billing/integrate — Integración de Google Play Billing Library para suscripciones in-app
- https://www.fda.gov/media/87330/download — Guía FDA sobre requisitos regulatorios para audífonos y diferenciación con PSAPs
- https://en.wikipedia.org/wiki/Personal_sound_amplification_product — Contexto regulatorio y definición de PSAPs vs audífonos
- https://open-earable.teco.edu/ — OpenEarable: plataforma open-source de AI para sensing en auriculares
- https://pmc.ncbi.nlm.nih.gov/articles/PMC9022875/ — Paper académico de openMHA como plataforma comunitaria para investigación de audífonos
- https://bleadvertiserapp.medium.com/auracast-on-android-16-the-ble-audio-shift-devs-are-building-wrong-fc30bd8a057e — Análisis técnico de Auracast en Android 16 y mejores prácticas para desarrolladores
