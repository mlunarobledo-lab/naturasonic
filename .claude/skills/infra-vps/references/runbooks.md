# Runbooks — operaciones de rutina

> Recetas puntuales para operar lo ya montado. Las acciones de un solo paso y reversibles son "triviales" (ejecutar directo); las destructivas pasan por el gate de confirmacion. Generalizado con placeholders.

## Contenido
- [Deploy / redeploy](#deploy--redeploy)
- [Logs y estado de un contenedor](#logs-y-estado)
- [Gestionar DNS](#gestionar-dns)
- [Emitir e instalar un certificado](#emitir-certificado)
- [Exentar un path en el firewall](#exentar-path)
- [Mantenimiento de disco](#mantenimiento-de-disco)
- [Troubleshooting rapido](#troubleshooting)

## Deploy / redeploy

- Automatico: `git push` que toque los watch paths → webhook → rebuild con rolling-update.
- Manual: `GET /api/v1/deploy?uuid=<app_uuid>` (via tunel a la API de Coolify).
- Monitorear: `GET /api/v1/deployments/<deployment_uuid>` → `status`.

## Logs y estado

```bash
ssh root@<VPS_IP> "docker ps --format '{{.Names}} {{.Status}}'"
ssh root@<VPS_IP> "docker logs --tail 100 <container-name>"
ssh root@<VPS_IP> "docker stats --no-stream <container-name>"
```

## Gestionar DNS

```bash
Z=$CLOUDFLARE_ZONE_ID; T=$CLOUDFLARE_API_TOKEN
# listar:  GET  /zones/$Z/dns_records?name=<sub>.<tu-dominio>
# crear:   POST /zones/$Z/dns_records {"type":"A","name":"<sub>","content":"<VPS_IP>","proxied":true,"ttl":1}
# editar:  PUT  /zones/$Z/dns_records/<id> {...}
```

Apuntar al VPS = `A` → `<VPS_IP>`, proxied. Email y otros hosts quedan DNS-only.

## Emitir certificado

Para un dominio nuevo bajo SSL estricto, emite un Origin CA e instalalo como certificado **adicional** en el proxy (sin tocar el por defecto). Receta completa en `cloudflare.md`.

## Exentar path

Para que un webhook publico o una API pasen el firewall/identidad, edita la regla con un `not starts_with(http.request.uri.path, "/<path>/")`, manteniendo el resto protegido. Detalle en `cloudflare.md`.

## Mantenimiento de disco

```bash
docker builder prune -af --filter until=168h   # cache de build > 7 dias
```

Nunca `docker system prune --volumes` ni `docker volume prune`. Solo cache de build e imagenes sin usar.

## Troubleshooting

| Sintoma | Causa probable | Accion |
|---|---|---|
| 526 / error de TLS | SSL no estricto o falta Origin CA | poner SSL estricto + instalar Origin CA |
| Bucle de redireccion | SSL en modo flexible | cambiar a estricto |
| Puerto abierto que no deberia | falta el lockdown del origen (Docker se salto UFW) | aplicar reglas en la cadena de contenedores |
| El endpoint responde directo por IP | el lockdown no esta puesto | reaplicar firewall del origen |
| Login aterriza en URL interna | el proxy no reenvia cabeceras de host | usar `x-forwarded-*` con fallback (ver `migraciones.md`) |
| Auto-deploy no dispara | path del webhook no exento en identidad | exentar `/webhooks/` |
| Build falla por OOM | RAM insuficiente en el build | añadir swap; bajar concurrencia de builds |
| El alumno no entra a un panel | puerto de admin cerrado (correcto) | acceder por tunel SSH o identidad, no por IP |

Para cualquier error no listado, sigue el protocolo de Auto-Refuerzo del `bucle-agentico` (leer el error completo, grep el codebase, MCPs, WebSearch, iterar) y documenta el aprendizaje.
