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
