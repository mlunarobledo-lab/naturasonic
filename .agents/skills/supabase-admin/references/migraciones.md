# Migraciones — naming, rollback, branch testing

## Naming canonico

`<NNNN>_<verbo>_<entidad>.sql`

- `0001_create_profiles_with_rls.sql`
- `0002_add_role_column_to_profiles.sql`
- `0003_create_lessons.sql`
- `0004_drop_legacy_field_from_profiles.sql`

Numero incremental, padding a 4 digitos. Verbo + entidad describe que hace.

## Idempotencia obligatoria

Cualquier migracion que se ejecute mas de una vez accidentalmente NO debe romper la BD. Use `if not exists` y `if exists`:

```sql
create table if not exists public.lessons (...);

alter table public.profiles
  add column if not exists referred_by uuid references public.profiles(id);

drop policy if exists "old_policy_name" on public.lessons;
create policy "new_policy_name" on public.lessons ...;
```

## Rollback

Supabase no tiene `rollback` automatico de migraciones. Cada migracion forward debe pensarse junto con su forma de reverso si la aplicacion ya esta en produccion:

```sql
-- 0010_add_status_column.sql
alter table public.purchases add column if not exists status text default 'pending';

-- 0011_remove_status_column_rollback.sql
alter table public.purchases drop column if exists status;
```

Si la migracion es destructiva (drop column, drop table), considerar:

1. Hacer backup antes (`scripts/dump-tables.sh`).
2. Aplicar primero en branch de Supabase (`mcp__claude_ai_Supabase__create_branch`).
3. Verificar que la app funciona con el branch.
4. Solo entonces aplicar a prod.

## Branch testing

```bash
# Crear branch desde main del proyecto
supabase branches create test-feature-x

# Ejecutar migracion ahi
supabase db push --branch test-feature-x

# Probar la app contra el branch (cambiar URL temporalmente)
# Si funciona:
supabase branches merge test-feature-x

# Si falla:
supabase branches delete test-feature-x
```

Disponible tambien via MCP: `mcp__claude_ai_Supabase__create_branch`, `merge_branch`, `delete_branch`.

## Aplicar migraciones desde Praxis

Default: `mcp__claude_ai_Supabase__apply_migration` con nombre + sql. Si el SQL es muy grande (>10KB), partir en 2 archivos. Si tiene un blob base64 enorme, usar `upload-payload` style (Edge Function que recibe el blob por HTTPS) en lugar de `execute_sql` — caso documentado en aprendizaje 2026-04-24 del meta-repo.
