# Dimensiones del analisis

> Las seis dimensiones canonicas que un proyecto necesita documentar para que el agente trabaje con certeza. Un agente read-only por dimension aplicable. No todas aplican a todo proyecto — saltar las que no (un sitio estatico sin backend no tiene dimension de base de datos).

## Las 6 dimensiones

| Dimension | Que responde | Donde mirar |
|---|---|---|
| **stack** | ¿Que lenguajes, framework, runtime y versiones reales usa? ¿Gestor de paquetes? | manifests (`package.json`, `requirements.txt`, `go.mod`, `Cargo.toml`...), lockfiles, `.nvmrc`/`.python-version`, Dockerfile |
| **estructura** | ¿Como esta organizado? ¿Entry points? ¿Donde vive cada cosa (rutas, modelos, UI, logica)? | arbol de carpetas, archivos `index`/`main`/`app`, convenciones de carpeta |
| **base de datos** | ¿Motor? ¿Esquema/modelos? ¿Migraciones? ¿ORM? ¿Como se conecta? ¿Hay RLS/policies? | carpetas `migrations`/`models`/`schema`, archivos ORM (Prisma, Drizzle, SQLAlchemy, ActiveRecord), clientes de BD |
| **integraciones** | ¿Que servicios externos consume (pagos, auth, email, IA, storage)? ¿APIs? ¿Webhooks? ¿Variables de entorno (por nombre)? | `.env.example`, clientes de SDK, carpetas `webhooks`/`api`, imports de paquetes externos |
| **convenciones** | ¿Estilo de codigo? ¿Naming? ¿Como se testea? ¿Lint/formatter? ¿Patrones repetidos? | configs (`.eslintrc`, `.prettierrc`, `tsconfig`, `pyproject`), carpeta de tests, archivos representativos |
| **superficie de comandos** | ¿Como se instala, corre, testea, buildea y despliega de verdad? | `scripts` del manifest, `Makefile`, `Justfile`, CI (`.github/workflows`), `README` |

## Reglas por dimension

- **Datos reales, no supuestos**: las versiones se leen de los manifests/lockfiles, no se infieren del nombre del framework. Los comandos se verifican contra los scripts reales, no se inventan.
- **Variables de entorno por nombre, jamas por valor**: la dimension de integraciones reporta `STRIPE_SECRET_KEY` existe y para que se usa, nunca su valor. Esto protege al alumno — el memory file es parte del prompt del agente.
- **Cada dimension devuelve un resumen estructurado**, no un volcado de archivos. El objetivo es lo que cambia decisiones al programar: donde vive la auth, como se corren las migraciones, que convencion de naming respetar. No un inventario exhaustivo.

## Output esperado de cada agente

Cada agente read-only entrega un bloque conciso (markdown) con:

```
## <dimension>
- <hallazgo 1 con evidencia: archivo/version/comando real>
- <hallazgo 2>
- Archivos clave: <2-5 paths que un humano leeria primero>
- Lagunas: <lo que no pudo determinar, si algo>
```

El campo **Lagunas** es importante: si un agente no pudo determinar algo (por tamaño del repo o ambiguedad), lo declara en vez de inventar. El orquestador decide si re-lanzar o documentar la incertidumbre.
