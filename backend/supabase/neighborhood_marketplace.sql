-- DailySathi Neighbourhood Marketplace (additive migration)
-- Apply after backend/supabase/schema.sql. Exact coordinates are private and are
-- returned only as an approximate distance by the bounded-radius RPCs.
create extension if not exists pgcrypto;

alter table public.service_providers
    add column if not exists verification_level text not null default 'unverified'
        check (verification_level in ('unverified','phone_verified','document_verified','admin_verified')),
    add column if not exists average_rating numeric(3,2) not null default 0
        check (average_rating between 0 and 5),
    add column if not exists review_count integer not null default 0
        check (review_count >= 0);

create table if not exists public.neighborhood_service_locations (
    provider_id uuid primary key references public.service_providers(id) on delete cascade,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    updated_at timestamptz not null default now()
);
alter table public.neighborhood_service_locations enable row level security;

create table if not exists public.service_provider_rates (
    id uuid primary key default gen_random_uuid(),
    provider_id uuid not null references public.service_providers(id) on delete cascade,
    service_name text not null check (char_length(trim(service_name)) between 2 and 100),
    min_rate numeric(10,2) not null check (min_rate >= 0),
    max_rate numeric(10,2) not null check (max_rate >= min_rate),
    rate_unit text not null default 'per job' check (char_length(trim(rate_unit)) between 2 and 40),
    notes text not null default '' check (char_length(notes) <= 300),
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);
create index if not exists service_provider_rates_provider_idx
    on public.service_provider_rates(provider_id) where is_active;
alter table public.service_provider_rates enable row level security;
drop policy if exists "Public can view rates of approved providers" on public.service_provider_rates;
create policy "Public can view rates of approved providers"
    on public.service_provider_rates for select to anon, authenticated
    using (is_active and exists (
        select 1 from public.service_providers p
        where p.id = provider_id and p.status = 'approved'
    ));

create table if not exists public.service_provider_reviews (
    id uuid primary key default gen_random_uuid(),
    provider_id uuid not null references public.service_providers(id) on delete cascade,
    reviewer_id uuid not null references auth.users(id) on delete cascade,
    rating integer not null check (rating between 1 and 5),
    review_text text not null default '' check (char_length(review_text) <= 500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(provider_id, reviewer_id)
);
create index if not exists service_provider_reviews_provider_idx
    on public.service_provider_reviews(provider_id, created_at desc);
alter table public.service_provider_reviews enable row level security;
drop policy if exists "Public can read provider reviews" on public.service_provider_reviews;
drop policy if exists "Users can read their own provider reviews" on public.service_provider_reviews;
create policy "Users can read their own provider reviews"
    on public.service_provider_reviews for select to authenticated using (reviewer_id = auth.uid());
drop policy if exists "Signed-in users can review approved providers" on public.service_provider_reviews;
create policy "Signed-in users can review approved providers"
    on public.service_provider_reviews for insert to authenticated
    with check (reviewer_id = auth.uid() and exists (
        select 1 from public.service_providers p
        where p.id = provider_id and p.status = 'approved'
    ));
drop policy if exists "Users can edit their own reviews" on public.service_provider_reviews;
create policy "Users can edit their own reviews"
    on public.service_provider_reviews for update to authenticated
    using (reviewer_id = auth.uid())
    with check (reviewer_id = auth.uid());

create or replace function public.refresh_service_provider_rating()
returns trigger language plpgsql security definer set search_path = public as $$
declare target_id uuid;
begin
    target_id := coalesce(new.provider_id, old.provider_id);
    update public.service_providers p
       set average_rating = coalesce((
               select round(avg(r.rating)::numeric, 2)
               from public.service_provider_reviews r where r.provider_id = target_id
           ), 0),
           review_count = (select count(*) from public.service_provider_reviews r where r.provider_id = target_id)
     where p.id = target_id;
    if tg_op = 'DELETE' then return old; end if;
    return new;
end;
$$;
drop trigger if exists service_provider_reviews_rating_refresh on public.service_provider_reviews;
create trigger service_provider_reviews_rating_refresh
after insert or update or delete on public.service_provider_reviews
for each row execute function public.refresh_service_provider_rating();

create table if not exists public.neighborhood_listings (
    id uuid primary key default gen_random_uuid(),
    seller_id uuid not null references auth.users(id) on delete cascade,
    title text not null check (char_length(trim(title)) between 3 and 100),
    description text not null default '' check (char_length(description) <= 1000),
    category text not null check (category in ('Furniture','Kids items','Electronics','Books','Home & Kitchen','Clothing','Sports & Hobby','Other')),
    listing_type text not null check (listing_type in ('sale','swap','free')),
    item_condition text not null check (item_condition in ('like_new','good','fair')),
    price_inr numeric(10,2) not null default 0 check (price_inr >= 0),
    swap_preferences text not null default '' check (char_length(swap_preferences) <= 300),
    contact_phone text not null check (contact_phone ~ '^\\+?[0-9]{10,15}$'),
    city text not null check (char_length(trim(city)) between 2 and 80),
    area text not null check (char_length(trim(area)) between 2 and 100),
    status text not null default 'pending' check (status in ('pending','active','reserved','sold','removed','rejected')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '60 days')
);
create index if not exists neighborhood_listings_feed_idx
    on public.neighborhood_listings(status, category, created_at desc);
create index if not exists neighborhood_listings_seller_idx
    on public.neighborhood_listings(seller_id, created_at desc);
alter table public.neighborhood_listings enable row level security;
drop policy if exists "Public can view active neighborhood listings" on public.neighborhood_listings;
create policy "Public can view active neighborhood listings"
    on public.neighborhood_listings for select to anon, authenticated using (status in ('active','reserved'));
drop policy if exists "Users can view their own listings" on public.neighborhood_listings;
create policy "Users can view their own listings"
    on public.neighborhood_listings for select to authenticated using (seller_id = auth.uid());
drop policy if exists "Users can update their own listings" on public.neighborhood_listings;
create policy "Users can update their own listings"
    on public.neighborhood_listings for update to authenticated
    using (seller_id = auth.uid()) with check (seller_id = auth.uid());
drop policy if exists "Users can delete their own listings" on public.neighborhood_listings;
create policy "Users can delete their own listings"
    on public.neighborhood_listings for delete to authenticated using (seller_id = auth.uid());

create table if not exists public.neighborhood_listing_locations (
    listing_id uuid primary key references public.neighborhood_listings(id) on delete cascade,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180)
);
alter table public.neighborhood_listing_locations enable row level security;

