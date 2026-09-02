# -*- coding: utf-8 -*-
"""0단계 매핑 로직 테스트.

네트워크와 DB 없이 도는 것만 담는다. 실제 매핑 결과 검증은
`python run.py mapping --dry-run` 의 '미매핑 0건' 으로 확인한다.
"""

import logging
import unittest

from petetl.config import normalize_service_key
from petetl.publicapi import PublicApiError, _check, items
from petetl.sources import mapping


class ParentCityTest(unittest.TestCase):
    def test_일반구는_모시로_접는다(self):
        # 두 API 모두 '성남시'까지만 있고 일반구가 없다
        self.assertEqual(mapping.parent_city("성남시 분당구"), "성남시")
        self.assertEqual(mapping.parent_city("창원시 마산회원구"), "창원시")

    def test_일반구가_아니면_그대로_둔다(self):
        self.assertEqual(mapping.parent_city("목포시"), "목포시")
        self.assertEqual(mapping.parent_city("강남구"), "강남구")
        self.assertEqual(mapping.parent_city("옹진군"), "옹진군")


class TourSidoNamesTest(unittest.TestCase):
    def test_광역시_특별시는_접미사를_뗀_후보도_낸다(self):
        # TourAPI 는 '서울', '부산' 처럼 짧게 쓴다
        self.assertEqual(mapping.tour_sido_names("서울특별시"), ("서울특별시", "서울"))
        self.assertEqual(mapping.tour_sido_names("부산광역시"), ("부산광역시", "부산"))

    def test_도는_그대로_쓴다(self):
        self.assertEqual(mapping.tour_sido_names("경기도"), ("경기도",))
        self.assertEqual(mapping.tour_sido_names("강원특별자치도"), ("강원특별자치도",))

    def test_세종은_접미사를_떼면_안_된다(self):
        # '세종특별자치시'.replace 로 잘못 처리하면 '세종자치시' 가 되어 안 맞는다
        self.assertIn("세종특별자치시", mapping.tour_sido_names("세종특별자치시"))

    def test_통합시도는_옛_코드_두_개로_갈린다(self):
        # D-29: TourAPI 는 아직 광주/전남을 나눠서 쓴다
        self.assertEqual(
            mapping.tour_sido_names("전남광주통합특별시"), ("광주", "전라남도")
        )


class TourSigunguNameTest(unittest.TestCase):
    def test_신설_자치구는_옛_이름으로_되돌린다(self):
        # 인천 2026 개편을 TourAPI 가 아직 반영하지 않았다
        self.assertEqual(mapping.tour_sigungu_name("인천광역시", "제물포구"), "중구")
        self.assertEqual(mapping.tour_sigungu_name("인천광역시", "영종구"), "중구")
        self.assertEqual(mapping.tour_sigungu_name("인천광역시", "서해구"), "서구")
        self.assertEqual(mapping.tour_sigungu_name("인천광역시", "검단구"), "서구")

    def test_같은_이름이라도_다른_시도면_별칭을_쓰지_않는다(self):
        # '중구' 는 전국에 여럿이다. 시도까지 같이 봐야 한다
        self.assertEqual(mapping.tour_sigungu_name("서울특별시", "중구"), "중구")
        self.assertEqual(mapping.tour_sigungu_name("대구광역시", "서구"), "서구")

    def test_별칭이_없으면_일반구_접기만_한다(self):
        self.assertEqual(mapping.tour_sigungu_name("경기도", "성남시 분당구"), "성남시")


class ItemsTest(unittest.TestCase):
    def test_목록을_그대로_돌려준다(self):
        self.assertEqual(items({"items": {"item": [{"a": 1}, {"a": 2}]}}), [{"a": 1}, {"a": 2}])

    def test_1건이면_객체_하나로_오는_서비스가_있다(self):
        self.assertEqual(items({"items": {"item": {"a": 1}}}), [{"a": 1}])

    def test_0건이면_items_가_빈_객체나_빈_문자열로_온다(self):
        self.assertEqual(items({"items": {}}), [])
        self.assertEqual(items({"items": ""}), [])
        self.assertEqual(items({}), [])


class CheckTest(unittest.TestCase):
    def test_정상응답은_body_를_돌려준다(self):
        body = _check({"response": {"header": {"resultCode": "00"}, "body": {"x": 1}}}, "u")
        self.assertEqual(body, {"x": 1})

    def test_TourAPI_는_resultCode_가_네_자리다(self):
        body = _check({"response": {"header": {"resultCode": "0000"}, "body": {"x": 1}}}, "u")
        self.assertEqual(body, {"x": 1})

    def test_오류본문은_HTTP200_이어도_예외로_바꾼다(self):
        payload = {
            "OpenAPI_ServiceResponse": {
                "cmmMsgHeader": {"errMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                                 "returnAuthMsg": "등록되지 않은 서비스키", "returnReasonCode": "30"}
            }
        }
        with self.assertRaises(PublicApiError) as ctx:
            _check(payload, "u")
        self.assertIn("활용신청", str(ctx.exception))  # 원인 안내가 붙어야 한다

    def test_resultCode_가_정상값이_아니면_예외다(self):
        with self.assertRaises(PublicApiError):
            _check({"response": {"header": {"resultCode": "99", "resultMsg": "X"}}}, "u")


class ServiceKeyTest(unittest.TestCase):
    def setUp(self):
        # Encoding 키 경고가 테스트 출력에 섞이지 않게 한다
        logging.disable(logging.WARNING)
        self.addCleanup(logging.disable, logging.NOTSET)

    def test_Encoding_키는_Decoding_으로_되돌린다(self):
        self.assertEqual(normalize_service_key("ab%2Fcd%2Bef%3D%3D"), "ab/cd+ef==")

    def test_Decoding_키는_건드리지_않는다(self):
        # base64 에는 '%' 가 없으므로 그대로 둔다
        self.assertEqual(normalize_service_key("ab/cd+ef=="), "ab/cd+ef==")

    def test_따옴표와_공백을_떼어낸다(self):
        self.assertEqual(normalize_service_key('  "abc"  '), "abc")


if __name__ == "__main__":
    unittest.main()
