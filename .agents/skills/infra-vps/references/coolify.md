# Coolify — instalar, operar y hostear apps

> Coolify es el orquestador tipo plataforma sobre el VPS. Generalizado con placeholders. Verifica la version actual de la API en la doc oficial antes de asumir un endpoint (ver "Protocolo de investigacion" del SKILL.md).

## Contenido
- [Instalacion](#instalacion)
- [Hablar con la API](#hablar-con-la-api)
- [Conceptos: proyecto, app, servicio](#conceptos)
- [Receta: hostear una app nueva](#receta-hostear-una-app-nueva)
- [Variables de entorno](#variables-de-entorno)
- [Watch paths (monorepo)](#watch-paths)
- [Deploy y rolling-update](#deploy-y-rolling-update)
- [Mantenimiento de disco](#mantenimiento-de-disco)

## Instalacion

En el VPS, como root, con Docker ya presente (el instalador lo pone si falta):

```bash
curl -fsSL https://cdn.coollabs.io/coolify/install.sh | bash
```

Deja corriendo el contenedor `coolify` + su proxy (Traefik) + su base de datos y cola internas. El dashboard queda en el puerto de administracion (cierralo a internet — ver `seguridad-vps.md` — y alcanzalo por tunel SSH o detras de identidad).

## Hablar con la API

El puerto de administracion no debe estar abierto a internet. Habla con la API por tunel SSH:

```bash
ssh -fN -L 18000:127.0.0.1:8000 root@<VPS_IP>
curl -s -H "Authorization: Bearer $COOLIFY_API_TOKEN" http://localhost:18000/api/v1/applications
```

`$COOLIFY_API_TOKEN` vive fuera de git (secret manager / archivo con permisos restringidos). Genera el token en el dashboard de Coolify.

## Conceptos

- **Proyecto**: agrupador logico. `POST /api/v1/projects {"name":"X","description":"..."}` (descripcion en ASCII basico; algunos caracteres especiales fallan).
- **App** (Build Pack = Dockerfile): un contenedor construido desde un repo Git. Es lo normal para apps propias.
- **Servicio** (Docker Compose): un stack multi-contenedor (p. ej. una herramienta de automatizacion). Coolify reporta su salud como `unknown` — es normal para compose; mira la salud real con `docker ps`.

## Receta: hostear una app nueva

Probada de punta a punta. Cada paso es una llamada a la API (verifica los nombres de campo contra la doc vigente):

1. **Proyecto** (o reusar): `POST /api/v1/projects` → `project_uuid`.
2. **Deploy key** (solo repo privado): `ssh-keygen -t ed25519 -f /tmp/k -N ''` → añade `/tmp/k.pub` como deploy key del repo en el host Git → registra la privada en Coolify (`POST /api/v1/security/keys`) → `private_key_uuid`.
3. **App**: `POST /api/v1/applications/private-deploy-key` con `{project_uuid, server_uuid, environment_name:"production", private_key_uuid, git_repository, git_branch:"main", build_pack:"dockerfile", ports_exposes:"3000", base_directory:"/", dockerfile_location:"/Dockerfile", watch_paths}` → `app_uuid`.
4. **Variables de entorno** (ver abajo) — **sembrar TODO antes del primer build**.
5. **Dominio**: `PATCH /api/v1/applications/{app_uuid} {"domains":"https://<tu-dominio>"}`.
6. **Certificado TLS** (`cloudflare.md` §Origin CA) + **DNS proxied** (`cloudflare.md` §DNS) + identidad/firewall si el endpoint debe protegerse.
7. **Deploy**: `GET /api/v1/deploy?uuid=<app_uuid>`.
8. **Verificar**: HTTP del endpoint via Cloudflare, `docker ps`, certificado.

La app expone su puerto interno (p. ej. 3000) **sin mapearlo al host** — el proxy enruta internamente por el `Host` header. Usa `restart: unless-stopped`.

## Variables de entorno

`PATCH /api/v1/applications/{uuid}/envs/bulk {"data":[{key,value,is_build_time,is_literal}]}`. Las variables publicas / build-args van con `is_build_time:true`. Siembra **todas** antes del primer build, o el build saldra incompleto. Nunca pongas secretos en variables publicas (build-time queda en el bundle del cliente).

## Watch paths

Semantica de Coolify: last-match-wins, `!` excluye, `**` cualquier profundidad, sin slash inicial. En un monorepo, cada app define que rutas disparan su build:

- App principal: `src/**` y excluye `landing/**` con `!landing/**`.
- Sub-app: `landing/**`.

Asi un push solo reconstruye la app cuyas rutas matchean.

## Deploy y rolling-update

- **Automatico**: `git push` que toque los watch paths → webhook → Coolify reconstruye con rolling-update gated por healthcheck (cero downtime). El path del webhook debe estar exento en la capa de identidad (ver `cloudflare.md`).
- **Manual**: `GET /api/v1/deploy?uuid=<app_uuid>` (ignora watch paths).
- **Monitorear**: `GET /api/v1/deployments/<deployment_uuid>` → `status` (queued/in_progress/finished/failed).

## Mantenimiento de disco

Cada build deja varios GB de cache. Poda semanal por cron:

```bash
docker builder prune -af --filter until=168h
```

Nunca `docker system prune --volumes` ni `docker volume prune` — borrarian las bases de datos y las credenciales persistentes. Solo cache de build e imagenes sin usar.
