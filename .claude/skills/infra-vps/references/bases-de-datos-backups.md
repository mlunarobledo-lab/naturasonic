# Bases de datos, backups y snapshots

> La base de datos como pieza de infraestructura: self-hosted, respaldada e inmutable, con rollback de servidor entero. Generalizado con placeholders.

## Contenido
- [Base de datos self-hosted](#base-de-datos-self-hosted)
- [Backup nocturno -> almacenamiento inmutable](#backup-nocturno)
- [Restore drill (ensayar la restauracion)](#restore-drill)
- [Snapshot del servidor entero](#snapshot-del-servidor-entero)
- [Restaurar un snapshot](#restaurar-un-snapshot)

## Base de datos self-hosted

Para soberania del dato, la base de datos de produccion corre en el propio VPS como contenedor(es), con Postgres escuchando **solo en loopback** (ver `seguridad-vps.md`). El ingreso publico, si lo hay, pasa por el gateway de la API con su capa de auth.

Regla de oro: toda tabla tiene su politica de acceso (RLS owner-only) desde el dia uno. El modelado de tablas/RLS de la app es trabajo de `@.claude/skills/supabase-admin/SKILL.md`; aqui solo se cubre la BD como infraestructura.

## Backup nocturno

Un cron en el VPS hace un dump de la base de datos y lo copia al almacenamiento de objetos off-site (append-only). Patron:

```bash
# /opt/backups/backup-local.sh (cron nocturno)
ts=$(date -u +%Y%m%d-%H%M)
pg_dump -Fc -h 127.0.0.1 -U <db_user> <db_name> > /opt/backups/db_${ts}.dump
rclone copy /opt/backups/db_${ts}.dump <remote>:<bucket-de-backups>/   # solo copy, nunca delete
find /opt/backups -name 'db_*.dump' -mtime +14 -delete                 # retencion local corta
```

El bucket remoto tiene **object-lock por inmutabilidad** (ver `cloudflare.md`): los backups recientes no se pueden borrar ni por el agente ni por una credencial robada. El cron solo hace `copy`, asi que no choca con el lock.

> Nota: una base de datos **gestionada** (no self-hosted) la respalda su proveedor; este flujo es para la BD que vive en el VPS.

## Restore drill

Un backup que nunca se restauro no es un backup. Ensaya la restauracion contra una base de datos desechable, sin tocar produccion:

```bash
docker run -d --name pg-drill --tmpfs /var/lib/postgresql/data -e POSTGRES_PASSWORD=x postgres:<version>
# espera a que arranque, luego:
pg_restore --no-owner --no-privileges -h 127.0.0.1 -p <port> -U postgres -d postgres /opt/backups/db_<ts>.dump
# cuenta tablas / filas para confirmar integridad, luego limpia:
docker rm -f pg-drill
```

La version del Postgres de restauracion debe coincidir con la del dump. Documenta el RTO (cuanto tomo) — sirve para dimensionar incidentes.

## Snapshot del servidor entero

El backup de BD restaura **datos**; el snapshot restaura **el servidor completo** (OS + contenedores + config). Es el boton de regreso del host. La mayoria de proveedores de VPS lo ofrecen via API:

```bash
# crea (suele sobreescribir el snapshot anterior):
curl -s -X POST -H "Authorization: Bearer $PROVIDER_TOKEN" \
  https://<api-del-proveedor>/vps/<VPS_ID>/snapshot
# verifica (read-only):
curl -s -H "Authorization: Bearer $PROVIDER_TOKEN" \
  https://<api-del-proveedor>/vps/<VPS_ID>/snapshot
```

Hechos tipicos (verifica los del proveedor concreto — varian): se guarda **un solo** snapshot que el nuevo sobreescribe; **expira** a las pocas semanas (por eso un refresh semanal por cron); se borra si reinstalas el OS. El nombre exacto del endpoint cambia por proveedor — investiga (ver Protocolo de investigacion del SKILL.md). Automatiza solo la **creacion**; la restauracion nunca es automatica.

## Restaurar un snapshot

Accion destructiva — pasa por el gate de confirmacion. Advertencias tipicas: **sobreescribe TODO** (se pierde lo posterior al snapshot, incluidos datos nuevos), **no se detiene** una vez iniciada, y bloquea el server entre minutos y horas.

Checklist antes de restaurar:
1. ¿Amerita rollback de *todo el servidor*? Si es solo un dato, restaura de backup de BD, no snapshot.
2. ¿Cuando se tomo el snapshot? Vas a perder todo lo posterior.
3. ¿Hay datos criticos creados despues (pagos, miembros nuevos)? Respaldalos aparte primero.

Despues de restaurar: los contenedores con `restart: unless-stopped` suelen revivir solos; si falta el ultimo commit, fuerza un redeploy; reaplica el firewall del origen si el snapshot es anterior a haberlo configurado.
