# Arquitectura de referencia — por que VPS + Coolify + Cloudflare

> La arquitectura probada que esta skill replica. Generalizada: usa placeholders (`<VPS_IP>`, `<tu-dominio>`), nunca valores reales de una infra concreta.

## Contenido
- [La idea en una frase](#la-idea-en-una-frase)
- [Las capas](#las-capas)
- [Topologia](#topologia)
- [Por que esta combinacion](#por-que-esta-combinacion)
- [Que vive donde](#que-vive-donde)

## La idea en una frase

Casi todo el negocio corre en **un solo VPS** gestionado por **Coolify** (contenedores Docker) y protegido por **Cloudflare** al frente. Lo unico que conviene dejar fuera del VPS es lo que de verdad gana en una plataforma gestionada (p. ej. una landing estatica con CDN gratis).

## Las capas

- **Cloudflare = la muralla y la porteria.** Todo el trafico entra aqui primero: decide quien pasa, frena ataques, pone el candado HTTPS y **esconde la IP real** del servidor.
- **VPS + Coolify = el edificio propio.** Un servidor Linux alquilado (o local) donde corren, en contenedores separados, las apps, las bases de datos self-hosted y las automatizaciones. Coolify es el conserje: despliega, reinicia y vigila cada contenedor.
- **Almacenamiento de objetos (S3) = la boveda off-site.** Los backups inmutables viven fuera del servidor, para sobrevivir incluso a la perdida del VPS.
- **Servicios externos = los proveedores.** APIs de IA, email, pagos. La luz y el agua del negocio.

## Topologia

```
                 Internet
                    |
        +-----------v-----------+   DNS proxied · WAF · identidad
        |      Cloudflare       |   SSL estricto · IP del VPS oculta
        +-----------+-----------+
                    | solo 80/443, solo desde rangos de Cloudflare
        +-----------v---------------------------------------+
        |  VPS  <VPS_IP>  ·  Coolify + proxy (Traefik)      |
        |    +- <tu-dominio>          -> app web            |
        |    +- api.<tu-dominio>      -> API / servicio     |
        |    +- panel.<tu-dominio>    -> panel (tras identidad)
        |    Base de datos self-hosted: Postgres solo 127.0.0.1
        +---------------------+-----------------------------+
                  | backups off-site (S3, inmutables)
                  v
            Almacenamiento de objetos
```

## Por que esta combinacion

1. **Control total, cero lock-in.** Un VPS corre cualquier cosa sin pedir permiso a una plataforma ni chocar con limites serverless.
2. **Costo plano y predecible.** Una renta fija en lugar de factura por request, y consolida en una maquina lo que estaba disperso.
3. **Coolify = experiencia tipo plataforma sobre hardware propio.** Push y la app se redespliega sola con rolling-update sin downtime, con dashboard, logs, secretos, certificados y healthchecks.
4. **Cloudflare = red y seguridad de clase mundial, gratis, delante de todo.** Oculta la IP, filtra ataques, aplica reglas e identidad, sirve HTTPS estricto con Origin CA.
5. **Soberania del dato.** La base de datos vive en el propio servidor (solo loopback), con backups cifrados e inmutables off-site.
6. **Rendimiento por co-ubicacion.** App y base de datos en el mismo host, misma red Docker, latencia minima.

El unico costo real es la responsabilidad de administrarlo — y para eso existe esta skill + el pipeline recursivo de Praxis.

## Que vive donde

| Pieza | Donde | Por que |
|---|---|---|
| Apps web / APIs / servicios | VPS (contenedores Coolify) | control + costo plano |
| Base de datos de produccion | VPS (self-hosted, solo loopback) | soberania del dato |
| Backups | Almacenamiento de objetos off-site | sobrevive a la perdida del VPS |
| Paneles de administracion | VPS, detras de identidad | nunca expuestos por IP |
| Landing estatica (opcional) | Plataforma gestionada con CDN | gana poco corriendo en el VPS |
| Secretos | Secret manager de Coolify / archivo fuera de git | nunca en el codigo |
