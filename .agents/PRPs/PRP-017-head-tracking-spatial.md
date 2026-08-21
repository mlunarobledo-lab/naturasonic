# PRP-017: Control por Gestos e Inferencia de Movimiento (Head-Tracking Spatial)

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-17
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Extiende la Fase 3 (Motor PSAP y Procesamiento de Senal) con enfoque direccional acustico controlado por movimiento de cabeza, aprovechando el pipeline existente de EQ biquad de 10 bandas con double-buffer atomico.
> Hereda Directiva de Stack, Supuestos, Fuera de Alcance, y aprendizajes heredados.

---

## Objetivo

> Quiero que NaturaSonic pueda detectar la orientacion de mi cabeza en tiempo real usando los sensores de movimiento del telefono y modifique el ecualizador automaticamente para crear un efecto de "foco direccional" — cuando miro hacia una direccion, los sonidos de esa direccion se amplifican con claridad, y cuando giro la cabeza, las frecuencias altas se atenuan imitando el filtrado natural de la cabeza humana (head shadow effect). Asi puedo enfocar mi atencion auditiva hacia una persona o fuente de sonido girando la cabeza, como lo haria alguien con audicion natural.

## Por Que

| Problema | Solucion |
|----------|----------|
| La amplificacion PSAP es omnidireccional — amplifica todo por igual sin distincion espacial | Head tracking aplica un modelo de atenuacion direccional que crea un "beam" de foco auditivo ajustado al movimiento |
| Usuarios con dificultad para distinguir conversaciones en entornos ruidosos no pueden "apuntar" a la fuente | El efecto de head shadow artificial atenua frecuencias altas fuera del eje de atencion, mejorando la relacion senal-ruido perceptual |
| No hay forma de calibrar la direccion "frente" sin intervencion manual | Boton de calibracion inercial establece el centro de referencia en la posicion actual |
| El control espacial requiere hardware especializado en audifonos clinicos | NaturaSonic simula el efecto usando el sensor de rotacion del telefono, accesible en cualquier smartphone moderno |

**Valor**: Direccionalidad acustica artificial — el procesamiento PSAP pasa de amplificacion plana a amplificacion inteligente que responde al movimiento, mejorando la comprension en entornos ruidosos sin hardware adicional.

## Que

### Criterios de exito
- [x] `HeadTrackingManager` singleton Hilt con `SensorEventListener` para `TYPE_ROTATION_VECTOR`
- [x] Calculo de azimut (yaw) y pitch desde la rotation matrix del sensor, relativos a un centro calibrado
- [x] StateFlow `HeadTrackingState` (Disabled, Calibrating, Active(azimuth, pitch), SensorUnavailable)
- [x] Canal JNI `setHeadTrackingAngles(azimuth, pitch)` con throttle a 50Hz max desde Kotlin
- [x] Modelo de atenuacion direccional en C++ que aplica offsets a las 10 bandas EQ basado en el angulo — bandas altas (4kHz+) se atenuan mas que las bajas cuando el azimut se aleja del centro
- [x] Los offsets espaciales son ADITIVOS al perfil EQ del usuario — no sobrescriben las ganancias base
- [x] Integracion lock-free en `EqSnapshot` usando el patron double-buffer existente
- [x] `HeadTrackingScreen` Compose con toggle de habilitacion, boton de calibracion, slider de sensibilidad, indicador visual de angulo
- [x] Ruta `HEAD_TRACKING` en NavGraph, accesible desde Settings → "Enfoque direccional"
- [x] Persistencia de preferencias (habilitado, sensibilidad) en DataStore
- [x] Build exitoso sin cambios en dependencias nativas ni AGP

### Comportamiento esperado

