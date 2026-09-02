-- verify.sql — 0단계 DB와 적재 상태를 확인한다.
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

  -- 적재 현황
  union all
  select 60, '적재 · regions 행수', (select count(*)::text || '행' from public.regions)
  union all
  select 61, '적재 · 레벨별',
         (select '시도 ' || count(*) filter (where level = 1)
               || ' · 시군구 ' || count(*) filter (where level = 2)
               || ' · 읍면동 ' || count(*) filter (where level = 3)
            from public.regions)

  -- 계층이 끊긴 곳이 있으면 3단 콤보가 막다른 길이 된다 (plan.md 0단계 DoD)
  union all
  select 70, '계층 · parent_code 없는 시군구/읍면동',
         (select case when count(*) = 0 then 'OK' else count(*)::text || '행 — 끊김' end
            from public.regions where level > 1 and parent_code is null)
  union all
  select 71, '계층 · 자식 없는 시군구',
         (select case when count(*) = 0 then 'OK' else count(*)::text || '개' end
            from public.regions r
           where r.level = 2
             and not exists (select 1 from public.regions c where c.parent_code = r.code))

  -- 0단계 외부 코드 매핑 (plan.md 0단계 DoD: 미매핑 0)
  union all
  select 80, '매핑 · apms_upr_cd 빈 행',
         (select case when count(*) = 0 then 'OK' else count(*)::text || '행' end
            from public.regions where apms_upr_cd is null)
  union all
  select 81, '매핑 · apms_org_cd 빈 읍면동',
         (select case when count(*) = 33 then 'OK (세종 33 — 세종은 시군구가 없다)'
                      when count(*) = 0  then 'OK'
                      else count(*)::text || '행 — 확인 필요' end
            from public.regions where level = 3 and apms_org_cd is null)
  union all
  select 82, '매핑 · tour_sigungu_cd 빈 읍면동',
         (select case when count(*) = 0 then 'OK' else count(*)::text || '행' end
            from public.regions where level = 3 and tour_sigungu_cd is null)
  union all
  select 83, '매핑 · localdata_cd',
         (select case when count(*) = 0 then '미착수 — LOCALDATA 인증키 대기'
                      else count(*)::text || '행 채움' end
            from public.regions where localdata_cd is not null)

  -- 읍면동 중심좌표 (0단계 잔여 · 카카오 REST 키 필요)
  union all
  select 90, '좌표 · 중심좌표 있는 읍면동',
         (select count(*)::text || ' / '
               || (select count(*) from public.regions where level = 3)::text
            from public.regions where level = 3 and center_lat is not null)
  union all
  select 91, '좌표 · 대한민국 범위를 벗어난 행',
         (select case when count(*) = 0 then 'OK' else count(*)::text || '행 — x/y 뒤바뀜 의심' end
            from public.regions
           where center_lat is not null
             and not (center_lat between 33.0 and 39.5 and center_lng between 124.0 and 132.0))

  -- ETL 실행 이력
  union all
  select 95, 'ETL · 마지막 성공',
         coalesce((select source || ' ' || to_char(finished_at, 'YYYY-MM-DD HH24:MI')
                     from public.sync_logs where status = 'success'
                    order by finished_at desc limit 1), '없음')
)
select 항목, 상태 from checks order by 순서, 항목;
