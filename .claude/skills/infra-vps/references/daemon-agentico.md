# Daemon agentico — tu agente operando el servidor

> Patron para correr un CLI agentico (Claude Code, Codex u otro) como contenedor daemon en el VPS, que opera la infraestructura 24/7. Agnostico del proveedor: lo especifico de cada CLI se investiga, no se asume.

## Contenido
- [Que es y cuando vale la pena](#que-es)
- [Imagen y runtime](#imagen-y-runtime)
- [Volumenes persistentes](#volumenes-persistentes)
- [Salud](#salud)
- [Modelo de seguridad de 3 capas](#modelo-de-seguridad-de-3-capas)
- [Contrato HTTP](#contrato-http)

## Que es

Un servicio de larga vida que envuelve un CLI agentico y expone una API HTTP. Otra app (un panel, un puente) le habla server-to-server y el agente ejecuta tareas: deploys, diagnosticos, backups, operacion. Vale la pena cuando el alumno quiere un operador autonomo de su infra, no solo asistencia interactiva.

## Imagen y runtime

Contenedor multi-stage sobre una base slim del runtime del CLI. Instala el CLI agentico con su version anclada (pin exacto — evita que un upgrade silencioso rompa el daemon). Si el CLI necesita permisos elevados para operar sin prompts dentro del contenedor, ejecutalo en modo no-interactivo controlado y aislado por el propio contenedor.

> El comando de instalacion, el flag de modo no-interactivo y el formato de credenciales **dependen del CLI concreto** — investiga la doc del proveedor (Protocolo de investigacion del SKILL.md). No asumas que Codex u otro se instalan o autentican igual que Claude Code.

## Volumenes persistentes

Dos volumenes que **nunca** se podan sin autorizacion expresa (perderlos = perder credenciales y estado):

- Volumen de **estado**: base de datos local del daemon (sesiones, locks, colas, uploads).
- Volumen de **credenciales**: el directorio donde el CLI guarda sus tokens (OAuth con refresh automatico). Re-autenticar el contenedor crea un linaje de refresh-token independiente — no mezcles copiar credenciales del host con re-autenticar dentro del contenedor, o la primera rotacion invalida una de las dos.

## Salud

Expon endpoints de salud sin auth para liveness, y endpoints con auth para el resto:

```
GET /healthz        -> { ok, uptime, pid }            (publico)
GET /healthz/auth   -> { ok, ... }                    (estado del login del CLI)
```

Si el login del CLI falla, el endpoint de auth lo refleja y dispara una alerta (por email u otro canal). Un rate-limiter (semaforo + token bucket) protege de saturar el modelo.

## Modelo de seguridad de 3 capas

Toda request (excepto `/healthz`) pasa tres capas:

| Capa | Que es | Como se pasa |
|---|---|---|
| 1. Identidad de borde | El hostname esta tras identidad (Zero Trust); solo entra con un service token valido | headers de service token |
| 1.5. Allowlist en el origen | El daemon valida que el identificador del token este en su lista blanca | automatico |
| 2. Bearer token | Secreto compartido comparado con `timingSafeEqual` | `Authorization: Bearer <token>` |

Los tres secretos son **server-side only** — nunca en el browser ni en variables publicas. La integracion es server-to-server: el browser habla con tu backend; tu backend (con los secretos) habla con el daemon.

Suma el **gate de confirmacion** (ver `seguridad-vps.md`): un clasificador bloquea patrones destructivos y pide aprobacion humana antes de ejecutarlos. Fail-safe: sin canal de aprobacion → deny.

## Contrato HTTP

Endpoint principal de chat por streaming (SSE): acumula los deltas de texto y usa el evento final como respuesta. Concurrencia tipica = 1 (serializa; una segunda query cancela la anterior). Multi-turno con un id de sesion estable por conversacion. Timeout duro por query.

> Si construyes un "puente" desde otra app, recuerda: lo que se converse queda registrado en el historial del operador (transparencia). No mandes nada que no quieras que aparezca ahi.
