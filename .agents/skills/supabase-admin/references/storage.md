# Supabase Storage — buckets, policies, URLs

Storage es el equivalente de S3 dentro del proyecto Supabase. Cada archivo vive en un bucket, y los buckets tienen RLS independiente de las tablas.

## Crear bucket privado

```sql
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', false);
```

`public = false` es el default seguro. Acceso solo via signed URL o por user autenticado con policy explicita.

## Policy de upload (cada user sube en su carpeta)

```sql
create policy "users_upload_own_avatar" on storage.objects
  for insert with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "users_read_own_avatar" on storage.objects
  for select using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
```

Estructura de paths: `<user-id>/<filename>`. La policy lee el primer segmento del path y lo compara con `auth.uid()`.

## Upload desde cliente browser

```ts
const supabase = createClient();
const { data: { user } } = await supabase.auth.getUser();
if (!user) return;

const { data, error } = await supabase.storage
  .from('avatars')
  .upload(`${user.id}/profile.jpg`, file, { upsert: true, contentType: 'image/jpeg' });
```

## Signed URL (compartir tempora)

```ts
const { data } = await supabase.storage
  .from('avatars')
  .createSignedUrl(`${user.id}/profile.jpg`, 3600); // 1 hora
console.log(data.signedUrl);
```

## Lifecycle (eliminar archivos viejos)

Supabase no tiene lifecycle nativo como S3. Implementar via Edge Function programada:

```ts
// supabase/functions/cleanup-old-temp/index.ts
const { data } = await supabase.storage.from('temp').list('', { limit: 1000 });
const oldFiles = data.filter((f) => Date.now() - new Date(f.created_at).getTime() > 7 * 24 * 3600 * 1000);
await supabase.storage.from('temp').remove(oldFiles.map((f) => f.name));
```

Trigger via `pg_cron` cada 24h. Cross-ref `references/edge-functions.md`.

## Cross-ref con auth-stack

Avatares en profiles → bucket `avatars` con policy por user-id. Cross-ref `@.agents/skills/auth-stack/SKILL.md`.

## Cross-ref con image-kit

Imagenes generadas por `image-kit` → upload directo a Storage para usar en UI sin URLs temporales. Cross-ref `@.agents/skills/image-kit/SKILL.md`.
