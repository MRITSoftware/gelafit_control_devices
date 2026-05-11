create table if not exists public.gelafit_control_devices (
  device_id text primary key,
  unit_email text,
  status text not null default 'offline',
  selected_apps jsonb not null default '[]'::jsonb,
  active_package text,
  command text,
  target_package text,
  command_nonce bigint not null default 0,
  last_command_nonce bigint not null default 0,
  last_seen_at timestamptz not null default now(),
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.gelafit_control_devices
add column if not exists unit_email text;

alter table public.gelafit_control_devices
add column if not exists active_package text;

create unique index if not exists gelafit_control_devices_unit_email_key
on public.gelafit_control_devices (unit_email)
where unit_email is not null;

alter table public.gelafit_control_devices enable row level security;

drop policy if exists "gelafit_control_devices_read" on public.gelafit_control_devices;
create policy "gelafit_control_devices_read"
on public.gelafit_control_devices
for select
to anon
using (true);

drop policy if exists "gelafit_control_devices_insert" on public.gelafit_control_devices;
create policy "gelafit_control_devices_insert"
on public.gelafit_control_devices
for insert
to anon
with check (true);

drop policy if exists "gelafit_control_devices_update" on public.gelafit_control_devices;
create policy "gelafit_control_devices_update"
on public.gelafit_control_devices
for update
to anon
using (true)
with check (true);

-- Exemplos de comando:
-- Abrir pacote especifico:
-- update public.gelafit_control_devices
-- set command = 'open', target_package = 'com.exemplo.app', command_nonce = command_nonce + 1
-- where device_id = 'COLE-O-DEVICE-ID-AQUI';
--
-- Reiniciar pacote especifico, sem force-stop:
-- update public.gelafit_control_devices
-- set command = 'restart', target_package = 'com.exemplo.app', command_nonce = command_nonce + 1
-- where device_id = 'COLE-O-DEVICE-ID-AQUI';
--
-- Reabrir todos selecionados:
-- update public.gelafit_control_devices
-- set command = 'restart_selected', target_package = null, command_nonce = command_nonce + 1
-- where device_id = 'COLE-O-DEVICE-ID-AQUI';
--
-- Definir o app principal do kiosk:
-- update public.gelafit_control_devices
-- set active_package = 'com.exemplo.app.principal'
-- where unit_email = 'unidade@exemplo.com';
