-- Datos compartidos de BILLMON (tabla billmon_kv). Se corre UNA vez en Supabase -> SQL Editor.
create table if not exists public.billmon_kv (
  clave text primary key,
  valor jsonb not null,
  ts bigint not null,
  por text,
  actualizado timestamptz not null default now()
);
create index if not exists billmon_kv_actualizado_idx on public.billmon_kv (actualizado, clave);

create or replace function public.billmon_touch() returns trigger
language plpgsql as $$
begin
  if tg_op = 'UPDATE' and new.ts < old.ts then
    return old;            -- una hora más vieja no pisa un dato más nuevo
  end if;
  new.actualizado := now();
  return new;
end $$;

drop trigger if exists billmon_touch on public.billmon_kv;
create trigger billmon_touch before insert or update on public.billmon_kv
  for each row execute function public.billmon_touch();

alter table public.billmon_kv enable row level security;
drop policy if exists billmon_cuenta on public.billmon_kv;
create policy billmon_cuenta on public.billmon_kv for all to authenticated
  using ((auth.jwt() ->> 'email') = 'billmon@robrist.app')
  with check ((auth.jwt() ->> 'email') = 'billmon@robrist.app');
revoke all on public.billmon_kv from anon;
