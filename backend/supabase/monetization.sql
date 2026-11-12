-- DailySathi monetization foundation (Google Play subscriptions)
-- Run after creating a Supabase project. This migration does NOT enable billing.
-- Never expose the Supabase service-role key or Google Play service-account credentials in Android.

create extension if not exists pgcrypto;

-- One profile per authenticated DailySathi account. Phone is optional and must be
-- collected with explicit consent; Supabase Auth phone verification is authoritative.
create table if not exists public.user_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null check (char_length(trim(display_name)) between 1 and 80),
    mobile_number text,
    mobile_verified boolean not null default false,
    contact_consent_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint profile_mobile_format check (
        mobile_number is null or mobile_number ~ '^\+[1-9][0-9]{7,14}$'
    )
);

alter table public.user_profiles enable row level security;
drop policy if exists "Users can view own profile" on public.user_profiles;
create policy "Users can view own profile"
    on public.user_profiles for select to authenticated
    using (user_id = auth.uid());

-- Profile writes are intentionally performed through a trusted Edge Function.
-- No client INSERT/UPDATE/DELETE policy is granted here.

-- Current entitlement projection. Only trusted backend functions using the
-- service role may write this table after verifying a Google Play purchase.
create table if not exists public.play_subscriptions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete restrict,
    product_id text not null default 'dailysathi_premium',
    base_plan_id text not null check (base_plan_id in ('monthly','six_months','annual')),
    google_order_id text,
    purchase_token_sha256 text not null unique,
    purchase_state text not null check (purchase_state in (
        'pending','active','grace_period','on_hold','paused','canceled','expired','revoked'
    )),
    acknowledgement_state text not null default 'pending'
        check (acknowledgement_state in ('pending','acknowledged')),
    started_at timestamptz,
    expires_at timestamptz,
    auto_renewing boolean not null default false,
    last_verified_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists play_subscriptions_user_expiry_idx
    on public.play_subscriptions(user_id, expires_at desc);
create index if not exists play_subscriptions_state_expiry_idx
    on public.play_subscriptions(purchase_state, expires_at);

alter table public.play_subscriptions enable row level security;
drop policy if exists "Users can view own subscription status" on public.play_subscriptions;
create policy "Users can view own subscription status"
    on public.play_subscriptions for select to authenticated
    using (user_id = auth.uid());
-- No client write policies: subscription entitlements are server-authoritative.

-- Append-only audit trail for Google Play Developer Notifications and verification.
-- Raw purchase tokens and service-account credentials must never be written here.
create table if not exists public.play_purchase_events (
    id uuid primary key default gen_random_uuid(),
    subscription_id uuid references public.play_subscriptions(id) on delete set null,
    user_id uuid references auth.users(id) on delete set null,
    event_source text not null check (event_source in ('android_client','google_rtdn','backend_recheck','admin')),
    event_type text not null,
    google_event_id text,
    order_id text,
    event_payload jsonb not null default '{}'::jsonb,
    processed boolean not null default false,
    processing_error text,
    received_at timestamptz not null default now(),
    processed_at timestamptz
);

create unique index if not exists play_purchase_events_google_event_idx
    on public.play_purchase_events(google_event_id)
    where google_event_id is not null;
create index if not exists play_purchase_events_user_time_idx
    on public.play_purchase_events(user_id, received_at desc);

alter table public.play_purchase_events enable row level security;
-- Deliberately no client policies: payment/audit trails are private to backend operators.

-- A minimal admin-readable view can be added later after a trusted admin role
-- and dashboard authentication have been established. Do not grant public access.

create or replace function public.set_daily_sathi_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists user_profiles_updated_at on public.user_profiles;
create trigger user_profiles_updated_at before update on public.user_profiles
for each row execute function public.set_daily_sathi_updated_at();

drop trigger if exists play_subscriptions_updated_at on public.play_subscriptions;
create trigger play_subscriptions_updated_at before update on public.play_subscriptions
for each row execute function public.set_daily_sathi_updated_at();

-- A canceled auto-renewing subscription remains entitled until its paid-through
-- expiry. Grace-period access is also retained while the verified expiry is future.
-- Clients must not calculate entitlement from local purchase callbacks alone.
create or replace function public.has_active_daily_sathi_premium(p_user_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = public
as $$
    select exists (
        select 1
        from public.play_subscriptions s
        where s.user_id = p_user_id
          and s.purchase_state in ('active','grace_period','canceled')
          and s.expires_at > now()
    );
$$;

revoke all on function public.has_active_daily_sathi_premium(uuid) from public;
grant execute on function public.has_active_daily_sathi_premium(uuid) to authenticated;
