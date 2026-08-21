---
name: praxis-init
description: "Analiza a fondo un proyecto que ya existe (con codigo, historia, incluso en produccion) orquestando un equipo de agentes read-only en paralelo —stack, estructura, base de datos, integraciones, convenciones, comandos— y agrega una seccion de Contexto del Proyecto verificada al memory file de Praxis sin reescribir lo que el alumno ya tenia. Activar cuando el usuario menciona tengo un proyecto ya hecho, analiza mi codigo, conoce mi proyecto, dame contexto del codebase, usa Praxis en un proyecto existente, mapea mi repo, o quiere trabajar con Praxis sobre codigo que no construyo Praxis."
allowed-tools: Read, Write, Edit, Bash, Grep, Glob, Agent, TeamCreate, SendMessage, TodoWrite
effort: max
---

# Skill: praxis-init — Praxis aterriza en un proyecto que ya existe

> Da al agente certeza absoluta sobre un proyecto existente: lanza un equipo de agentes read-only que radiografia todo el codebase y deja el memory file del provider activo (`CLAUDE.md` en Claude, o el archivo de memoria equivalente en Codex/Gemini) con una seccion de Contexto del Proyecto verdadera, sin reescribir nada de lo que el alumno ya tenia.
>
> Esta skill cumple el [STYLE-GUIDE de skills Praxis](../STYLE-GUIDE.md) (Skills 2.0 spec + voz canonica + bundle structure). Leer antes de modificar.

`praxis-init` es la pieza para usar Praxis sobre **terreno construido**. El INIT de un proyecto vacio monta un scaffold que el agente ya conoce. Un proyecto existente es lo contrario: el agente entra ciego. Praxis se instala sin pisar nada del alumno (modo convivencia), pero instalar no es conocer. Esta skill cierra esa brecha — analiza el proyecto entero y deja documentado lo que importa para que el agente trabaje despues con la misma doctrina Praxis sobre datos reales.

Es el superset Praxis del `/init` nativo: en vez de un solo agente que genera un archivo de cero, paraleliza el analisis con un equipo y **augmenta** el memory file sin destruir lo existente.

---

## Cuando activar

- "Tengo un proyecto ya hecho / en produccion y quiero usar Praxis aqui."
- "Analiza mi codigo / conoce mi proyecto / estudia este repo."
- "Dame contexto del codebase / mapea mi proyecto."
- "Acabo de instalar Praxis en un proyecto que ya existia — ¿que sigue?"
- "Necesito que entiendas como funciona esto antes de tocar nada."

## Cuando NO activar

- **Proyecto vacio / recien iniciado por Praxis (modo managed)**: el scaffold ya es conocido y el memory file ya lo describe. No hay nada que radiografiar.
- **Repo trivial** (2-3 archivos): leerlos directo con `Read` es mas rapido que montar un equipo. Anunciar que se hace inline y saltar la orquestacion.
- **El usuario pide implementar una feature**, no entender el proyecto: eso es `prp` + `bucle-agentico`.

## Antes de empezar — verifica empiricamente

Por la doctrina Praxis (investigar antes de preguntar), no preguntes nada de esto al alumno: averigualo.

- [ ] **Provider activo**: leer `.praxis/config.json#provider` (`claude`/`codex`/`gemini`). Define el memory file objetivo — en Claude es `CLAUDE.md`; en Codex/Gemini, el archivo de memoria que ese provider usa (Praxis ya lo dejo en el proyecto, listalo en la raiz). Si no hay config, default `claude` → `CLAUDE.md`.
- [ ] **Estado del memory file**: ¿existe? ¿tiene marcadores `<!-- PRAXIS:*_START/END -->` (proyecto Praxis) o es ajeno del alumno? El contrato de escritura cambia segun el caso (ver `references/contrato-memory-file.md`).
- [ ] **Tamaño del repo**: `git ls-files | wc -l` (o `find . -type f` excluyendo `node_modules`/`.git`/build). Define cuantos agentes lanzar y si hace falta dividir por capas.
- [ ] **Grafo de conocimiento (`.graphify/`)**: ¿existe `.graphify/graph.json`? Si si, es tu mejor mapa estructural de arranque: `graphify summary --graph .graphify/graph.json` te da los **god nodes** (los archivos mas conectados = por donde empezar) y las **comunidades** (como se agrupa el sistema) — usalos para dividir el trabajo entre los agentes de dimension. Si no existe y el repo no es trivial, **construirlo con `@.claude/skills/graphify/SKILL.md` es parte natural de este analisis**: da la esqueleto estructural del proyecto en un artefacto persistente que el agente reusara en cada sesion futura (no solo para este INIT).
- [ ] **Herramientas de equipo disponibles**: si `TeamCreate`/`Agent` no estan (provider no-Claude), degradar a analisis secuencial — ver `references/orquestacion-equipo.md`.

---

## Flujo principal

### Paso 1: Mapear el meta-contexto del repo

Antes de lanzar el equipo, hacer un barrido superficial barato para orientar la division del trabajo: leer los manifests de raiz (`package.json`, `requirements.txt`, `go.mod`, `Cargo.toml`, `composer.json`, `pom.xml`, etc.), el `README` si existe, y el arbol de carpetas de primer nivel. Razon: cada agente del equipo arranca con una pista de donde mirar en vez de descubrir el lenguaje desde cero, lo que ahorra contexto y evita solapamiento.

