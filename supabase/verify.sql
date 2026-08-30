-- verify.sql — 0단계 DB가 제대로 만들어졌는지 확인한다.
-- apply_all.sql 실행 후 SQL Editor 에 붙여넣고 실행하면 점검 목록이 표로 나온다.
-- 상태 열이 전부 OK 여야 한다.

with checks as (
  -- 확장
  select 1 as 순서, '확장 · postgis' as 항목,
         case when exists (select 1 from pg_extension where extname = 'postgis')
              then 'OK' else '없음 — 20260830090000_extensions.sql 실행 필요' end as 상태
  union all
  select 2, '확장 · pg_trgm',
         case when exists (select 1 from pg_extension where extname = 'pg_trgm')
              then 'OK' else '없음' end

  -- 테이블 5개
  union all
  select 10, '테이블 · ' || t,
         case when to_regclass('public.' || t) is not null then 'OK' else '없음' end
    from unnest(array['regions','places','shelters','animals','sync_logs']) as t

  -- RLS 가 5개 테이블 모두 켜져 있어야 한다
  union all
  select 20, 'RLS · ' || c.relname,
         case when c.relrowsecurity then 'OK' else '꺼짐 — 20260830090600_rls.sql 실행 필요' end
    from pg_class c
   where c.relnamespace = 'public'::regnamespace
     and c.relkind = 'r'
     and c.relname in ('regions','places','shelters','animals','sync_logs')

  -- 읽기 정책이 각 1개씩 있어야 한다 (없으면 앱에서 빈 목록으로 보인다)
  union all
  select 30, '읽기정책 · ' || t,
         coalesce(
           (select count(*)::text || '개'
              from pg_policies p
             where p.schemaname = 'public' and p.tablename = t), '0개')
    from unnest(array['regions','places','shelters','animals','sync_logs']) as t

  -- places.geom 이 lat/lng 로부터 자동 생성되는지
  union all
  select 40, 'places.geom 자동생성',
         coalesce(
           (select case when is_generated = 'ALWAYS' then 'OK' else '수동 — 트리거 대안 검토' end
              from information_schema.columns
             where table_schema = 'public' and table_name = 'places' and column_name = 'geom'),
           '컬럼 없음')

  -- 반경 검색 RPC
  union all
  select 50, 'RPC · places_nearby',
         case when exists (
                select 1 from pg_proc
                 where pronamespace = 'public'::regnamespace and proname = 'places_nearby')
              then 'OK' else '없음' end

  -- 적재 현황 (지금은 0건이 정상)
  union all
  select 60, '적재 · regions 행수', (select count(*)::text || '행' from public.regions)
)
select 항목, 상태 from checks order by 순서, 항목;
