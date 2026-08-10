# Cloudflare — red, TLS, firewall e identidad

> Cloudflare va delante de todo el VPS. Generalizado con placeholders. Verifica endpoints contra la doc oficial antes de asumir.

## Contenido
- [DNS proxied (oculta la IP)](#dns-proxied)
- [SSL estricto + Origin CA](#ssl-estricto--origin-ca)
- [Firewall (WAF) y rate-limit](#firewall-waf-y-rate-limit)
- [Identidad delante de paneles (Zero Trust)](#identidad-zero-trust)
- [Almacenamiento de objetos inmutable](#almacenamiento-de-objetos-inmutable)
- [Workers (opcional)](#workers-opcional)

## DNS proxied

Apuntar un subdominio al VPS = registro `A` → `<VPS_IP>` con **proxy activado** (nube naranja). Con el proxy activo, Cloudflare termina el TLS, filtra el trafico y **oculta la IP real**.

```bash
# crear:  POST /zones/$ZONE_ID/dns_records {"type":"A","name":"<sub>","content":"<VPS_IP>","proxied":true,"ttl":1}
# listar: GET  /zones/$ZONE_ID/dns_records?name=<sub>.<tu-dominio>
# editar: PUT  /zones/$ZONE_ID/dns_records/<record_id> {...}
```

Los registros de email (MX/SPF/DKIM) quedan DNS-only — no se proxean.

## SSL estricto + Origin CA

- El modo SSL de la zona debe ser **estricto** (Full Strict), nunca flexible (flexible causa bucles de redireccion).
- Bajo proxy, el desafio HTTP de los certificados automaticos **no funciona** y el lockdown del origen lo bloquea. Solucion: certificados **Origin CA** de Cloudflare instalados en el proxy del VPS (validez ~15 años).

```bash
openssl req -new -newkey rsa:2048 -nodes -keyout /tmp/o.key -out /tmp/o.csr -subj "/CN=<sub>.<tu-dominio>"
# POST /certificates {"hostnames":["<sub>.<tu-dominio>"],"requested_validity":5475,"request_type":"origin-rsa","csr":"<CSR>"}
# -> cert PEM. Copialo + la key al directorio dinamico del proxy del VPS,
#    con un archivo de config que agregue el cert como ADICIONAL (no toques el cert por defecto).
```

## Firewall (WAF) y rate-limit

- Reglas custom en el ruleset de firewall de la zona. Util: rate-limit sobre rutas de login (p. ej. N req / ventana / IP).
- Para exentar un path publico (webhooks de auto-deploy, APIs publicas), edita la expresion de la regla con un `not starts_with(http.request.uri.path, "/webhooks/")`, manteniendo el resto protegido.

## Identidad (Zero Trust)

Pon los paneles de administracion (Coolify, base de datos, automatizaciones) detras de **identidad**, no expuestos por IP:

- Crea una aplicacion Access por hostname con politica "allow solo el owner" (IdP simple: codigo por email).
- **Exenta los paths que deben ser publicos**: las APIs que tus apps consumen con su propia auth, los webhooks de auto-deploy, los healthchecks.
- Para integraciones server-to-server (una app que llama a un daemon), usa un **service token** dedicado por integracion (revocable independiente).
- Defensa en profundidad: valida el JWT de Access tambien en el origen, no solo en el borde.

Rollback de identidad: re-habilitar reglas de firewall por IP + desactivar el enforce en el origen. El rescate del panel es siempre el tunel SSH (el puerto de admin esta cerrado por el firewall de contenedores, no por identidad).

## Almacenamiento de objetos inmutable

Para backups off-site, usa el almacenamiento de objetos compatible con S3 de Cloudflare con **bloqueo de inmutabilidad** por una ventana de retencion (p. ej. 30 dias):

- Crea el bucket de backups.
- Aplica una regla de object-lock por `Age` = ventana de retencion.
- Verifica empiricamente que el lock vive: intenta borrar un objeto reciente → debe fallar con error de objeto bloqueado.

Asi ni el agente ni una credencial robada pueden borrar los backups recientes. El cron de backup solo hace `copy` (append-only), sin conflicto con el lock.

## Workers (opcional)

Para logica de borde (redirects, edge functions, dominios custom delegados), Cloudflare Workers con Custom Domain. No es parte del nucleo de la arquitectura; usalo solo si una pieza concreta lo necesita.
