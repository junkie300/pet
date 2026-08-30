-- apply_all.sql — supabase/migrations/*.sql 를 순서대로 이어붙인 파일 (자동 생성)
--
-- 목적: Supabase SQL Editor 에 한 번에 붙여넣기 위한 것. 원본은 migrations/ 이며
--       이 파일은 아래 명령으로 언제든 다시 만든다.
--
--   python -c "from pathlib import Path; p=sorted(Path('supabase/migrations').glob('*.sql')); Path('supabase/apply_all.sql').write_text(''.join(f.read_text(encoding='utf-8') for f in p), encoding='utf-8')"
--
-- 전부 멱등(idempotent)하므로 여러 번 실행해도 안전하다.
-- 실패하면 그 파일부터 migrations/ 에서 하나씩 실행해 원인을 좁힌다.


-- ===== 20260830090000_extensions.sql ===============================

-- 20260830090000_extensions.sql
-- 0단계 · 확장 설치
--
-- Supabase 관례에 따라 확장은 public 이 아니라 extensions 스키마에 설치한다.
-- PostgREST 의 검색 경로에 extensions 가 포함되어 있으므로 앱에서는 신경 쓸 필요 없다.
-- 이후 마이그레이션에서 geography / gin_trgm_ops 를 extensions. 로 명시 참조한다.

create extension if not exists postgis  with schema extensions;  -- 좌표 · 반경 검색
create extension if not exists pg_trgm  with schema extensions;  -- 지역명 부분일치 검색 (ilike)

-- ===== 20260830090100_regions.sql ==================================

-- 20260830090100_regions.sql
-- 0단계 · regions — 지역코드 정규화 테이블 (spec.md §3.1)
--
-- 이 앱의 핵심 자산. 공공 API 마다 지역코드 체계가 다르므로 법정동코드를 기준으로 전부 흡수한다.
-- level 1=시도 / 2=시군구 / 3=읍면동. 리(里)는 적재하지 않는다.

-- PostGIS 는 extensions 스키마에 있다. 타입·연산자·opclass 해석을 위해 명시한다.
set search_path = public, extensions;

create table if not exists public.regions (
  code            text primary key,                 -- 법정동코드 10자리
  level           smallint not null check (level between 1 and 3),
  parent_code     text references public.regions(code),
  sido_name       text not null,
  sigungu_name    text,
  dong_name       text,
  full_name       text not null,                    -- 예: 경기도 성남시수정구 신흥동
  center_lat      double precision,
  center_lng      double precision,

  -- 외부 코드 매핑 (0단계에서 순차적으로 채운다)
  apms_upr_cd     text,                             -- 국가동물보호정보시스템 시도
  apms_org_cd     text,                             --            〃        시군구
  localdata_cd    text,                             -- LOCALDATA 지역코드
  tour_area_cd    text,                             -- TourAPI areaCode
  tour_sigungu_cd text,                             -- TourAPI sigunguCode

  updated_at      timestamptz not null default now()
);

comment on table  public.regions is '법정동코드 기준 지역 정규화 테이블. 모든 ETL 의 지역 매핑 기준점.';
comment on column public.regions.level is '1=시도, 2=시군구, 3=읍면동';
comment on column public.regions.full_name is '시도부터 이어붙인 전체 명칭. 지역 검색 대상 필드.';

create index if not exists regions_parent_code_idx on public.regions (parent_code);
create index if not exists regions_level_idx       on public.regions (level);

-- S-01 지역 검색: full_name=ilike.*{q}* 를 인덱스로 처리하기 위한 트라이그램 인덱스
create index if not exists regions_full_name_trgm_idx
  on public.regions using gin (full_name extensions.gin_trgm_ops);

-- ETL 역방향 조회(외부코드 → region_code)용
create index if not exists regions_localdata_cd_idx on public.regions (localdata_cd) where localdata_cd is not null;
create index if not exists regions_apms_org_cd_idx  on public.regions (apms_org_cd)  where apms_org_cd  is not null;

-- ===== 20260830090200_places.sql ===================================

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

-- ===== 20260830090300_shelters_animals.sql =========================

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

-- ===== 20260830090400_sync_logs.sql ================================

-- 20260830090400_sync_logs.sql
-- 전 단계 공통 · sync_logs — 운영 관측 (spec.md §3.4)
--
-- 앱은 S-08(데이터 출처)과 상세 화면의 "기준일" 표기를 위해 이 테이블을 읽는다.
-- 실패 로그는 클라이언트에 노출하지 않는다 (RLS 에서 success 만 허용).

create table if not exists public.sync_logs (
  id            bigserial primary key,
  source        text not null,                      -- localdata_hospital | tourapi_ldong | ...
  started_at    timestamptz not null default now(),
  finished_at   timestamptz,
  rows_upserted int,
  status        text not null default 'running'
                check (status in ('running','success','partial','failed')),
  error         text
);

create index if not exists sync_logs_source_finished_idx
  on public.sync_logs (source, finished_at desc);
create index if not exists sync_logs_success_idx
  on public.sync_logs (finished_at desc) where status = 'success';

-- ===== 20260830090500_rpc.sql ======================================

-- 20260830090500_rpc.sql
-- API 계약 · RPC (spec.md §4)
--
-- 지역 콤보 / 지역 검색 / 지역 내 장소 / 상세 조회는 PostgREST 자동 생성 엔드포인트로 충분하다.
-- 반경 검색만 PostGIS 가 필요하므로 RPC 로 만든다.
--
-- security invoker(기본값)이므로 places 의 RLS 가 그대로 적용된다.
-- 반환 타입이 setof places 라서 geom 컬럼이 응답에 포함된다. 전송량이 문제가 되면
-- 명시 컬럼 + distance_m 를 반환하는 형태로 바꾼다.

-- PostGIS 는 extensions 스키마에 있다. 타입·연산자·opclass 해석을 위해 명시한다.
set search_path = public, extensions;

create or replace function public.places_nearby(
  p_lat        double precision,
  p_lng        double precision,
  p_radius_m   int,
  p_categories public.place_category[]
)
returns setof public.places
language sql
stable
set search_path = public, extensions
as $$
  select *
    from public.places
   where status = 'open'
     and category = any(p_categories)
     and extensions.st_dwithin(
           geom,
           extensions.st_setsrid(extensions.st_makepoint(p_lng, p_lat), 4326)::extensions.geography,
           p_radius_m
         )
   order by geom <-> extensions.st_setsrid(extensions.st_makepoint(p_lng, p_lat), 4326)::extensions.geography
   limit 200;
$$;

comment on function public.places_nearby is '반경 검색. 최대 200건, 가까운 순. 폐업/휴업 제외.';

grant execute on function public.places_nearby to anon, authenticated;

-- ===== 20260830090600_rls.sql ======================================

-- 20260830090600_rls.sql
-- 보안 · RLS (spec.md §8)
--
-- 원칙: 앱(anon 키)은 읽기만 한다. 쓰기는 ETL 이 service_role 키로 수행하며
--       service_role 은 RLS 를 우회하므로 INSERT/UPDATE 정책을 만들지 않는다.
--       → 정책을 하나도 만들지 않은 동작(=거부)이 그대로 쓰기 차단이 된다.

alter table public.regions   enable row level security;
alter table public.places    enable row level security;
alter table public.shelters  enable row level security;
alter table public.animals   enable row level security;
alter table public.sync_logs enable row level security;

drop policy if exists regions_select_public   on public.regions;
drop policy if exists places_select_public    on public.places;
drop policy if exists shelters_select_public  on public.shelters;
drop policy if exists animals_select_public   on public.animals;
drop policy if exists sync_logs_select_public on public.sync_logs;

create policy regions_select_public  on public.regions
  for select to anon, authenticated using (true);

create policy places_select_public   on public.places
  for select to anon, authenticated using (true);

create policy shelters_select_public on public.shelters
  for select to anon, authenticated using (true);

create policy animals_select_public  on public.animals
  for select to anon, authenticated using (true);

-- 실패 로그(에러 메시지)는 클라이언트에 노출하지 않는다. 성공 로그만 기준일 표기에 쓴다.
create policy sync_logs_select_public on public.sync_logs
  for select to anon, authenticated using (status = 'success');
