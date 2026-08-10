---
name: infra-vps
description: "Convierte al agente en experto DevOps para levantar y operar tu propia infraestructura en un VPS con Coolify detras de Cloudflare: hostear proyectos, migrar soluciones desde Vercel/Railway/Render a un servidor propio, configurar firewall, certificados, bases de datos, backups inmutables y snapshots, todo con la seguridad y la simplicidad de la arquitectura probada de SinergIA. Activar cuando el usuario menciona hostear en mi servidor, levantar un VPS, instalar Coolify, configurar Cloudflare, migrar de Vercel/Railway a un VPS, hacer backup de mi base de datos, asegurar mi servidor, desplegar con Docker, o quiere dejar de depender de proveedores externos."
allowed-tools: Read, Write, Edit, Bash, Grep, Glob, WebSearch, WebFetch
effort: high
---

# Skill: infra-vps — tu infraestructura, en tu servidor

> Da al agente el conocimiento DevOps para replicar la arquitectura probada del negocio —Cloudflare al frente + un VPS con Coolify orquestando Docker + bases de datos self-hosted + backups inmutables— en el servidor del alumno. Hospeda sus proyectos, migra lo que hoy vive en otro proveedor, y opera todo el ciclo de vida sin depender de nadie.
>
> Esta skill cumple el [STYLE-GUIDE de skills Praxis](../STYLE-GUIDE.md) (Skills 2.0 spec + voz canonica + bundle structure). Leer antes de modificar.

`infra-vps` no es una receta rigida: es la **capa de conocimiento de dominio** sobre infraestructura. La experticia (Coolify, Cloudflare, hardening, migraciones, backups) vive en esta skill y su bundle `references/`. La forma de trabajar —planear y ejecutar— la gobierna la metodologia recursiva de Praxis. Esa division es deliberada: el conocimiento se actualiza aqui; el rigor de ejecucion lo aportan `prp` y `bucle-agentico`.

La arquitectura de referencia es una decision de ingenieria rentable y robusta: un VPS propio corre cualquier cosa sin limites serverless ni lock-in, con costo plano; Coolify le da experiencia tipo plataforma (push y redeploy con rolling-update) sobre hardware propio; Cloudflare pone red y seguridad de clase mundial gratis delante de todo (oculta la IP real, filtra ataques, aplica reglas e identidad, sirve HTTPS estricto). El detalle del por que esta en `references/arquitectura-referencia.md`.

---

## Como se apalanca en la metodologia Praxis

Antes de tocar un servidor, decide la **altitud** del trabajo. Razon: la infraestructura es de las pocas areas donde un paso mal dado tira un negocio entero; el rigor por fases existe justo para eso.

- **Trabajo trivial** — una sola accion reversible y acotada (ver logs de un contenedor, forzar un redeploy, emitir un certificado, listar registros DNS). Ejecutalo directo leyendo el `references/` pertinente. No montes un PRP para algo de un paso.
- **Trabajo no trivial** — provision inicial de la arquitectura, una migracion desde otro proveedor, un cambio que toca varios componentes (DNS + proxy + contenedor + base de datos), o cualquier accion con riesgo de perdida de datos. **Encadena el pipeline recursivo de Praxis**:
  1. **Planear con `prp`** (`@.claude/skills/prp/SKILL.md`): genera un PRP de infraestructura por fases. La experticia de esta skill alimenta el contexto y la arquitectura propuesta del PRP (que servidor, que orden, que riesgos, que rollback).
  2. **Ejecutar con `bucle-agentico`** (`@.claude/skills/bucle-agentico/SKILL.md`): corre el PRP fase por fase, con mapeo de contexto real antes de cada fase, Auto-Refuerzo cuando algo falla, y propagacion de aprendizajes al cerrar.

Asi una migracion se trata con el mismo cuidado que cualquier feature de software: mapear → planear este nivel → ejecutar → documentar → propagar. Esta skill **no reimplementa** esa doctrina: la referencia y la encadena. El conocimiento de infraestructura es suyo; el planear/ejecutar es de las skills canonicas.

Ejemplo de hand-off: el alumno dice "migra mi app de Vercel a mi VPS". No improvises el cutover. Lee `references/migraciones.md` para el contexto de dominio, luego invoca `prp` para planear las fases (inventario → dockerizar → Coolify + Cloudflare → datos → DNS cutover → validacion + rollback), y `bucle-agentico` para ejecutarlas.

---

## Cuando activar

