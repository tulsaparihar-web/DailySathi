-- DailySathi Local Services Marketplace
-- Run this in the Supabase SQL Editor after enabling Phone Auth.
create extension if not exists pgcrypto;

create table if not exists public.service_providers (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    business_name text not null check (char_length(trim(business_name)) between 2 and 80),
    category text not null check (category in (
        'Electrician','Plumber','Mason','Carpenter','Painter',
        'House cleaner / Helper','AC repair','Appliance repair','Driver'
    )),
    contact_name text not null check (char_length(trim(contact_name)) between 2 and 80),
    phone text not null check (phone ~ '^\\+?[0-9]{10,15}$'),
    whatsapp_phone text,
    city text not null check (char_length(trim(city)) between 2 and 80),
    area text not null check (char_length(trim(area)) between 2 and 100),
    address text,
    description text check (char_length(coalesce(description,'')) <= 500),
    status text not null default 'pending' check (status in ('pending','approved','rejected','suspended')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists service_providers_category_city_idx
    on public.service_providers (category, city) where status = 'approved';
create index if not exists service_providers_area_idx
    on public.service_providers (area) where status = 'approved';
create index if not exists service_providers_owner_idx
    on public.service_providers (owner_id);

alter table public.service_providers enable row level security;

-- Anyone can discover only approved listings.
drop policy if exists "Public can view approved providers" on public.service_providers;
create policy "Public can view approved providers"
    on public.service_providers for select
    using (status = 'approved');

-- A signed-in provider can submit a listing only for their own account,
-- and every new listing starts in the moderation queue.
drop policy if exists "Providers can submit pending listings" on public.service_providers;
create policy "Providers can submit pending listings"
    on public.service_providers for insert to authenticated
    with check (owner_id = auth.uid() and status = 'pending');

-- Providers can edit their own pending listings. Approved listings require
-- admin moderation before any public profile changes take effect.
drop policy if exists "Providers can edit pending listings" on public.service_providers;
create policy "Providers can edit pending listings"
    on public.service_providers for update to authenticated
    using (owner_id = auth.uid() and status = 'pending')
    with check (owner_id = auth.uid() and status = 'pending');

drop policy if exists "Providers can delete pending listings" on public.service_providers;
create policy "Providers can delete pending listings"
    on public.service_providers for delete to authenticated
    using (owner_id = auth.uid() and status = 'pending');

-- Only accounts whose trusted app_metadata role is set to admin can moderate.
drop policy if exists "Admins can manage all provider listings" on public.service_providers;
create policy "Admins can manage all provider listings"
    on public.service_providers for all to authenticated
    using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin')
    with check ((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin');

create or replace function public.set_service_provider_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists service_providers_updated_at on public.service_providers;
create trigger service_providers_updated_at
before update on public.service_providers
for each row execute function public.set_service_provider_updated_at();