create table if not exists public.neighborhood_listing_reports (
    id uuid primary key default gen_random_uuid(),
    listing_id uuid not null references public.neighborhood_listings(id) on delete cascade,
    reporter_id uuid not null references auth.users(id) on delete cascade,
    reason text not null check (reason in ('spam','fraud','unsafe','prohibited','other')),
    details text not null default '' check (char_length(details) <= 500),
    created_at timestamptz not null default now(),
    unique(listing_id, reporter_id)
);
alter table public.neighborhood_listing_reports enable row level security;
drop policy if exists "Signed-in users can report listings" on public.neighborhood_listing_reports;
create policy "Signed-in users can report listings"
    on public.neighborhood_listing_reports for insert to authenticated
    with check (reporter_id = auth.uid());

create or replace function public.find_nearby_service_providers(
    p_latitude double precision, p_longitude double precision,
    p_radius_km double precision default 3, p_category text default null
)
returns table (
    id uuid, business_name text, category text, contact_name text, phone text,
    city text, area text, description text, verification_level text,
    average_rating numeric, review_count integer, distance_km double precision
)
language sql stable security definer set search_path = public as $$
    select p.id, p.business_name, p.category, p.contact_name, p.phone,
           p.city, p.area, coalesce(p.description,''), p.verification_level,
           p.average_rating, p.review_count,
           6371 * 2 * asin(sqrt(
             power(sin(radians(l.latitude - p_latitude) / 2), 2) +
             cos(radians(p_latitude)) * cos(radians(l.latitude)) *
             power(sin(radians(l.longitude - p_longitude) / 2), 2)
           )) as distance_km
      from public.service_providers p
      join public.neighborhood_service_locations l on l.provider_id = p.id
     where p.status = 'approved'
       and p_latitude between -90 and 90 and p_longitude between -180 and 180
       and p_radius_km between 2 and 5
       and (p_category is null or p.category = p_category)
       and 6371 * 2 * asin(sqrt(
             power(sin(radians(l.latitude - p_latitude) / 2), 2) +
             cos(radians(p_latitude)) * cos(radians(l.latitude)) *
             power(sin(radians(l.longitude - p_longitude) / 2), 2)
           )) <= p_radius_km
     order by distance_km asc limit 100;
$$;

drop function if exists public.find_nearby_neighborhood_listings(double precision,double precision,double precision,text);

create or replace function public.find_nearby_neighborhood_listings(
    p_latitude double precision, p_longitude double precision,
    p_radius_km double precision default 3, p_category text default null
)
returns table (
    id uuid, title text, description text, category text, listing_type text,
    item_condition text, price_inr numeric, swap_preferences text,
    city text, area text, created_at timestamptz,
    distance_km double precision
)
language sql stable security definer set search_path = public as $$
    select n.id, n.title, n.description, n.category, n.listing_type,
           n.item_condition, n.price_inr, n.swap_preferences,
           n.city, n.area, n.created_at,
           6371 * 2 * asin(sqrt(
             power(sin(radians(l.latitude - p_latitude) / 2), 2) +
             cos(radians(p_latitude)) * cos(radians(l.latitude)) *
             power(sin(radians(l.longitude - p_longitude) / 2), 2)
           )) as distance_km
      from public.neighborhood_listings n
      join public.neighborhood_listing_locations l on l.listing_id = n.id
     where n.status = 'active'
       and n.expires_at > now()
       and p_latitude between -90 and 90 and p_longitude between -180 and 180
       and p_radius_km between 2 and 5
       and (p_category is null or n.category = p_category)
       and 6371 * 2 * asin(sqrt(
             power(sin(radians(l.latitude - p_latitude) / 2), 2) +
             cos(radians(p_latitude)) * cos(radians(l.latitude)) *
             power(sin(radians(l.longitude - p_longitude) / 2), 2)
           )) <= p_radius_km
     order by n.created_at desc limit 100;
