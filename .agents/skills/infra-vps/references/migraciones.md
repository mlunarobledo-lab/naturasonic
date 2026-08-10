# Migraciones — de otro proveedor a tu VPS

> Mover una solucion desde Vercel / Railway / Render / Netlify / otro VPS al servidor propio. Siempre no trivial: planea con `prp` y ejecuta con `bucle-agentico`. Este doc es el conocimiento de dominio que alimenta ese plan.

## Contenido
- [Fase 1: inventario del origen](#fase-1-inventario)
- [Fase 2: dockerizar](#fase-2-dockerizar)
- [Fase 3: montar en Coolify + Cloudflare](#fase-3-montar)
- [Fase 4: migrar los datos](#fase-4-datos)
- [Fase 5: cutover de DNS sin downtime](#fase-5-cutover)
- [Fase 6: validacion E2E + apagar el origen](#fase-6-validacion)
- [Leccion: app detras de proxy](#leccion-app-detras-de-proxy)

## Fase 1: inventario

Mapea exhaustivamente que vive en el origen antes de mover nada:

- **Runtime y build**: lenguaje, framework, comando de build, comando de arranque, version de runtime.
- **Variables de entorno**: todas, **por nombre** (los valores los aporta el alumno o se copian del panel del origen — nunca a git).
- **Datos**: ¿base de datos gestionada del proveedor? ¿almacenamiento de archivos? ¿colas? Cada uno necesita su plan de migracion.
- **Dominios**: que hostnames sirve y donde apunta hoy el DNS.
- **Servicios atados**: cron jobs, webhooks entrantes, integraciones externas con URLs que habra que reapuntar.

Si el agente no conoce el codebase, corre `@.agents/skills/praxis-init/SKILL.md` primero.

## Fase 2: dockerizar

Si el proyecto no tiene Dockerfile, escribe uno (multi-stage, imagen slim, usuario no-root cuando aplique). Coolify construye desde el Dockerfile. Para frameworks con modo "standalone" (p. ej. Next), activa esa salida para una imagen minima. Verifica el build localmente antes de subir.

## Fase 3: montar

Sigue la receta "hostear una app nueva" de `coolify.md`: proyecto → deploy key → app → variables (todas, antes del primer build) → dominio → certificado Origin CA → DNS proxied → deploy. **Aun no apuntes el dominio de produccion** — usa un subdominio de staging para validar primero.

## Fase 4: datos

- **Base de datos**: dump del origen → restaura en la BD self-hosted del VPS → verifica conteos de tablas/filas. Para datos que cambian en vivo, planea una ventana o una replica final justo antes del cutover.
- **Archivos**: sincroniza el almacenamiento de objetos del origen al destino (`rclone sync`).
- Verifica integridad antes de seguir (un restore drill cuenta como ensayo — ver `bases-de-datos-backups.md`).

## Fase 5: cutover

Con staging validado, cambia el DNS de produccion: registro `A` → `<VPS_IP>`, proxied (ver `cloudflare.md`). El TTL bajo (proxied = TTL 1/auto) hace la propagacion casi inmediata.

**Antes del cutover, ten escrito el rollback**: el valor anterior del registro DNS (el destino viejo) para revertir en segundos si algo falla. No apagues el origen viejo todavia — es tu red de seguridad.

## Fase 6: validacion

Valida el flujo completo end-to-end contra produccion: carga la app, haz login real, prueba el camino critico (pago, formulario, lo que sea el corazon del negocio), revisa que los webhooks entrantes lleguen. Solo cuando todo pase, desconecta el auto-deploy del origen viejo y, mas tarde, apagalo.

## Leccion: app detras de proxy

Una app que construye URLs absolutas publicas (OAuth, magic-link, callbacks) puede aterrizar en una URL interna (`http://0.0.0.0:3000`) tras el proxy, porque el proxy **no reenvia las cabeceras de host por defecto**. Fix: los handlers que arman URLs publicas deben usar las cabeceras `x-forwarded-host` / `x-forwarded-proto` con fallback a una variable de entorno de URL publica del sitio. Si el login redirige a una URL interna despues del cutover, esta es casi siempre la causa.
