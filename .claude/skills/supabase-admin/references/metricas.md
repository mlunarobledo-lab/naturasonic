# Metricas canonicas — churn, MRR, funnel, retention

## MRR (Monthly Recurring Revenue)

```sql
-- Suscripciones activas + monto mensualizado
select
  sum(case
    when interval = 'month' then amount_cents
    when interval = 'year' then amount_cents / 12.0
  end) / 100.0 as mrr_usd
from public.subscriptions
where status = 'active';
```

## Churn rate (mensual)

```sql
-- % de suscripciones canceladas este mes vs activas a inicio de mes
with active_inicio as (
  select count(*) as n from public.subscriptions
  where status = 'active'
    and created_at < date_trunc('month', now())
),
canceladas_mes as (
  select count(*) as n from public.subscriptions
  where canceled_at >= date_trunc('month', now())
    and canceled_at < date_trunc('month', now()) + interval '1 month'
)
select
  100.0 * canceladas_mes.n / nullif(active_inicio.n, 0) as churn_pct_mes
from active_inicio, canceladas_mes;
```

## Funnel signup → primer cobro

```sql
select
  count(distinct p.id) as total_signups,
  count(distinct case when pur.id is not null then p.id end) as compraron,
  round(100.0 * count(distinct case when pur.id is not null then p.id end) / nullif(count(distinct p.id), 0), 1) as conversion_pct
from public.profiles p
left join public.purchases pur on pur.user_id = p.id;
```

## Retention cohort (semana 0 → semana 1, 2, 3, 4)

```sql
with cohorts as (
  select
    id,
    date_trunc('week', created_at) as cohort_week
  from public.profiles
),
events_per_week as (
  select
    c.cohort_week,
    c.id,
    floor(extract(epoch from (e.created_at - c.cohort_week)) / (7 * 24 * 3600))::int as week_offset
  from cohorts c
  join public.events e on e.user_id = c.id
)
select
  cohort_week,
  count(distinct id) filter (where week_offset = 0) as w0,
  count(distinct id) filter (where week_offset = 1) as w1,
  count(distinct id) filter (where week_offset = 2) as w2,
  count(distinct id) filter (where week_offset = 3) as w3,
  count(distinct id) filter (where week_offset = 4) as w4
from events_per_week
group by cohort_week
order by cohort_week desc
limit 8;
```

## Top creators por revenue

```sql
select
  p.email,
  p.full_name,
  sum(pur.amount_cents) / 100.0 as revenue_usd,
  count(distinct pur.id) as ventas
from public.profiles p
join public.purchases pur on pur.creator_id = p.id
where p.role = 'creator'
group by p.id, p.email, p.full_name
order by revenue_usd desc
limit 20;
```

## Average lifetime value (LTV simplificado)

```sql
select avg(total) as ltv_usd
from (
  select user_id, sum(amount_cents) / 100.0 as total
  from public.purchases
  group by user_id
) sub;
```

## Daily / weekly / monthly active users

```sql
select
  count(distinct user_id) filter (where created_at >= now() - interval '1 day') as dau,
  count(distinct user_id) filter (where created_at >= now() - interval '7 days') as wau,
  count(distinct user_id) filter (where created_at >= now() - interval '30 days') as mau
from public.events
where created_at >= now() - interval '30 days';
```