- "Quiero hostear mi proyecto en mi propio servidor / VPS."
- "Migra esto de Vercel / Railway / Render a un VPS propio."
- "Levanta Coolify con Cloudflare como el de Juan / como SinergIA."
- "Asegura mi servidor / configura el firewall / oculta la IP."
- "Haz backup de mi base de datos / configura snapshots / restaura el servidor."
- "Quiero dejar de pagar por request y tener costo plano."
- "Despliega mi app con Docker / configura un dominio con HTTPS."

## Cuando NO activar

- **El alumno quiere construir una feature de su app**, no infraestructura: eso es `brief` → `prp` → `bucle-agentico`.
- **Operaciones de base de datos a nivel aplicacion** (crear tablas, RLS, queries de una app Next.js sobre Supabase): eso es `@.claude/skills/supabase-admin/SKILL.md`. Esta skill cubre la BD como pieza de **infraestructura** (self-hosted, backups, restore), no el modelado de datos de la app.
- **Despliegue trivial en una plataforma gestionada** que el alumno ya quiere conservar (un `vercel deploy` puntual): no fuerces una migracion que nadie pidio.

## Antes de empezar — verifica empiricamente

Por la doctrina Praxis (investigar antes de preguntar), averigua todo lo que puedas antes de preguntar nada. Solo escala al alumno por una credencial que solo el puede dar (paso de escalacion abajo).

- [ ] **Que se quiere lograr**: hostear algo nuevo, migrar algo existente, o operar algo ya montado. Define el escenario del flujo principal.
- [ ] **Que servidor hay a la mano**: ¿un VPS alquilado (que proveedor) o una maquina local? Si hay acceso SSH, conectate y mapea el estado real (`uname -a`, `docker ps`, `df -h`, que ya corre). No asumas un servidor vacio.
- [ ] **Dominio y DNS**: ¿el alumno tiene un dominio en Cloudflare? Sin el, hay una variante degradada documentada (acceso por IP con TLS propio) — anunciala con sus limites.
- [ ] **Que se va a hostear o migrar**: lee el proyecto (manifests, Dockerfile si existe, variables de entorno por nombre). Si viene de otro proveedor, inventaria que vive alla (ver `references/migraciones.md`).
- [ ] **Credenciales necesarias**: token de API del proveedor de VPS, llave SSH, tokens de Cloudflare. Lo que no exista y solo el alumno pueda generar es el unico motivo legitimo para escalar (abajo).
- [ ] **Proveedor o herramienta no cubierta**: si el proveedor de VPS o una herramienta concreta no esta en el bundle, **investiga su panel/API en la web antes de actuar** (ver "Protocolo de investigacion").

---

## Flujo principal

Identifica el escenario y entra a su receta. Para todo lo no trivial, recuerda la regla de altitud: planea con `prp` y ejecuta con `bucle-agentico`; las recetas de abajo son el conocimiento de dominio que alimenta ese plan, no un sustituto del plan.

### Escenario A: Provision inicial de la arquitectura

Levantar la arquitectura completa en un servidor nuevo: Coolify sobre el VPS, Cloudflare delante, hardening del origen, y la primera app o base de datos encima.

1. Mapea el servidor real por SSH y confirma recursos minimos (ver `references/proveedores-vps.md`).
2. Instala Coolify y deja el proxy corriendo (`references/coolify.md`).
3. Pon Cloudflare delante: DNS proxied, SSL estricto con Origin CA, e identidad delante de los paneles (`references/cloudflare.md`).
4. Aplica el lockdown del origen — el paso de seguridad que mas se olvida y mas importa (`references/seguridad-vps.md`).
5. Hospeda la primera app o base de datos (Escenario B / `references/bases-de-datos-backups.md`).
6. Configura backups y snapshots desde el dia uno (`references/bases-de-datos-backups.md`).

### Escenario B: Hostear una app nueva en el VPS

Una app del alumno (web, API, servicio) corriendo en Coolify, servida por un dominio con HTTPS.

Receta resumida (detalle y llamadas exactas en `references/coolify.md`): crear proyecto → preparar deploy key si el repo es privado → crear la app (build pack Dockerfile, puerto interno, watch paths) → sembrar TODAS las variables de entorno antes del primer build → asignar el dominio → emitir el certificado Origin CA y crear el registro DNS proxied → desplegar → verificar contra el servidor vivo (HTTP del endpoint, `docker ps`, certificado).

Si la app construye URLs absolutas publicas (OAuth, magic-link), revisa la leccion de "app detras de proxy" en `references/migraciones.md` — el proxy no reenvia las cabeceras de host por defecto y el login puede aterrizar en una URL interna.

