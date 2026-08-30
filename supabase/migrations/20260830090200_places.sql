-- 20260830090200_places.sql
-- 1~5단계 · places — POI 통합 테이블 (spec.md §3.2, DECISIONS D-26)
--
-- 메뉴 1(미용)·2(동물병원)·3(동반식당)·4(관광)·7(야생동물구조센터)를 한 테이블로 통합한다.
-- 카테고리가 달라도 구조가 같으므로 지도·목록·상세 화면 코드를 1벌만 만들면 된다.

-- PostGIS 는 extensions 스키마에 있다. 타입·연산자·opclass 해석을 위해 명시한다.
set search_path = public, extensions;

do $$
begin
  create type public.place_category as enum
    ('hospital','grooming','restaurant','tour','wildlife_center');
exception
  when duplicate_object then null;
end
$$;

create table if not exists public.places (
  id                bigserial primary key,
  category          public.place_category not null,
  source            text not null,                  -- localdata | mfds | tourapi | stddata | kcisa
  source_id         text not null,                  -- 원본 고유키 (upsert 기준)
  name              text not null,
  tel               text,
  address_road      text,
  address_jibun     text,
  lat               double precision,
  lng               double precision,

  -- lat/lng 로부터 자동 생성. ETL 이 geom 을 직접 쓰지 않으므로 좌표 불일치가 원천 차단된다.
  geom              extensions.geography(Point, 4326)
                    generated always as (
                      case
                        when lat is null or lng is null then null
                        else extensions.st_setsrid(extensions.st_makepoint(lng, lat), 4326)::extensions.geography
                      end
                    ) stored,

  region_code       text references public.regions(code),
  status            text not null default 'open'
                    check (status in ('open','closed','suspended')),
  extra             jsonb not null default '{}'::jsonb,
  source_updated_at timestamptz,                    -- 원본 기준일. 상세 화면에 반드시 노출한다.
  synced_at         timestamptz not null default now(),

  unique (source, source_id)
);

comment on table  public.places is '카테고리 통합 POI 테이블. 화면 코드 재사용을 위한 단일 스키마.';
comment on column public.places.source_updated_at is '원본 데이터 기준일. 전 상세 화면 노출 의무 (개발계획서 §8.1).';
comment on column public.places.extra is 'hospital:{open_24h,business_hours} / restaurant:{registered_mfds,pet_area} / tour:{content_id,image_url,pet_policy}';

create index if not exists places_geom_idx            on public.places using gist (geom);
create index if not exists places_region_category_idx on public.places (region_code, category) where status = 'open';
create index if not exists places_category_idx        on public.places (category)              where status = 'open';
create index if not exists places_name_trgm_idx       on public.places using gin (name extensions.gin_trgm_ops);

-- ── 참고 ────────────────────────────────────────────────────────────────
-- 위 generated column 이 PostGIS 버전 문제로 거부되면 아래 트리거 방식으로 대체한다.
-- (geom 컬럼 정의에서 generated ... stored 를 지우고 아래 주석을 해제)
--
-- create or replace function public.places_set_geom() returns trigger
-- language plpgsql as $fn$
-- begin
--   new.geom := case
--     when new.lat is null or new.lng is null then null
--     else extensions.st_setsrid(extensions.st_makepoint(new.lng, new.lat), 4326)::extensions.geography
--   end;
--   return new;
-- end;
-- $fn$;
--
-- create trigger places_set_geom_trg
--   before insert or update of lat, lng on public.places
--   for each row execute function public.places_set_geom();
