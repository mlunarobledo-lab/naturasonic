# Queries comunes — patrones SQL frecuentes

## Conteos rapidos

```sql
-- Cuantos usuarios totales
select count(*) from public.profiles;

-- Cuantos signups esta semana
select count(*) from public.profiles
where created_at >= now() - interval '7 days';

-- Activos por rol
select role, count(*) from public.profiles group by role;
```

## Group by con date_trunc (timeline)

```sql
-- Signups por dia, ultimos 30 dias
select date_trunc('day', created_at)::date as dia, count(*) as signups
from public.profiles
where created_at >= now() - interval '30 days'
group by 1
order by 1;
```

## Funnel signup → first action

```sql
-- Cuantos alumnos que se registraron tambien hicieron al menos una compra
select
  count(distinct p.id) as signed_up,
  count(distinct case when pur.user_id is not null then p.id end) as purchased,
  round(100.0 * count(distinct case when pur.user_id is not null then p.id end) / nullif(count(distinct p.id), 0), 1) as pct
from public.profiles p
left join public.purchases pur on pur.user_id = p.id;
```

## Cohort retention

```sql
-- Alumnos signed-up este mes que volvieron la semana siguiente
with first_visit as (
  select p.id, date_trunc('week', p.created_at) as cohort
  from public.profiles p
  where date_trunc('month', p.created_at) = date_trunc('month', now())
),
return_visit as (
  select fv.id, fv.cohort,
    exists(
      select 1 from public.events e
      where e.user_id = fv.id
        and e.created_at >= fv.cohort + interval '7 days'
        and e.created_at < fv.cohort + interval '14 days'
    ) as returned
  from first_visit fv
)
select cohort, count(*) total, sum(case when returned then 1 else 0 end) returned
from return_visit
group by cohort
order by cohort;
```

## Window functions

```sql
-- Ranking de creators por revenue, con percentil
select
  p.email,
  sum(pur.amount_cents) as total,
  rank() over (order by sum(pur.amount_cents) desc) as rank,
  percent_rank() over (order by sum(pur.amount_cents)) as percentile
from public.profiles p
join public.purchases pur on pur.creator_id = p.id
where p.role = 'creator'
group by p.id, p.email
order by total desc;
```

## CTE recursivo (jerarquia)

```sql
-- Si tienes referrals padre/hijo:
with recursive referral_tree as (
  select id, email, referred_by, 1 as depth
  from public.profiles
  where referred_by is null

  union all

  select p.id, p.email, p.referred_by, rt.depth + 1
  from public.profiles p
  join referral_tree rt on rt.id = p.referred_by
)
select * from referral_tree order by depth, email;
```

## Tip de performance

Cualquier query que filtra por una columna sin indice y la tabla supera ~10K filas merece un `create index`. `mcp__claude_ai_Supabase__get_advisors --type performance` los detecta.