$$;

create or replace function public.create_neighborhood_listing(
    p_title text, p_description text, p_category text, p_listing_type text,
    p_item_condition text, p_price_inr numeric, p_swap_preferences text,
    p_contact_phone text, p_city text, p_area text,
    p_latitude double precision, p_longitude double precision
)
returns uuid language plpgsql security definer set search_path = public as $$
declare new_id uuid;
begin
    if auth.uid() is null then raise exception 'Sign in with phone verification to post an item'; end if;
    if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
        raise exception 'A valid approximate location is required';
    end if;
    insert into public.neighborhood_listings(
        seller_id,title,description,category,listing_type,item_condition,
        price_inr,swap_preferences,contact_phone,city,area,status
    ) values (
        auth.uid(),trim(p_title),coalesce(p_description,''),p_category,p_listing_type,
        p_item_condition,coalesce(p_price_inr,0),coalesce(p_swap_preferences,''),
        p_contact_phone,trim(p_city),trim(p_area),'pending'
    ) returning id into new_id;
    insert into public.neighborhood_listing_locations(listing_id,latitude,longitude)
    values (new_id,p_latitude,p_longitude);
    return new_id;
end;
$$;


-- Providers set their own private service location only after signing in.
create or replace function public.set_neighborhood_service_location(
    p_provider_id uuid, p_latitude double precision, p_longitude double precision
)
returns void language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'Sign in is required'; end if;
    if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
        raise exception 'Invalid coordinates';
    end if;
    if not exists (
        select 1 from public.service_providers p
        where p.id = p_provider_id and p.owner_id = auth.uid() and p.status = 'pending'
    ) then raise exception 'Only the owner of a pending provider application can set its location'; end if;
    insert into public.neighborhood_service_locations(provider_id,latitude,longitude)
    values (p_provider_id,p_latitude,p_longitude)
    on conflict (provider_id) do update
      set latitude = excluded.latitude, longitude = excluded.longitude, updated_at = now();
end;
$$;
grant execute on function public.set_neighborhood_service_location(uuid,double precision,double precision) to authenticated;

-- Public review feed deliberately omits reviewer IDs and other account details.
create or replace function public.get_service_provider_reviews(p_provider_id uuid)
returns table (rating integer, review_text text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
    select r.rating, r.review_text, r.created_at
      from public.service_provider_reviews r
      join public.service_providers p on p.id = r.provider_id
     where r.provider_id = p_provider_id and p.status = 'approved'
     order by r.created_at desc limit 50;
$$;
grant execute on function public.get_service_provider_reviews(uuid) to anon, authenticated;

grant execute on function public.find_nearby_service_providers(double precision,double precision,double precision,text) to anon, authenticated;
grant execute on function public.find_nearby_neighborhood_listings(double precision,double precision,double precision,text) to anon, authenticated;
grant execute on function public.create_neighborhood_listing(text,text,text,text,text,numeric,text,text,text,text,double precision,double precision) to authenticated;

-- Public listing search never returns a seller's phone number.
-- Contact is disclosed only after the buyer signs in and explicitly taps Contact.
create or replace function public.get_neighborhood_listing_contact(p_listing_id uuid)
returns text language plpgsql stable security definer set search_path = public as $$
declare result text;
begin
    if auth.uid() is null then raise exception 'Sign in is required to contact a seller'; end if;
    select contact_phone into result
      from public.neighborhood_listings
     where id = p_listing_id and status in ('active','reserved') and expires_at > now();
    if result is null then raise exception 'Seller contact is not available'; end if;
    return result;
end;
$$;
grant execute on function public.get_neighborhood_listing_contact(uuid) to authenticated;

create or replace function public.set_neighborhood_listing_status(p_listing_id uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'Sign in is required'; end if;
    if p_status not in ('reserved','sold','removed') then
        raise exception 'Unsupported listing status';
    end if;
    update public.neighborhood_listings
       set status = p_status, updated_at = now()
     where id = p_listing_id and seller_id = auth.uid()
       and status in ('active','reserved');
    if not found then raise exception 'Listing not found or not owned by this account'; end if;
end;
$$;
grant execute on function public.set_neighborhood_listing_status(uuid,text) to authenticated;