1. El usuario navega a Settings → "Enfoque direccional" → se abre `HeadTrackingScreen`.
2. La pantalla verifica si `TYPE_ROTATION_VECTOR` esta disponible. Si no: estado `SensorUnavailable` con card informativa.
3. El usuario activa el toggle → `HeadTrackingManager` registra el listener de sensor.
4. El usuario pulsa "Calibrar centro" con el telefono apuntando hacia la fuente de sonido deseada → se captura la rotation matrix como referencia.
5. A partir de ese momento, cada lectura del sensor calcula azimut y pitch relativos al centro calibrado.
6. Estos angulos se envian via JNI (throttled a 50Hz) al AudioProcessor C++.
7. El AudioProcessor aplica un modelo de atenuacion: a azimut 0 (mirando al centro), offsets = 0. A ±90 grados, bandas altas se atenuan hasta -6dB (ajustable por sensibilidad). Bandas bajas (<500Hz) apenas se modifican.
8. El efecto es inmediato y continuo — el EQ se adapta en tiempo real al movimiento.
9. Al desactivar el toggle o salir de la pantalla, los offsets se limpian y el EQ vuelve al perfil base del usuario.
10. Slider de sensibilidad (0.0-1.0) escala la magnitud maxima de los offsets.

### Casos borde

- **Sensor no disponible**: Algunos dispositivos budget no tienen `TYPE_ROTATION_VECTOR`. Estado `SensorUnavailable`, pantalla informativa, feature deshabilitada.
- **Sensor con drift**: El giroscopio acumula drift con el tiempo. El rotation vector (fusionado accel+gyro+mag) es resistente a drift, pero el usuario puede recalibrar en cualquier momento.
- **Telefono en bolsillo**: Si el usuario activa head tracking con el telefono en el bolsillo, los angulos seran erraticos. La pantalla muestra instruccion de sostener el telefono frente al pecho o en la mano.
- **Cambio de perfil EQ con head tracking activo**: Los offsets se aplican sobre las nuevas ganancias base — transicion suave.
- **AudioService detenido**: El head tracking se desactiva automaticamente al detener el servicio.
- **Output muteado**: Los offsets se siguen aplicando al EQ snapshot — consistente con el patron de que el procesamiento DSP no depende del estado del output.
- **Eco mode activo**: Head tracking no se throttlea por eco mode — el sensor consume energia negligible y las escrituras JNI son muy ligeras.
- **Sensibilidad 0**: Offsets siempre 0 — efectivamente desactivado sin desregistrar el sensor.

---

## Contexto

