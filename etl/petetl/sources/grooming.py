# -*- coding: utf-8 -*-
"""2단계 · places(category='grooming') — 행안부 동물미용업 조회서비스(15154944).

**이 파일이 짧다는 것 자체가 `plan.md` 2단계의 완료 기준이다** (D-95).
동물병원(15154952)과 같은 기관(`1741000`)의 형제 서비스라 응답 필드·좌표계(EPSG:5174)·
영업상태코드가 전부 같다. 그래서 변환 코드는 한 줄도 베끼지 않고 `hospitals` 의 것을
그대로 쓰고, 다른 것은 아래 `GROOMING` 한 덩어리뿐이다.

⚠️ **다만 하나가 다르다 — 주소의 번지가 가려져 온다.**

    경기도 오산시 갈곶동 *** *층
    인천광역시 연수구 송도동 **-* 더샵 송도 아크베이 ***동 ***호

병원 쪽은 `경기도 광명시 광명동 155-3` 처럼 온전히 온다(실측 0% 마스킹). 미용은 **100%** 다.
읍면동 배정은 앞 토큰만 쓰므로 멀쩡하지만, **좌표 지오코딩은 못 한다** — 카카오에 물으면
잘해야 읍면동 중심이 돌아오고, 그것을 가게 좌표로 적으면 없는 정밀도를 지어내는 것이다.
그래서 `geocode_coords=False` 다. 좌표가 빈 5%는 **좌표 없이** 실린다 (목록·상세에는
그대로 나오고 지도에만 안 뜬다 — 거짓 핀보다 낫다).
"""

from __future__ import annotations

from .hospitals import Dataset, run as _run

GROOMING = Dataset(
    sync_source="animal_grooming",
    dataset="15154944",
    category="grooming",
    url="https://apis.data.go.kr/1741000/pet_grooming/info",
    # ⚠️ 주소가 가려져 온다. 위 설명 참고 — 바꾸기 전에 반드시 읽을 것.
    geocode_coords=False,
)


def run(client=None, dry_run: bool = False, limit: int | None = None,
        allow_shrink: bool = False) -> list[dict]:
    return _run(client=client, dry_run=dry_run, limit=limit,
                allow_shrink=allow_shrink, ds=GROOMING)