### Escenario C: Migrar desde otro proveedor

Mover una solucion que hoy vive en Vercel / Railway / Render / Netlify / otro VPS a un servidor propio. Esto es **siempre no trivial**: planea con `prp` y ejecuta con `bucle-agentico`. El conocimiento de dominio (inventario, dockerizar, cutover de DNS sin downtime, plan de rollback, validacion E2E) esta en `references/migraciones.md`.

Regla dura: nunca un cutover sin backup previo verificado de los datos + un plan de rollback escrito (revertir el registro DNS al destino viejo) + validacion E2E antes de apagar el origen viejo.

### Escenario D: Operar lo ya montado

Deploys, logs, certificados nuevos, reglas de firewall, mantenimiento de disco, backups, snapshots, restauraciones. Las recetas de rutina estan en `references/runbooks.md`. Las acciones destructivas (restaurar un snapshot, podar volumenes, dropear datos) pasan por el gate de confirmacion (abajo).

---

## Modelo de seguridad por defecto (no es opcional)

Aplica estas reglas en cada escenario. Son las que separan una infra robusta de una vulnerable, y todas vienen de la arquitectura probada. El detalle operativo esta en `references/cloudflare.md` y `references/seguridad-vps.md`.

- **La IP real del origen nunca se expone.** Todo el trafico entra por Cloudflare (DNS proxied). El servidor solo acepta 80/443 desde los rangos de Cloudflare; el resto se descarta.
- **Lockdown del origen a nivel del firewall de contenedores.** Docker se salta el firewall del host (UFW), asi que el control vive en la cadena de contenedores, persistida para sobrevivir reinicios. Sin esto, los puertos de los contenedores quedan abiertos a internet aunque UFW diga lo contrario — es el error silencioso mas peligroso.
- **HTTPS estricto con Origin CA.** Bajo Cloudflare proxied, los certificados automaticos por desafio HTTP no funcionan; se usan certificados Origin CA instalados en el proxy. El modo SSL de la zona es estricto, nunca flexible (causa bucles de redireccion).
- **Identidad delante de los paneles.** Los paneles de administracion (Coolify, base de datos, automatizaciones) van detras de un control de identidad, no expuestos por IP. Los puertos de administracion se cierran a internet y se alcanzan por tunel SSH.
- **La base de datos escucha solo en loopback.** Postgres y el pooler nunca se exponen a internet; el acceso externo es solo via el gateway de la API con su capa de auth.
- **Secretos fuera de git, siempre.** Las credenciales viven en el secret manager de Coolify o en un archivo fuera del repo con permisos restringidos. Jamas en el codigo, jamas en el archivo de memoria, jamas en un commit. En cualquier doc se referencian por nombre, nunca por valor.
- **Backups inmutables off-site.** Los respaldos van a almacenamiento de objetos con bloqueo de inmutabilidad por una ventana de retencion, de modo que ni el agente ni una credencial robada puedan borrarlos.

## Gate de confirmacion para acciones destructivas

Antes de cualquier accion irreversible —restaurar un snapshot (sobreescribe TODO el servidor), podar volumenes (borra bases de datos), dropear datos, borrar un registro DNS de produccion— **detente, explica al alumno en su voz que se va a perder y que no se puede deshacer, y pide confirmacion explicita**. Razon: estas operaciones no tienen "ctrl-Z"; el costo de un falso positivo es catastrofico y el de preguntar es de segundos.

La lista de "que nunca tocar sin orden explicita" (volumenes de datos, certificados por defecto del proxy, servidores fuera de scope, registros de email) esta en `references/seguridad-vps.md`.

---

## Adaptabilidad: el alumno no usa exactamente lo mismo que nosotros

La arquitectura es la misma; las piezas concretas varian. Adapta sin asumir:

- **Proveedor de VPS distinto** (Hostinger, Contabo, Hetzner, DigitalOcean, OVH, u otro): lo que cambia es la API de snapshots, el panel y a veces la red. Variantes y que difiere en `references/proveedores-vps.md`.
- **Maquina local en vez de VPS alquilado**: Coolify tambien corre local. Sin IP publica ni DNS, el acceso es por LAN o tunel, y se pierden algunas garantias (IP oculta, certificados publicos). Documentado en `references/proveedores-vps.md`.
- **CLI agentico distinto a Claude Code** (Codex u otro corriendo como daemon en el servidor): el patron de daemon es agnostico del proveedor. Lo especifico de cada CLI se investiga, no se asume. Ver `references/daemon-agentico.md`.

