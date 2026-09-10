# -*- coding: utf-8 -*-
"""적재 직전 안전장치 테스트. 네트워크·DB 없이 돈다.

여기서 지키려는 것은 하나다 — **아무것도 못 실었는데 success 가 찍히면 안 된다.**
그러면 앱이 낡은 데이터에 오늘 날짜를 붙여 보여준다 (spec.md §4).
"""

import unittest

from petetl import sanity
from petetl.sanity import SanityError


class RowCountTest(unittest.TestCase):
    def check(self, count, previous, **kw):
        sanity.check_row_count(count, previous, source="animal_hospital", **kw)

    def test_평소대로면_통과한다(self):
        self.check(10_617, 10_600)

    def test_0건은_언제나_실패다(self):
        with self.assertRaises(SanityError):
            self.check(0, 10_600)

    def test_첫_실행이어도_0건은_실패다(self):
        # 비교 대상이 없다고 0건을 실어서는 안 된다.
        with self.assertRaises(SanityError):
            self.check(0, None)

    def test_첫_실행은_건수만_맞으면_통과한다(self):
        self.check(10_617, None)

    def test_직전_기록이_0이면_비교하지_않는다(self):
        self.check(12, 0)

    def test_절반_밑으로_줄면_실패다(self):
        with self.assertRaises(SanityError):
            self.check(12, 10_600)

    def test_정확히_절반이면_통과한다(self):
        # 경계는 통과 쪽에 둔다. 애매한 값으로 매일 터지면 알림을 믿지 않게 된다.
        self.check(5_300, 10_600)

    def test_늘어나는_것은_막지_않는다(self):
        self.check(21_000, 10_600)

    def test_allow_shrink_면_줄어도_통과하되_경고는_남는다(self):
        # 조용히 넘어가면 안 된다. 사람이 허락한 것이지 정상인 것은 아니다.
        with self.assertLogs(sanity.log, "WARNING"):
            self.check(12, 10_600, allow_shrink=True)

    def test_allow_shrink_여도_0건은_실패다(self):
        # 사람이 "줄어도 좋다"고 한 것이지 "비어도 좋다"고 한 것이 아니다.
        with self.assertRaises(SanityError):
            self.check(0, 10_600, allow_shrink=True)

    def test_실패_메시지에_두_숫자가_다_들어간다(self):
        with self.assertRaises(SanityError) as caught:
            self.check(12, 10_600)
        message = str(caught.exception)
        self.assertIn("10,600", message)
        self.assertIn("12", message)
        self.assertIn("--allow-shrink", message)


class FakeTable:
    """supabase 클라이언트의 체이닝 흉내. 마지막 execute() 만 값을 낸다."""

    def __init__(self, data, raises=False):
        self._data = data
        self._raises = raises

    def select(self, *a, **k): return self
    def eq(self, *a, **k): return self
    def order(self, *a, **k): return self
    def limit(self, *a, **k): return self

    def execute(self):
        if self._raises:
            raise RuntimeError("네트워크 끊김")
        return type("R", (), {"data": self._data})()


class FakeClient:
    def __init__(self, data, raises=False):
        self._table = FakeTable(data, raises)

    def table(self, name):
        return self._table


class LastSuccessTest(unittest.TestCase):
    def test_마지막_성공_행수를_읽는다(self):
        client = FakeClient([{"rows_upserted": 10_617}])
        self.assertEqual(sanity.last_success_rows(client, "animal_hospital"), 10_617)

    def test_기록이_없으면_None(self):
        self.assertIsNone(sanity.last_success_rows(FakeClient([]), "animal_hospital"))

    def test_읽기가_실패해도_ETL_을_멈추지_않는다(self):
        # 안전장치가 본작업을 무너뜨리면 안 된다. 비교를 포기할 뿐이다.
        client = FakeClient(None, raises=True)
        with self.assertLogs(sanity.log, "ERROR"):
            self.assertIsNone(sanity.last_success_rows(client, "animal_hospital"))

    def test_guard_가_직전_성공과_비교해_터진다(self):
        client = FakeClient([{"rows_upserted": 10_617}])
        with self.assertRaises(SanityError):
            sanity.guard(client, "animal_hospital", 12)

    def test_guard_는_평소_건수를_통과시킨다(self):
        client = FakeClient([{"rows_upserted": 10_617}])
        sanity.guard(client, "animal_hospital", 10_600)


if __name__ == "__main__":
    unittest.main()


class FakeLogTable:
    """sync_logs 흉내. insert/update 를 기록해 두었다가 테스트가 들여다본다."""

    def __init__(self, store, last_success):
        self.store = store
        self.last_success = last_success
        self._mode = None
        self._patch = None

    # 읽기 (sanity.last_success_rows)
    def select(self, *a, **k): self._mode = "select"; return self
    def eq(self, *a, **k): return self
    def order(self, *a, **k): return self
    def limit(self, *a, **k): return self

    # 쓰기 (SyncRun)
    def insert(self, row):
        self._mode = "insert"
        self.store["inserted"] = row
        return self

    def update(self, patch):
        self._mode = "update"
        self._patch = patch
        return self

    def execute(self):
        if self._mode == "select":
            return type("R", (), {"data": self.last_success})()
        if self._mode == "insert":
            return type("R", (), {"data": [{"id": 7}]})()
        self.store["updated"] = self._patch
        return type("R", (), {"data": []})()


class FakeLogClient:
    def __init__(self, last_success):
        self.store = {}
        self._table = FakeLogTable(self.store, last_success)

    def table(self, name):
        return self._table


class HospitalsWiringTest(unittest.TestCase):
    """순수 함수가 맞는 것과 그것이 **실제로 불리는 것**은 다른 문제다.

    여기서 지키는 것: 0건이면 `run` 이 터지고, 그 실패가 **sync_logs 에 남는다.**
    SyncRun 밖에서 터뜨리면 그날 ETL 이 아예 안 돈 것처럼 보인다.
    """

    def setUp(self):
        from petetl.sources import hospitals

        self.hospitals = hospitals
        self._saved = (hospitals.fetch_all, hospitals.build_places, hospitals.load_data_go_kr_key)
        hospitals.fetch_all = lambda key, limit=None: []
        hospitals.build_places = lambda client, items_, kakao: ([], {})
        hospitals.load_data_go_kr_key = lambda: "테스트키"

    def tearDown(self):
        (self.hospitals.fetch_all, self.hospitals.build_places,
         self.hospitals.load_data_go_kr_key) = self._saved

    def test_0건이면_run_이_터진다(self):
        client = FakeLogClient(last_success=[{"rows_upserted": 10_617}])
        with self.assertRaises(SanityError):
            self.hospitals.run(client=client)

    def test_그_실패가_sync_logs_에_failed_로_남는다(self):
        client = FakeLogClient(last_success=[{"rows_upserted": 10_617}])
        with self.assertRaises(SanityError):
            self.hospitals.run(client=client)
        self.assertEqual(client.store["updated"]["status"], "failed")
        self.assertIn("0건", client.store["updated"]["error"])

    def test_dry_run_은_DB_를_건드리지_않는다(self):
        # dry-run 은 검사 전에 돌아간다. 확인용 실행이 알림을 울리면 안 된다.
        client = FakeLogClient(last_success=[{"rows_upserted": 10_617}])
        self.hospitals.run(client=client, dry_run=True)
        self.assertNotIn("inserted", client.store)