Si hay (o construyes) un **grafo de conocimiento** (`.graphify/`), este barrido se apoya en el: `graphify summary` revela god nodes y comunidades — el esqueleto estructural que dice a cada agente de dimension por donde empezar. El grafo es el mapa; los agentes lo profundizan.

Registrar las **dimensiones** que aplican (un repo sin base de datos no necesita el agente de BD). Las dimensiones canonicas y que cubre cada una viven en `references/analisis-dimensiones.md` — leerlo ahora.

### Paso 2: Lanzar el equipo de analisis read-only

Diseñar un agente por dimension aplicable y lanzarlos en paralelo. La receta completa (roles, prompts, escalado al tamaño del repo, degradacion sin `TeamCreate`) vive en `references/orquestacion-equipo.md` — leerlo al entrar a este paso.

Contrato duro, no negociable:

- **Los agentes de analisis son read-only.** Solo leen (`Grep`/`Glob`/`Read`/`Bash` de lectura) y devuelven un resumen estructurado de su dimension. Nunca escriben ni modifican archivos del proyecto. Razon: el alumno tiene codigo en produccion; el analisis produce conocimiento, jamas cambios.
- **Nunca combinar salida estructurada con escritura en el mismo agente.** Un agente que tiene que entregar un resumen Y editar archivos falla a medias. Separar: los agentes analizan y reportan; el orquestador (tu) es el unico que escribe.
- **En repos grandes, escalar por capas y declarar lo omitido.** Si el repo es enorme y se muestrea o se prioriza, decirlo explicitamente en el reporte — nunca truncar en silencio (un analisis parcial presentado como completo es peor que uno honesto).

### Paso 3: Sintetizar los resumenes

Cuando todos los agentes reporten, no confiar en el conteo de "completados" como prueba de trabajo — leer cada resumen y verificar que tiene contenido util. Si una dimension volvio vacia o pobre, re-lanzar ese agente sobre lo que falto (loop hasta tener cobertura real).

Sintetizar los resumenes en un retrato unico y conciso del proyecto: que es, con que esta hecho, como se organiza, como se conecta, como se corre. Priorizar lo que **cambia decisiones del agente** al programar — no un inventario exhaustivo de cada archivo.

### Paso 4: Augmentar el memory file (sin reescribir)

Escribir el resultado en el memory file del provider activo siguiendo `references/contrato-memory-file.md` — leerlo al entrar a este paso. En resumen:

- Agregar la seccion `## Contexto del Proyecto` entre marcadores `<!-- PRAXIS:PROJECT_CONTEXT_START -->` y `<!-- PRAXIS:PROJECT_CONTEXT_END -->`. Idempotente: re-ejecutar reemplaza solo lo que esta entre marcadores.
- Editar quirurgicamente las zonas criticas (Trust Stack, Arquitectura, Comandos) con los datos reales del proyecto, preservando la doctrina y cualquier prosa que el alumno haya escrito.
- **Sin secretos**: registrar nombres de variables de entorno, jamas sus valores. El memory file es parte del prompt del agente — tratarlo como documento que podria compartirse.

### Paso 5: Verificar y reportar

- Confirmar en disco que la seccion quedo entre marcadores, que el archivo sigue siendo markdown valido, y que los comandos citados existen de verdad (probar que `package.json` tiene los scripts mencionados, etc.).
- Reportar al alumno en voz cercana: que encontro el equipo (stack, estructura, BD, integraciones) y que se actualizo en el memory file. Si algo quedo fuera del analisis (repo grande), decirlo.

---

## Cross-references con skills hermanas

- `@.claude/skills/build-with-agent-team/SKILL.md` — fuente de los patrones de roles del equipo. `praxis-init` reutiliza esos patrones en **modo analisis read-only** (no construccion); orquesta su propio equipo, no modifica `build-with-agent-team`.
- `@.claude/skills/bucle-agentico/SKILL.md` — despues de `praxis-init`, el agente ya tiene contexto real; cualquier feature compleja se ejecuta con la doctrina por fases sobre terreno conocido.
- `@.claude/skills/supabase-admin/SKILL.md` — si la dimension de base de datos detecta Supabase, esta skill es la via para inspeccionar tablas/RLS en profundidad.

## Archivos lazy-loaded

- `references/analisis-dimensiones.md` — las 6 dimensiones canonicas del analisis y que cubre cada una. Leer en Paso 1.
- `references/orquestacion-equipo.md` — como diseñar y lanzar el equipo read-only, escalado al tamaño del repo, degradacion sin `TeamCreate`. Leer en Paso 2.
- `references/contrato-memory-file.md` — como augmentar el memory file sin reescribir: marcadores, ediciones quirurgicas, los tres casos brownfield, regla sin-secretos. Leer en Paso 4.

## Validacion al cerrar

- [ ] El memory file del provider activo tiene la seccion `## Contexto del Proyecto` entre sus marcadores, con datos reales del proyecto.
- [ ] Ningun archivo del proyecto del alumno fue modificado por los agentes de analisis (solo el memory file por el orquestador).
- [ ] No hay secretos en lo escrito (solo nombres de variables de entorno).
- [ ] Re-ejecutar la skill no duplica la seccion (idempotencia).
