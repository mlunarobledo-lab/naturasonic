---
name: supabase-admin
description: "Operaciones rapidas con Supabase desde Praxis: crear tabla con RLS, query CRUD, migrar schema, consultar metricas, leer logs. Skill-glue que conecta auth-stack, emails-transactional, payments-polar, pwa-mobile y ai-sdk-kit. Activar cuando el usuario menciona crear tabla, migracion, RLS, query, ver datos, metricas, churn, funnel, storage upload, edge function, logs Supabase, o pide 'cuantos registros tengo de X'."
allowed-tools: Read, Write, Edit, Bash, mcp__claude_ai_Supabase__apply_migration, mcp__claude_ai_Supabase__execute_sql, mcp__claude_ai_Supabase__list_tables, mcp__claude_ai_Supabase__get_advisors, mcp__claude_ai_Supabase__get_logs
---

# supabase-admin — glue skill para operaciones rapidas

> Indice + cheatsheet de 60 lineas. Lo grueso esta en `references/<topic>.md` y, sobre todo, en las skills hermanas.

---

## Cuando activar

- "Crea una tabla X con campo Y."
- "Cuantos usuarios tengo registrados?"
- "Que migraciones tengo aplicadas?"
- "Sube esta imagen a Storage."
- "Necesito una Edge Function que haga Z."
- "Revisa los logs del backend."

## Cuando NO activar (delega a la hermana)

- "Quiero login / signup / RLS para profiles." → `@.claude/skills/auth-stack/SKILL.md` ya trae el patron canonico.
- "Webhook de Polar guarda en BD." → `@.claude/skills/payments-polar/SKILL.md`.
- "Push subscription en BD." → `@.claude/skills/pwa-mobile/SKILL.md`.
- "Email log en BD." → `@.claude/skills/emails-transactional/SKILL.md`.
- "Vector embeddings + conversaciones." → `@.claude/skills/ai-sdk-kit/SKILL.md`.

## Cheatsheet — primera tabla con RLS (60 lineas)

Aplicar via `mcp__claude_ai_Supabase__apply_migration` con nombre `<NNNN>_<descripcion>`. RLS habilitada **antes del primer write**, sin excepciones — Supabase no la re-aplica retroactivamente sin migracion compleja.

```sql
-- Migracion: 0002_lessons.sql
create table public.lessons (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.profiles(id) on delete cascade,
  title text not null,
  content text,
  published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.lessons enable row level security;

-- Lectura: solo lessons publicadas, o las propias del creator
create policy "lessons_read" on public.lessons
  for select using (
    published or auth.uid() = creator_id
  );

-- Write: solo el creator de la leccion
create policy "lessons_write_own" on public.lessons
  for all using (auth.uid() = creator_id)
  with check (auth.uid() = creator_id);

create index lessons_creator_idx on public.lessons(creator_id);
create index lessons_published_idx on public.lessons(published) where published = true;
```

Verificar al cerrar:

```bash
# Tabla creada con RLS
echo "select tablename, rowsecurity from pg_tables where tablename = 'lessons';"

# Indices presentes
echo "select indexname from pg_indexes where tablename = 'lessons';"
```

## Flujo principal (cualquier operacion)

1. **Lee primero**. `mcp__claude_ai_Supabase__list_tables` antes de cualquier migracion. Si la tabla ya existe y la owna otra skill o el usuario, NO sobrescribir — escalar c2.
2. **Aplica migracion**. Nombre incremental `<NNNN>_<descripcion>.sql`. RLS antes del primer write.
3. **Verifica**. `list_tables` confirma que aparece. `get_advisors` (security + performance) detecta problemas RLS o queries lentas.
4. **Documenta cross-ref**. Si la tabla la consume otra skill (ej. `lessons` la consume `payments-polar` para vincular purchases), declarar el join en el README de la feature.

## Si tu Directiva no es Next.js/Supabase

Esta skill aplica a cualquier proyecto que use Supabase como backend. La capa Next.js es trivial — los clients se ajustan segun framework. Ver `@.claude/skills/auth-stack/references/non-next/<framework>.md` para clientes en Expo, SvelteKit, Remix, API-only.

Si la Directiva pide otro backend (Firebase, PlanetScale, Postgres self-hosted), esta skill NO aplica — escalar c2 al usuario para confirmar el cambio de backend antes de improvisar.

## Cross-references con skills hermanas

- `@.claude/skills/auth-stack/SKILL.md` — primer cliente y migracion `profiles`. Esta skill reusa su patron de RLS para cualquier tabla nueva. Hand-off: la migracion `profiles` siempre va primero (`0001_profiles_with_rls`).
- `@.claude/skills/emails-transactional/SKILL.md` — tabla `email_logs` para auditar envios. Hand-off: cada `sendEmail()` despues del envio inserta una fila con `to`, `template`, `status`, `provider_message_id`.
- `@.claude/skills/payments-polar/SKILL.md` — tablas `purchases` y `subscriptions`. Hand-off: el webhook de Polar UPSERT en `purchases` con `user_id` resuelto desde `profiles.email`.
- `@.claude/skills/pwa-mobile/SKILL.md` — columna `push_subscription` (jsonb) en `profiles` o tabla aparte `push_subscriptions`. Hand-off: el subscribe handler hace UPSERT.
- `@.claude/skills/ai-sdk-kit/SKILL.md` — tablas `conversations` y `messages` con `pgvector` para embeddings. Hand-off: `supabase-admin` aplica la migracion + extension `vector`; `ai-sdk-kit` consume el storage.

## Archivos lazy-loaded

- `references/queries-comunes.md` — patrones SQL frecuentes (count, group by, window functions, CTE recursivos para metricas).
- `references/storage.md` — Supabase Storage: buckets, policies, signed URLs, lifecycle.
- `references/edge-functions.md` — crear, deploy, secrets, logs, debugging.
- `references/metricas.md` — queries canonicas para churn, MRR, funnel signup→purchase, retention cohort.
- `references/migraciones.md` — convenciones de naming, rollback, branch testing antes de prod.
- `scripts/dump-tables.sh` — pg_dump rapido a archivo local para backup pre-migracion.

## Validacion al cerrar

```bash
# No hay advisors criticos pendientes
mcp__claude_ai_Supabase__get_advisors --type security
mcp__claude_ai_Supabase__get_advisors --type performance

# Tablas inventariadas y RLS habilitada
echo "select tablename, rowsecurity from pg_tables where schemaname='public' order by tablename;"
```
