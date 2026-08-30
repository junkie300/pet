-- 20260830090300_shelters_animals.sql
-- 6단계 · shelters / animals — [입양] 탭 (spec.md §3.3)
--
-- ⚠️ 범위 제한: 이 데이터는 지자체 지정 "동물보호센터" 기준이다.
--    민간 단체(케어·카라 등)의 자체 구조·분양 동물은 포함되지 않는다.
--    → UI 에 "동물구조단체"가 아니라 "동물보호센터"로 표기할 것.

-- PostGIS 는 extensions 스키마에 있다. 타입·연산자·opclass 해석을 위해 명시한다.
set search_path = public, extensions;

create table if not exists public.shelters (
  care_reg_no  text primary key,                    -- 보호센터 등록번호
  name         text not null,
  tel          text,
  address      text,
  lat          double precision,
  lng          double precision,
  geom         extensions.geography(Point, 4326)
               generated always as (
                 case
                   when lat is null or lng is null then null
                   else extensions.st_setsrid(extensions.st_makepoint(lng, lat), 4326)::extensions.geography
                 end
               ) stored,
  region_code  text references public.regions(code),
  save_target  text,                                -- 구조 대상 동물
  org_name     text,                                -- 관할 기관
  extra        jsonb not null default '{}'::jsonb,
  synced_at    timestamptz not null default now()
);

create index if not exists shelters_region_idx on public.shelters (region_code);
create index if not exists shelters_geom_idx   on public.shelters using gist (geom);

create table if not exists public.animals (
  desertion_no  text primary key,                   -- 유기번호
  care_reg_no   text references public.shelters(care_reg_no),
  region_code   text references public.regions(code),
  kind_name     text,                               -- 품종
  color         text,
  age           text,
  weight        text,
  sex           text check (sex in ('M','F','Q')),
  neuter        text check (neuter in ('Y','N','U')),
  happen_dt     date,
  happen_place  text,
  notice_sdt    date,
  notice_edt    date,
  process_state text,                               -- 공고중 / 종료(입양) 등
  image_url     text,
  special_mark  text,
  synced_at     timestamptz not null default now()
);

create index if not exists animals_region_notice_idx
  on public.animals (region_code, process_state, notice_edt desc);
create index if not exists animals_care_reg_no_idx on public.animals (care_reg_no);
