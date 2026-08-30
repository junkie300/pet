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
