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
