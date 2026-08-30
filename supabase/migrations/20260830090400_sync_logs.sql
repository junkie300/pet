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
