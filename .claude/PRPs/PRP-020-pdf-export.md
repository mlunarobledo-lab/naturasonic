# PRP-020: Motor de Exportación de Reportes de Bienestar en PDF

> **Estado**: COMPLETADO
> **Fecha**: 2026-08-20
> **Proyecto**: NaturaSonic

---

## Origen

> Derivado de `@docs/BRIEF-naturasonic.md`. Feature de bienestar auditivo que compila datos de alertas (YAMNet) y métricas vocales (VoiceAnalyzer) en un reporte PDF exportable con disclaimer PSAP obligatorio.

---

## Objetivo

> Quiero que mis usuarios puedan exportar un reporte PDF con su historial de bienestar auditivo de los últimos 7 días — alertas detectadas y métricas de salud vocal — para compartirlo con su fonoaudiólogo o guardarlo como registro personal. El documento debe dejar claro que NaturaSonic es un PSAP, no un dispositivo médico.

## Por Qué

| Problema | Solución |
|----------|----------|
| Los datos de alertas y métricas vocales solo se ven en pantalla, no se pueden compartir ni archivar | Reporte PDF exportable con historial de 7 días |
| Un usuario no puede mostrar sus tendencias vocales a un profesional de salud | PDF compartible vía email, mensajería o impresión |
| Sin disclaimer visible, el usuario podría confundir métricas informativas con diagnóstico clínico | Disclaimer PSAP en cabecera y pie de cada página del PDF |

**Valor**: Los usuarios pueden llevar un registro tangible de su bienestar auditivo y vocal, compartirlo con profesionales de salud, y tener evidencia documental de tendencias a lo largo del tiempo.

## Qué

### Criterios de éxito
- [x] Las métricas de VoiceAnalyzer (pitch, jitter, shimmer) se persisten en Room con timestamp
- [x] El PDF incluye resumen de alertas YAMNet de los últimos 7 días agrupadas por clase
- [x] El PDF incluye resumen diario de métricas vocales (promedios jitter/shimmer por día)
- [x] Cabecera de cada página: "NaturaSonic — Reporte de Bienestar" + disclaimer PSAP
- [x] Pie de cada página: disclaimer completo + fecha de generación
- [x] El PDF se genera con PrintedPdfDocument (API nativa, sin deps externas)
- [x] El usuario puede compartir/guardar el PDF desde un botón en la UI
- [x] Pantalla ExportReportScreen accesible desde Settings y VoiceHealthScreen
- [x] Build compila sin errores (`./gradlew assembleDebug`)

### Comportamiento esperado

El usuario navega a "Exportar reporte" desde Settings o desde la pantalla de Salud Vocal. Ve un resumen previo de cuántas alertas y muestras vocales hay en los últimos 7 días. Pulsa "Generar PDF". La app compila los datos de Room, genera un PDF con PrintedPdfDocument usando Canvas para dibujar texto, tablas y separadores. El PDF se guarda en el directorio cache de la app y se abre un share sheet del sistema para que el usuario lo comparta, guarde en Files, o imprima. Cada página lleva disclaimer PSAP en cabecera y pie.

### Casos borde

- Sin datos en los últimos 7 días: el PDF se genera con secciones vacías indicando "Sin registros en este período"
- Más de 50 alertas en 7 días: paginación automática (PrintedPdfDocument soporta múltiples páginas)
- Permisos de almacenamiento: no se necesitan — se usa cache dir + FileProvider + share intent
- PDF excesivamente largo: limitar a resumen estadístico, no listar cada evento individual si > 100

---

## Contexto

### Código existente a consultar
- `app/src/main/java/com/naturasonic/app/data/local/entity/AlertEvent.kt` — entity Room de alertas YAMNet
- `app/src/main/java/com/naturasonic/app/data/local/dao/AlertEventDao.kt` — DAO sin query por rango de fecha (necesita `getSince`)
- `app/src/main/java/com/naturasonic/app/data/repository/AlertHistoryRepository.kt` — repositorio de alertas
- `app/src/main/java/com/naturasonic/app/audio/VoiceHealthRepository.kt` — métricas vocales en memoria (no persiste a Room)
- `app/src/main/java/com/naturasonic/app/data/local/AppDatabase.kt` — Room v3, 4 entities
- `app/src/main/java/com/naturasonic/app/di/AppModule.kt` — Hilt bindings de DAOs
- `app/src/main/AndroidManifest.xml` — sin FileProvider configurado

