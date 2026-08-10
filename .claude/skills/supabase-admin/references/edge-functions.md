# Edge Functions — crear, deploy, debug

Edge Functions son funciones Deno que corren en el edge (cerca del usuario). Sirven para webhook handlers, jobs programados, integraciones con APIs externas que requieren secret server-side.

## Crear funcion

```bash
supabase functions new mi-funcion
```

Genera `supabase/functions/mi-funcion/index.ts`:

```ts
import { serve } from 'https://deno.land/std@0.224.0/http/server.ts';

serve(async (req) => {
  const body = await req.json();
  return new Response(JSON.stringify({ ok: true, echo: body }), {
    headers: { 'content-type': 'application/json' },
  });
});
```

## Deploy

```bash
supabase functions deploy mi-funcion --no-verify-jwt
```

`--no-verify-jwt` solo si la funcion es publica (webhook entrante de tercero). Default verifica el JWT del Authorization header.

## Secrets

```bash
supabase secrets set STRIPE_WEBHOOK_SECRET=whsec_xxx
supabase secrets set OPENROUTER_API_KEY=sk-or-xxx
```

Leer en la funcion: `Deno.env.get('STRIPE_WEBHOOK_SECRET')`.

## Logs

```bash
supabase functions logs mi-funcion --follow
```

O via MCP: `mcp__claude_ai_Supabase__get_logs --service edge-function`.

## Trigger via pg_cron (jobs programados)

```sql
-- Habilitar extension una vez
create extension if not exists pg_cron;

-- Llamar la funcion cada 24h
select cron.schedule(
  'daily-cleanup',
  '0 3 * * *',
  $$
    select net.http_post(
      url := 'https://<project-ref>.supabase.co/functions/v1/cleanup-old-temp',
      headers := jsonb_build_object(
        'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key')
      )
    );
  $$
);
```

## Patron canonico de webhook handler

```ts
serve(async (req) => {
  const signature = req.headers.get('x-webhook-signature');
  const body = await req.text();

  // Valida firma timing-safe
  const expected = await hmacSha256(body, Deno.env.get('WEBHOOK_SECRET')!);
  if (!safeEqual(signature, expected)) {
    return new Response('invalid signature', { status: 401 });
  }

  const event = JSON.parse(body);
  // ... procesar
  return new Response('ok');
});
```

Cross-ref `@.claude/skills/payments-polar/SKILL.md` para el webhook canonico de Polar.

## Debug local

```bash
supabase functions serve mi-funcion --env-file .env.local
# Ahora corre en http://localhost:54321/functions/v1/mi-funcion
curl -X POST http://localhost:54321/functions/v1/mi-funcion -d '{"test":1}'
```