## Protocolo de investigacion (parte del contrato, no un anexo)

Cuando el proveedor, la herramienta o la version concreta no esten cubiertos en el bundle, **investiga antes de actuar** — nunca asumas una API o un flag de memoria:

1. `WebSearch` + `WebFetch` la documentacion oficial del proveedor/herramienta, anclando por version cuando importe.
2. Verifica empiricamente contra el servidor vivo: prueba un comando de solo lectura (`curl` a la API, `docker ps`, un `GET`) antes de uno que cambie estado.
3. Si la API difiere de lo que esperabas, ajusta y vuelve a verificar. Documenta el hallazgo (en el PRP si estas en un flujo planeado) para que no se repita la busqueda.

Razon: cada proveedor nombra distinto sus endpoints (un snapshot puede ser `/snapshot`, `/backup`, o `/images`). Adivinar rompe; investigar y verificar es el camino corto.

---

## Cross-references con skills hermanas

- `@.claude/skills/prp/SKILL.md` — **planear** todo trabajo de infra no trivial como un PRP por fases, alimentado por la experticia de esta skill.
- `@.claude/skills/bucle-agentico/SKILL.md` — **ejecutar** ese PRP fase por fase con mapeo de contexto, Auto-Refuerzo y propagacion de aprendizajes.
- `@.claude/skills/supabase-admin/SKILL.md` — cuando el trabajo baja del nivel de infraestructura al de datos de la app (tablas, RLS, queries) sobre la base de datos ya montada.
- `@.claude/skills/praxis-init/SKILL.md` — si el alumno quiere migrar un proyecto que el agente no conoce, correr `praxis-init` primero da el contexto real del codebase antes de planear la migracion.

## Archivos lazy-loaded

- `references/arquitectura-referencia.md` — la arquitectura probada y el por que de cada capa (Cloudflare + VPS + Coolify). Leer al planear una provision o una migracion.
- `references/coolify.md` — instalar Coolify, su API, crear proyectos/apps/servicios, deploy keys, watch paths, variables, dominios, rolling deploy, mantenimiento de disco, y la receta de hostear una app. Leer en Escenarios A y B.
- `references/cloudflare.md` — DNS proxied, SSL estricto + Origin CA, reglas de firewall + rate-limit, identidad (Zero Trust), almacenamiento de objetos inmutable, Workers. Leer en Escenario A y para todo lo de red/seguridad de borde.
- `references/seguridad-vps.md` — lockdown del origen, fail2ban, SSH, base de datos en loopback, secretos fuera de git, gate de confirmacion, "que nunca tocar". Leer en Escenario A y antes de cualquier accion destructiva.
- `references/bases-de-datos-backups.md` — base de datos self-hosted, backup nocturno, almacenamiento inmutable, restore drill, snapshot del servidor y como restaurar. Leer en Escenarios A y D.
- `references/migraciones.md` — migrar desde Vercel/Railway/Render/Netlify/otro VPS: inventario, dockerizar, cutover de DNS sin downtime, rollback, validacion E2E, leccion de app detras de proxy. Leer en Escenario C.
- `references/daemon-agentico.md` — correr un CLI agentico como contenedor daemon: imagen, volumenes persistentes de credenciales/estado, salud, modelo de seguridad de 3 capas, contrato HTTP. Leer si el alumno quiere su propio agente operando el servidor.
- `references/proveedores-vps.md` — variantes de proveedor (Hostinger/Contabo/Hetzner/DigitalOcean/OVH/local), que difiere en cada uno, y como investigar uno desconocido. Leer al mapear el servidor.
- `references/runbooks.md` — operaciones de rutina: deploy/redeploy, logs, DNS, certificados, exenciones de firewall, disco, troubleshooting. Leer en Escenario D.

## Validacion al cerrar

- [ ] Lo que se monto responde de verdad: el endpoint da el HTTP esperado via Cloudflare, los contenedores estan `Up`, el certificado es valido.
- [ ] La IP del origen no es alcanzable directo (solo via Cloudflare) — verificado, no asumido.
- [ ] Los paneles de administracion estan detras de identidad y sus puertos cerrados a internet.
- [ ] Hay un backup verificado y, si aplica, un snapshot del servidor.
- [ ] Ningun secreto quedo en git, en el codigo, ni en el archivo de memoria.
- [ ] Si fue un trabajo no trivial, quedo planeado en un PRP y ejecutado por fases, con aprendizajes documentados.