### Documentacion externa
- [SensorManager (Android Developers)](https://developer.android.com/reference/android/hardware/SensorManager) — API de sensores, `getRotationMatrixFromVector`, `getOrientation`
- [Sensor.TYPE_ROTATION_VECTOR](https://developer.android.com/reference/android/hardware/Sensor#TYPE_ROTATION_VECTOR) — fusionado accel+gyro+mag, estable a drift
- [Position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position) — documentacion oficial de sensores de posicion Android

### Codigo existente a consultar
- `app/src/main/cpp/audio_processor.h` — EqSnapshot con double-buffer atomico, kCenterFreqs[10], patron copy-modify-swap
- `app/src/main/cpp/audio_processor.cpp` — `process()` lee snapshot con `memory_order_acquire`, `applyEqualizer` aplica gains por banda
- `app/src/main/cpp/oboe_engine.h` — NaturaSonicEngine wrapper, delega a AudioProcessor
- `app/src/main/cpp/native-lib.cpp` — JNI bridge, patron de getters/setters
- `app/src/main/java/com/naturasonic/app/audio/OboeAudioEngine.kt` — Kotlin JNI wrappers, patron de external fun + coercion
- `app/src/main/java/com/naturasonic/app/data/preferences/UserPreferences.kt` — DataStore pattern con Flow + suspend setter
- `app/src/main/java/com/naturasonic/app/service/AudioService.kt` — lifecycle integration pattern
- `app/src/main/java/com/naturasonic/app/ui/screens/eco/EcoModeScreen.kt` — patron de pantalla con toggles, sliders, status cards
- `app/src/main/java/com/naturasonic/app/ui/screens/settings/SettingsScreen.kt` — patron de navegacion con lambda params
- `app/src/main/java/com/naturasonic/app/ui/navigation/NavGraph.kt` — Routes object + composable entries

### Gotchas conocidas
- **`SensorManager.getDefaultSensor(TYPE_ROTATION_VECTOR)` puede retornar null** en dispositivos sin el sensor fusionado. Siempre verificar antes de registrar listener.
- **`SENSOR_DELAY_GAME` (~20ms) genera muchos eventos** — throttle con `System.nanoTime()` para no saturar JNI. 50Hz (20ms) es suficiente para head tracking suave.
- **`SensorManager.getOrientation` retorna azimut en radianes [-PI, PI]** — azimut 0 es norte magnetico. Para head tracking relativo necesitamos delta desde calibracion, no absoluto.
- **Rotation vector a orientacion**: `getRotationMatrixFromVector(rotMatrix, event.values)` → `getOrientation(rotMatrix, orientation)` → `orientation[0]` = azimut, `orientation[1]` = pitch, `orientation[2]` = roll.
- **El double-buffer EqSnapshot ya tiene espacio**: añadir campos al struct y extender los setters existentes. El patron copy-modify-swap se replica exactamente como `setNoiseGateMode` o `setAmplification`.
- **Offsets deben ser ADITIVOS a gains base**: en `applyEqualizer`, leer `snap.gains[band] + snap.spatialOffsets[band]` en vez de solo `snap.gains[band]`. Si headtracking esta deshabilitado, offsets = 0 → no cambia nada.

---

## Directiva de Stack heredada

### Clasificacion
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Toda la infraestructura nativa actual
- Todas las dependencias — congeladas
- AGP 8.7.3 — congelado
- NDK / CMake / Oboe — congelados
- Pipeline Oboe C++ — se extiende, no se reemplaza

### ADD
- Ninguna dependencia nueva — `SensorManager` es API del framework Android

### REMOVE / REPLACE / CONFIG
- Ninguno

### Refinamientos a la Directiva durante este PRP
- Ninguno anticipado.

---

## Supuestos heredados

- [x] El dispositivo tiene Android 10+ (API 29+) como minimo
- [x] AudioService foreground service corriendo durante uso activo
- [x] EqSnapshot double-buffer con 10 bandas biquad funcional

### Supuestos adicionales (especificos de este PRP)
- [ ] `Sensor.TYPE_ROTATION_VECTOR` disponible en el dispositivo del usuario (la mayoria de smartphones desde 2015)
- [ ] El pipeline DSP tiene headroom para procesar gains con offsets aditivos sin cliping (el VolumeLimiter ya protege downstream)

---

## Fuera de Alcance heredado

- Audiograma clinico
- Version iOS
- Backend en la nube
- Integracion con audifonos clinicos FDA
- VoIP / llamadas de voz

### Fuera de Alcance adicional (especifico de este PRP)

- **Beamforming real con array de microfonos**: El efecto es perceptual via EQ, no beamforming fisico — el telefono solo tiene un microfono accesible via Oboe
- **Spatial audio 3D / HRTF**: No se aplica HRTF completo — el modelo es una atenuacion simplificada por bandas basada en head shadow
- **Tracking continuo sin AudioService**: El head tracking solo opera cuando el servicio de audio esta activo
- **Persistencia de calibracion entre sesiones**: La calibracion es volatil — se reinicia al activar. Persistir la rotation matrix de referencia añadiria complejidad sin valor claro
- **Tracking via camara / face mesh**: Solo sensores IMU, no vision

---

## Aprendizajes heredados

- **2026-08-13 (PRP-007)**: Double-buffer copy-modify-swap como patron canonico para parametros DSP. Lectores (audio thread): `load(memory_order_acquire)` → const ref. Escritores: `lock_guard<mutex>` → copy → modify → `store(memory_order_release)`. Nunca atomics independientes para parametros coherentes.
- **2026-08-15 (PRP-014)**: `applyProfile` API y EqSnapshot evolucionan agregando campos. Patron: (1) agregar campo al struct, (2) nuevo setter copy-modify-swap, (3) extender applyProfile, (4) JNI wrapper en native-lib.cpp, (5) Kotlin wrapper en OboeAudioEngine.
- **2026-08-17 (PRP-016)**: DataStore para settings simples (toggles, sliders, umbrales). Pattern establecido en UserPreferences con Flow + suspend setter.

---

## Plan de implementacion

> IMPORTANTE: solo FASES aqui. Las subtareas se generan al ENTRAR a cada fase.

### Fase 1: HeadTrackingManager + Sensor Integration
- **Objetivo**: Crear `HeadTrackingManager` singleton Hilt con `SensorEventListener` para `TYPE_ROTATION_VECTOR`. Calcular azimut y pitch relativos a un centro calibrado. Exponer `StateFlow<HeadTrackingState>`. Throttle a 50Hz. Persistir `headTrackingEnabled` y `headTrackingSensitivity` en DataStore.
- **Validacion**: Compilacion exitosa. StateFlow emite `SensorUnavailable` si no hay sensor, `Active(azimuth, pitch)` con angulos actualizandose al girar el telefono.

### Fase 2: C++ Spatial EQ Integration
- **Objetivo**: Extender `EqSnapshot` con `float spatialGainOffsets[kMaxEqBands]` y `bool headTrackingEnabled`. Implementar `setHeadTrackingAngles(float azimuth, float pitch)` en AudioProcessor con modelo de atenuacion direccional coseno-based (bandas altas se atenuan mas, bandas bajas apenas afectadas). Modificar `applyEqualizer` para sumar offsets. Agregar `setHeadTrackingEnabled(bool)` y `clearSpatialOffsets()`. Extender JNI bridge y OboeAudioEngine.
- **Validacion**: Build C++ exitoso. Setter JNI invocable desde Kotlin. Offsets se aplican al EQ cuando enabled, offsets = 0 cuando disabled.

### Fase 3: HeadTrackingScreen + ViewModel + Navegacion
- **Objetivo**: Crear `HeadTrackingScreen` Compose con toggle de habilitacion, boton de calibracion, slider de sensibilidad (0.0-1.0), indicador visual de angulo (Canvas compass). Crear `HeadTrackingViewModel`. Agregar ruta `HEAD_TRACKING` a NavGraph. Agregar boton "Enfoque direccional" en SettingsScreen.
- **Validacion**: Pantalla renderiza correctamente. Toggle activa/desactiva sensores. Calibracion resetea centro. Slider persiste en DataStore.

### Fase 4: Integracion con AudioService + Validacion Final
- **Objetivo**: Integrar `HeadTrackingManager` en `AudioService` para lifecycle management (start/stop con servicio). Conectar el flujo de angulos → JNI → AudioProcessor via coroutine que observa los StateFlows del manager. Validacion final del build.
- **Validacion**:
  - [x] `./gradlew assembleDebug` exitoso en 3 arquitecturas
  - [x] No hay cambios en dependencias nativas ni AGP
  - [x] Head tracking se desactiva limpiamente al detener AudioService

---

## Aprendizajes

**2026-08-17: Offsets espaciales requieren recomputo de coeficientes biquad, no solo suma en applyEqualizer**
- **Error**: Inicialmente solo se sumaron offsets en `applyEqualizer` al leer gains. Pero los coeficientes biquad se pre-computan en `computeEqCoefficients` desde las gains base — sumar offsets despues no modifica la respuesta del filtro.
- **Fix**: `computeEqCoefficients` ahora calcula `gainDb = gains[i] + spatialGainOffsets[i]` cuando `headTrackingEnabled`. `setHeadTrackingAngles` llama a `computeEqCoefficients` dentro del mutex antes del swap atomico. El audio thread lee coeficientes ya computados — zero-lock.
- **Aplicar en**: Cualquier futuro parametro que modifique la respuesta EQ debe integrarse en `computeEqCoefficients`, no en `applyEqualizer`.

---

## Anti-patrones

- No modificar las ganancias base del usuario — los offsets son aditivos y se limpian al desactivar
- No registrar el sensor sin verificar disponibilidad con `getDefaultSensor(TYPE_ROTATION_VECTOR) != null`
- No enviar datos de sensor directamente a JNI sin throttle — saturaria el audio thread con mutex contention
- No crear atomics independientes para azimuth/pitch — deben ir dentro del EqSnapshot como parte del snapshot atomico
- No asumir orientacion absoluta — siempre calcular delta desde calibracion
- No olvidar limpiar los offsets al desactivar head tracking — dejar offsets residuales alteraria el EQ del usuario

---

*PRP COMPLETADO 2026-08-17. Todas las fases implementadas y build verificado.*
