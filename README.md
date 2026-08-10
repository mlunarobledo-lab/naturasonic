# NaturaSonic

App Android nativa de amplificación de sonido personal (PSAP) y bienestar auditivo. Convierte cualquier par de auriculares Bluetooth (LE Audio, ASHA o Classic) en un sistema inteligente de amplificación con transcripción offline, detección de alertas sonoras y modos de escucha adaptables.

---

## Funcionalidades

| Feature | Descripción |
|---------|-------------|
| Amplificación PSAP | Audio del micrófono procesado en tiempo real con baja latencia vía Oboe (C++) |
| Ecualizador | 10 bandas de frecuencia (125 Hz - 12 kHz) con perfiles guardables |
| Transcripción offline | Subtítulos en tiempo real con Vosk (~50 MB modelo descargable) |
| Detección de alertas | YAMNet/TF Lite detecta sirenas, timbres, alarmas con vibración |
| Modos de escucha | Conversación, Entretenimiento, Exterior, Micrófono Remoto |
| Protección auditiva | Límite hard 85 dB, bloqueo a 70 dB tras 1 hora continua |
| Cancelación de eco | AcousticEchoCanceler + NoiseSuppressor del sistema Android |
| Nota de salud | Recomendación de visitar otorrinolaringólogo/audiólogo con enlace a Google Maps |
| Diseño UltraView | Alto contraste, tipografía escalable, touch targets ≥ 48dp, dark/light mode |

---

## Requisitos

- Android 10+ (API 29+)
- Auriculares Bluetooth (LE Audio, ASHA o Classic)
- 3+ GB RAM (para modelos ML)

---

## Stack técnico

| Capa | Tecnología |
|------|------------|
| Lenguaje | Kotlin 2.0+ / C++17 |
| UI | Jetpack Compose + Material 3 |
| Audio | Oboe 1.9 (C++ vía JNI) |
| PSAP | Implementación directa (biquad EQ, volume limiter, noise gate) |
| Transcripción | Vosk 0.3.45+ (offline, streaming) |
| Detección de sonidos | TensorFlow Lite + YAMNet |
| Bluetooth | LE Audio (API 33+), ASHA (API 29+), Classic |
| DI | Hilt |
| BD local | Room |
| Preferencias | DataStore |
| Navegación | Navigation Compose |
| Estado | ViewModel + StateFlow |
| Billing | Google Play Billing Library |
| Backup | Android Auto Backup |

---

## Estructura del proyecto

```
naturasonic/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/                          # Motor nativo C++
│       │   ├── CMakeLists.txt
│       │   ├── native-lib.cpp            # JNI bridge
│       │   ├── oboe_engine.cpp/h         # Pipeline Oboe
│       │   ├── audio_processor.cpp/h     # EQ + amplificación + noise gate
│       │   └── volume_limiter.cpp/h      # Limitador 85/70 dB
│       ├── java/com/naturasonic/app/
│       │   ├── MainActivity.kt
│       │   ├── NaturaSonicApp.kt
│       │   ├── audio/                    # Engine + AEC/NS + modos + protección
│       │   ├── bluetooth/                # BLE/ASHA manager
│       │   ├── transcription/            # Vosk engine
│       │   ├── detection/                # YAMNet alert detector
│       │   ├── billing/                  # Play Billing manager
│       │   ├── service/                  # Foreground audio service
│       │   ├── data/                     # Room DB + DataStore
│       │   ├── di/                       # Hilt modules
│       │   └── ui/                       # Compose screens
│       │       ├── theme/
│       │       ├── navigation/
│       │       └── screens/
│       │           ├── home/
│       │           ├── onboarding/
│       │           ├── transcription/
│       │           └── settings/
│       └── res/
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
├── docs/BRIEF-naturasonic.md
└── .claude/PRPs/PRP-001-naturasonic.md
```

---

## Build

```bash
./gradlew assembleDebug      # Build debug
./gradlew assembleRelease    # Build release (requiere signing config)
./gradlew lint               # Lint
```

---

## Modelo freemium

| Gratis | Premium |
|--------|---------|
| Amplificación básica | Ecualizador avanzado (10 bandas) |
| Cancelación de eco/ruido | Transcripción continua |
| Detección de alertas | Micrófono remoto |
| Modo Conversación | Perfiles de entorno ilimitados |
| | whisper.cpp (alta fidelidad) |

---

## Aviso legal (PSAP)

Este producto no es un dispositivo médico. No está diseñado para diagnosticar, tratar ni prevenir pérdida auditiva. NaturaSonic es un producto de amplificación de sonido personal (PSAP) destinado a mejorar la experiencia auditiva en situaciones cotidianas. Si experimenta problemas de audición, consulte a un profesional de salud auditiva.

---

## Sistema agéntico

El directorio `.claude/` contiene el sistema agéntico Praxis que opera sobre este proyecto. La metodología recursiva (`brief → prp → bucle-agentico`) documenta todas las decisiones técnicas en los PRPs bajo `.claude/PRPs/`.
