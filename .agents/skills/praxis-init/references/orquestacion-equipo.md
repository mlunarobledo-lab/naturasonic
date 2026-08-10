# Orquestacion del equipo de analisis read-only

> Como lanzar el equipo que radiografia el proyecto. Reutiliza los patrones de roles de `@.agents/skills/build-with-agent-team/SKILL.md`, pero en **modo analisis**: los agentes solo leen y reportan, nunca construyen.

## Principio: un agente por dimension, en paralelo, read-only

Cada dimension aplicable (ver `analisis-dimensiones.md`) se asigna a un agente. Corren en paralelo porque son independientes: el agente de stack no necesita lo que encuentre el de base de datos. Cada uno vive en su propia ventana de contexto y devuelve un resumen — asi el orquestador no quema su contexto leyendo miles de archivos directamente.

## Plantilla de mensaje al agente

```
Eres un analista de codigo READ-ONLY. NO modifiques ningun archivo del proyecto.

Tu dimension: <stack | estructura | base de datos | integraciones | convenciones | comandos>.
Pista inicial (del barrido del orquestador): <lenguaje/framework detectado, carpetas relevantes>.

Investiga SOLO tu dimension usando Grep/Glob/Read y Bash de lectura.
Reporta un resumen estructurado (formato en analisis-dimensiones.md):
- hallazgos con evidencia real (archivo, version, comando)
- archivos clave (2-5 paths)
- lagunas (lo que no pudiste determinar)

Variables de entorno: reporta SOLO nombres, nunca valores.
No escribas codigo. No edites archivos. Solo lee y reporta.
```

## Escalado al tamaño del repo

| Tamaño | Estrategia |
|---|---|
| **Chico** (< ~50 archivos) | Considerar saltar el equipo: leer inline es mas barato. Anunciarlo. |
| **Mediano** (~50-1000 archivos) | Un agente por dimension aplicable (tipicamente 4-6). Caso comun. |
| **Grande / monorepo** (> ~1000 archivos) | Dividir por capas: primero un agente que mapea los paquetes/modulos top-level, luego un equipo por paquete relevante. Priorizar los modulos con mas actividad (`git log` reciente). **Declarar que modulos quedaron fuera del analisis** — nunca presentar un analisis parcial como completo. |

## Verificacion: completado ≠ hecho

El conteo de agentes que "terminaron" no prueba que entregaron contenido util. Tras recolectar los resumenes:

1. Leer cada uno. Si una dimension volvio vacia o con solo "lagunas", re-lanzar ese agente con una pista mas concreta.
2. Repetir hasta tener cobertura real de las dimensiones aplicables (loop-until-done).
3. Solo entonces sintetizar.

## Degradacion sin TeamCreate (provider no-Claude)

`TeamCreate`/`Agent` son herramientas de Claude Code. Si el provider activo es Codex o Gemini y no estan disponibles:

- Ejecutar el analisis **secuencial**: el agente principal recorre las dimensiones una por una, manteniendo cada una acotada para no saturar el contexto (resumir y descartar detalle entre dimensiones).
- El resultado es el mismo retrato sintetizado; solo cambia que no hubo paralelismo. Anunciar al alumno que el analisis fue secuencial (mas lento, mismo resultado).

## Anti-patrones

- ❌ Un agente con salida estructurada (schema) que ademas edita archivos — falla a medias. Analista lee y reporta; orquestador escribe.
- ❌ Dos agentes cubriendo la misma dimension — desperdicia contexto. Una dimension, un agente.
- ❌ Truncar el analisis de un repo grande sin avisar — el alumno creera que el contexto es completo cuando no lo es.
