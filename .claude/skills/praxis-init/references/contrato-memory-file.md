# Contrato de upgrade del memory file

> Como escribir el resultado del analisis en el memory file del provider activo **sin reescribir** lo que el alumno ya tenia. La regla de oro: editar y augmentar, jamas reemplazar.

## El memory file objetivo depende del provider

Leer `.praxis/config.json#provider` y resolver el memory file del provider activo:

- `claude` (default) → `CLAUDE.md`.
- `codex` → el memory file estandar que usa Codex (formato AGENTS).
- `gemini` → el memory file que usa Gemini (formato GEMINI).

Praxis ya renombra estos archivos al cambiar de provider, asi que en la raiz del proyecto encontraras el que corresponde al provider activo — listala y trabaja sobre ese. Todo lo de abajo aplica al archivo resuelto. Si no hay `.praxis/config.json`, default `CLAUDE.md`.

## La seccion `## Contexto del Proyecto` (entre marcadores)

Escribir la sintesis del analisis entre un par de marcadores dedicado:

```markdown
<!-- PRAXIS:PROJECT_CONTEXT_START -->
## Contexto del Proyecto

<sintesis concisa: que es el proyecto, stack real con versiones, estructura,
base de datos, integraciones (env vars por nombre), convenciones, comandos reales>
<!-- PRAXIS:PROJECT_CONTEXT_END -->
```

**Idempotencia**: si los marcadores ya existen (re-ejecucion de la skill), reemplazar **solo** el contenido entre ellos. Nunca duplicar la seccion. Si no existen, insertarlos (ver ubicacion abajo).

**Conciso**: priorizar lo que cambia decisiones al programar. Si la seccion crece demasiado, el agente ignora la mitad. Donde vive la auth, como se corren las migraciones, que convencion respetar — eso si. El listado de los 200 componentes — no.

## Ediciones quirurgicas a las zonas criticas

Ademas de la seccion nueva, mejorar las zonas que ya describen el stack con los datos reales del proyecto:

- **Trust Stack / tabla de tecnologias**: si el proyecto usa Django + Postgres, reflejarlo — no dejar el Next.js + Supabase generico si no aplica.
- **Arquitectura / estructura de carpetas**: ajustar al arbol real.
- **Comandos**: reemplazar los comandos genericos por los reales (`make migrate`, `pnpm dev`, etc.).

Regla: **ediciones quirurgicas, no reescritura**. Cambiar las lineas que son falsas para este proyecto; preservar la doctrina Praxis y cualquier prosa que el alumno haya escrito. Si una zona ya es correcta, no tocarla.

## Los tres casos brownfield

1. **Memory file Praxis** (tiene marcadores `<!-- PRAXIS:*_START/END -->`): augmentar. Insertar/actualizar la seccion `## Contexto del Proyecto` y editar las zonas criticas marcadas. Ubicacion sugerida de los marcadores nuevos: cerca del Trust Stack, alto en el archivo (el contexto del proyecto es lo primero que el agente necesita).

2. **Memory file ajeno del alumno** (existe pero sin marcadores Praxis): NO reescribir su prosa. Anexar la seccion `## Contexto del Proyecto` con sus marcadores (al final, o tras la intro), dejando intacto todo lo que el alumno escribio. Es su archivo; Praxis solo añade un bloque acotado y reconocible.

3. **Sin memory file**: crear uno con un minimo de doctrina Praxis (el norte: metodologia recursiva brief → prp → bucle, y que el agente trabaje por fases con mapeo de contexto) + la seccion `## Contexto del Proyecto`. No copiar el `CLAUDE.md` completo del scaffold managed — sembrar solo lo esencial + el contexto real.

## Regla dura: sin secretos

El memory file es parte del prompt del agente y suele commitearse. Nunca escribir valores de variables de entorno, API keys, connection strings, ni tokens. Solo **nombres** de variables y para que sirven (`DATABASE_URL` — conexion a Postgres; `STRIPE_SECRET_KEY` — pagos). Si el analisis topo con un secreto en claro en el codigo, reportarlo al alumno como riesgo, pero **no** copiarlo al memory file.

## Verificacion post-escritura

- Los marcadores `PROJECT_CONTEXT_START/END` estan presentes y balanceados.
- El archivo sigue siendo markdown valido (headings bien formados, sin marcadores rotos).
- Los comandos citados existen de verdad (cruzar contra los scripts/Makefile reales).
- Re-ejecutar no duplica la seccion.
