# Seguridad del VPS — lockdown del origen y reglas de oro

> El conjunto de defensas del servidor. La mas importante —y la que mas se olvida— es el lockdown del origen. Generalizado con placeholders.

## Contenido
- [Lockdown del origen (la defensa real)](#lockdown-del-origen)
- [Base de datos en loopback](#base-de-datos-en-loopback)
- [fail2ban + SSH](#fail2ban--ssh)
- [Secretos fuera de git](#secretos-fuera-de-git)
- [Gate de confirmacion para acciones destructivas](#gate-de-confirmacion)
- [Que nunca tocar](#que-nunca-tocar)

## Lockdown del origen

**Docker se salta el firewall del host (UFW).** Por eso, aunque UFW diga que un puerto esta cerrado, los puertos de los contenedores pueden quedar abiertos a internet. El control real vive en la cadena de contenedores del firewall (`DOCKER-USER` en iptables), persistida para sobrevivir reinicios.

Reglas minimas:

- `80,443` → permitir **solo desde los rangos de IP de Cloudflare**; descartar el resto. (La lista de rangos esta publicada por Cloudflare; obtenla por su API/doc — no la hardcodees, cambia.)
- Puertos de administracion (panel de Coolify, etc.) → **descartar** desde internet. Solo accesibles por tunel SSH.
- Todo IPv6 externo a 80/443 → descartar.

Persistir las reglas (en Debian/Ubuntu, `netfilter-persistent` guarda en `/etc/iptables/rules.v{4,6}`). Si reconstruyes el VPS, **reaplicar**.

Verificacion empirica (no asumir):

```bash
# directo a la IP del origen -> debe fallar / colgar:
curl --resolve <tu-dominio>:443:<VPS_IP> https://<tu-dominio> -m 5
# via Cloudflare -> debe responder 200:
curl https://<tu-dominio> -m 5 -o /dev/null -w "%{http_code}\n"
```

## Base de datos en loopback

Postgres y el pooler escuchan **solo en `127.0.0.1`**, nunca en `0.0.0.0`. El acceso externo es unicamente via el gateway de la API (con su capa de auth). Verifica con `ss -tlnp | grep -E '5432|6543'` — deben estar en loopback.

## fail2ban + SSH

- `fail2ban` activo con el jail de SSH (banea IPs tras N intentos fallidos).
- SSH por llave publica. Endurece `PasswordAuthentication` y `PermitRootLogin` **solo cuando tengas una consola de rescate confirmada** — sin ella, el riesgo de quedarte fuera supera el beneficio. Documenta la decision.

## Secretos fuera de git

- Viven en el secret manager de Coolify o en un archivo fuera del repo con permisos restringidos (`chmod 600`).
- Nunca en el codigo, nunca en el archivo de memoria del agente, nunca en un commit.
- En cualquier doc o reporte, referencia los secretos **por nombre** (`$PROVIDER_TOKEN`), jamas por valor.
- Si un secreto se filtra a git, rotalo — borrar el commit no basta (queda en el historial y en clones).

## Gate de confirmacion

Antes de cualquier accion irreversible, **detente, explica que se pierde y pide confirmacion explicita**. Razon: no hay deshacer. Acciones que SIEMPRE pasan por el gate:

- Restaurar un snapshot (sobreescribe todo el servidor).
- `docker volume prune` / borrar un volumen (borra bases de datos / credenciales).
- `DROP` de tablas o bases de datos.
- Borrar un registro DNS de produccion o un certificado.
- Reinstalar el OS del VPS.

Si operas un daemon agentico, este gate se implementa como un clasificador que bloquea patrones destructivos y pide aprobacion antes de ejecutar (ver `daemon-agentico.md`).

## Que nunca tocar

- **Volumenes de datos** (base de datos, credenciales persistentes) — cero `prune --volumes`.
- **El certificado por defecto del proxy** — agrega certificados adicionales, no lo reemplaces.
- **Servidores fuera de scope** y sus registros DNS — otros hosts del alumno.
- **Registros de email** (MX/SPF/DKIM) y verificaciones de terceros — DNS-only, intactos.
- **El modo SSL de la zona** — es global; cambiarlo afecta a todos los subdominios.