### Gotchas conocidas
- VoiceMetrics NO tiene persistencia Room — solo existe en memoria (StateFlow con max 60 puntos de sesión). Se necesita entity + DAO + migration v3→v4 para historial de 7 días
- AlertEventDao no tiene query por rango de fecha — necesita `getSince(timestamp)`
- FileProvider no está configurado — necesario para compartir PDFs vía intent
- PrintedPdfDocument usa Canvas para dibujar — todo el layout es manual (coordenadas X/Y, Paint objects)
- El pipeline Oboe alimenta VoiceAnalyzer continuamente pero los datos no se guardan entre sesiones

### Modelo de datos

```sql
-- Nueva tabla: voice_metrics (migration v3→v4)
CREATE TABLE IF NOT EXISTS voice_metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    pitchHz REAL NOT NULL,
    jitterPercent REAL NOT NULL,
    shimmerPercent REAL NOT NULL,
    recordedAt INTEGER NOT NULL
);
```

---

## Directiva de Stack heredada

### Clasificación
- **Tipo**: mobile-android-native
- **Compatibilidad con Praxis**: REPLACE

### KEEP
- Room v3 → v4 (migration incremental)
- PrintedPdfDocument (API nativa Android, disponible desde API 19)
- FileProvider (androidx.core, ya presente como dependencia transitiva)

### ADD
- Ninguna dependencia nueva

### REMOVE
- Ninguna

### CONFIG
- `AndroidManifest.xml` — agregar `<provider>` para FileProvider
- `app/src/main/res/xml/file_provider_paths.xml` — paths de cache para PDFs

---

## Supuestos heredados

- [x] Room database v3 operativa con 4 entities
- [x] VoiceAnalyzer C++ alimenta VoiceHealthRepository con métricas cada 500ms
- [x] AlertEventDao persiste eventos YAMNet con timestamp
- [x] Hilt configurado para inyección de DAOs y repositorios
- [x] Navigation Compose con NavGraph centralizado

---

## Fuera de Alcance

- Gráficos embebidos en el PDF (solo texto tabular y separadores — Canvas drawing de charts complejos queda fuera)
- Exportación en formatos distintos a PDF (CSV, JSON)
- Envío automático por email o sync a la nube
- Personalización del contenido del reporte por el usuario
- Traducción del reporte a múltiples idiomas

---

## Aprendizajes heredados de fases previas

- **2026-08-12**: Ring buffer C++ para consumidores Kotlin — VoiceAnalyzer usa este patrón con buffer de 2s
- **2026-08-13**: Double-buffer EqSnapshot — VoiceMetrics pasa por JNI polling, no por callback
- **2026-08-03**: Comandos de validación son `./gradlew assembleDebug`, no npm

---

## Plan de implementación

### Fase 1: Persistencia — VoiceMetrics Room + AlertEvent query por fecha
- **Objetivo**: Crear entity `VoiceMetricsEntry` en Room, DAO con queries temporales, migration v3→v4, actualizar VoiceHealthRepository para persistir muestras. Agregar `getSince()` a AlertEventDao.
- **Validación**: Build compila, VoiceHealthRepository persiste a Room además de mantener StateFlow en memoria.

### Fase 2: Motor PDF — WellnessReportGenerator con PrintedPdfDocument
- **Objetivo**: Crear clase que consulta 7 días de datos de ambos DAOs, genera PDF multipágina con Canvas (cabecera, resumen alertas, resumen métricas vocales, pie con disclaimer). Configurar FileProvider para compartir.
- **Validación**: PDF se genera correctamente con disclaimer PSAP en cabecera y pie de cada página. FileProvider registrado en AndroidManifest.

### Fase 3: UI — ExportReportScreen + navegación
- **Objetivo**: Crear ExportReportScreen con vista previa del contenido disponible (conteo de alertas, muestras vocales en 7 días) y botón de exportar. ViewModel que orquesta la generación. Navegación desde Settings y VoiceHealthScreen.
- **Validación**: Pantalla accesible, botón dispara generación + share intent.

### Fase 4: Validación final
- **Objetivo**: Sistema funcionando end-to-end
- **Validación**:
  - [ ] `./gradlew assembleDebug` sin errores
  - [ ] Criterios de éxito cumplidos
  - [ ] PRP marcado COMPLETADO
  - [ ] CLAUDE.md actualizado si hay aprendizajes transversales

---

## Aprendizajes

> Esta sección crece con cada error. El conocimiento persiste para futuros PRPs.

---

## Anti-patrones

- No añadir dependencias externas para PDF (iTextPDF, Apache PDFBox) — usar API nativa
- No ignorar el disclaimer PSAP — es requisito legal no negociable
- No almacenar PDFs permanentemente — usar cache dir, el usuario decide si guarda
- No commitear secrets

---

*PRP en ejecución — bucle-agentico activo.*
