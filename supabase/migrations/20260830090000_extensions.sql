-- 20260830090000_extensions.sql
-- 0단계 · 확장 설치
--
-- Supabase 관례에 따라 확장은 public 이 아니라 extensions 스키마에 설치한다.
-- PostgREST 의 검색 경로에 extensions 가 포함되어 있으므로 앱에서는 신경 쓸 필요 없다.
-- 이후 마이그레이션에서 geography / gin_trgm_ops 를 extensions. 로 명시 참조한다.

create extension if not exists postgis  with schema extensions;  -- 좌표 · 반경 검색
create extension if not exists pg_trgm  with schema extensions;  -- 지역명 부분일치 검색 (ilike)
