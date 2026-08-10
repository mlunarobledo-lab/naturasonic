# Proveedores de VPS — variantes y como investigar uno desconocido

> La arquitectura es la misma en cualquier proveedor; cambian los detalles. Generalizado con placeholders.

## Contenido
- [Que comparten todos](#que-comparten-todos)
- [Que cambia entre proveedores](#que-cambia-entre-proveedores)
- [Variante: maquina local](#variante-maquina-local)
- [Como investigar un proveedor desconocido](#como-investigar-un-proveedor-desconocido)

## Que comparten todos

Cualquier VPS Linux decente sirve para esta arquitectura. Requisitos minimos comodos: 4 vCPU / 8-16 GB RAM, disco SSD con holgura (los builds dejan cache), y acceso root por SSH. Si vas a correr una base de datos self-hosted + varias apps + un daemon, no bajes de ~8 GB de RAM (añade swap para los builds pesados). Verifica el estado real por SSH antes de planear: `uname -a`, `nproc`, `free -h`, `df -h`, `docker ps`.

## Que cambia entre proveedores

| Aspecto | Varia asi |
|---|---|
| **API de snapshots** | El endpoint y su semantica difieren (un snapshot puede llamarse `snapshot`, `backup` o `image`). Investiga antes de automatizar. |
| **Panel y tokens** | Cada proveedor genera su token de API en un lugar distinto y con scopes propios. |
| **Red** | Algunos dan IP fija de datacenter (ideal — estable, reputada); otros rotan o ponen NAT. |
| **Firewall del proveedor** | Algunos tienen un firewall propio ademas del del host; configuralo o desactivalo para no duplicar reglas. |
| **Imagen base del OS** | Confirma la distro/version; los comandos de persistencia de firewall y de servicios varian (systemd vs otros). |

Proveedores comunes que sirven igual de bien: Hostinger, Contabo, Hetzner, DigitalOcean, OVH, Linode, Vultr, y otros. Ninguno esta "bendecido" — elige por precio/region/soporte; la skill se adapta.

## Variante: maquina local

Coolify tambien corre en una maquina local (un mini-PC, un servidor casero). Diferencias:

- **Sin IP publica fija**: no puedes hacer DNS → IP directo. Opciones: un tunel (expone el servicio local por un hostname publico sin abrir puertos) o acceso solo-LAN.
- **Se pierden garantias**: si usas tunel, la IP "oculta" la da el tunel; sin Cloudflare proxied delante pierdes el WAF y la identidad de borde a menos que los reconstruyas.
- **Bueno para**: desarrollo, herramientas internas, o un primer paso antes de alquilar un VPS. Documenta los limites al alumno en su voz.

## Como investigar un proveedor desconocido

Cuando el proveedor del alumno no este cubierto:

1. `WebSearch` la doc oficial de su API de VPS (snapshots, gestion de la maquina, tokens).
2. `WebFetch` la pagina concreta del endpoint que necesitas; confirma el metodo, la ruta y los campos.
3. Prueba primero un endpoint de **solo lectura** (listar maquinas, leer el snapshot actual) para validar el token y la forma de la respuesta.
4. Recien entonces ejecuta el endpoint que cambia estado, y verifica el resultado.

Nunca asumas que la API de un proveedor calca la de otro. Investigar + verificar empiricamente es mas rapido que depurar una llamada inventada.
