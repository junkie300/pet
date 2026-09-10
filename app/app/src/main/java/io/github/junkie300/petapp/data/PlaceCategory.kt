package io.github.junkie300.petapp.data

/**
 * places.category ENUM 의 앱 쪽 표현 (spec.md §3.2).
 *
 * 다섯 카테고리가 **한 테이블·한 화면 코드**를 공유한다는 것이 이 프로젝트의 재사용 전략이다
 * (D-26 · plan.md 2단계 완료 기준 — "앱 코드 수정이 필터 칩 1줄 추가 수준으로 끝나야 한다").
 */
enum class PlaceCategory(val dbValue: String, val loaded: Boolean) {

    // loaded = 그 카테고리의 ETL 이 실제로 적재를 끝냈는가.
    //
    // ⚠️ 이 값이 왜 있는가: 적재 전 카테고리의 건수를 조회하면 전국 어디서나 0 이 나온다.
    // 홈 그리드에 "미용 0곳" 이라고 적으면 사용자는 **"이 동네엔 미용실이 없구나"** 로 읽는다.
    // spec.md §5.3 이 "데이터 없음"과 "불러오기 실패"를 가르라고 한 것과 같은 이유로,
    // "이 동네에 없다"와 "우리가 아직 안 실었다"도 갈라야 한다. 앞은 0곳, 뒤는 "준비 중" 이다.
    //
    // 단계별 ETL 이 끝나면 여기 한 줄만 true 로 바꾼다.
    HOSPITAL("hospital", loaded = true),                 // 1단계 — 10,617건 적재 완료
    GROOMING("grooming", loaded = true),                 // 2단계 — 16,160건 적재 완료 (D-95)
    RESTAURANT("restaurant", loaded = false),            // 3단계
    TOUR("tour", loaded = false),                        // 4단계
    WILDLIFE_CENTER("wildlife_center", loaded = false),  // 5단계
    ;

    companion object {
        /** 화면 간 이동에 카테고리를 실어 보낼 때 쓴다. 모르는 값이면 null — 앱을 죽이지 않는다. */
        fun fromDbValue(value: String?): PlaceCategory? = entries.firstOrNull { it.dbValue == value }

        /** 지금 조회해도 되는 카테고리. 적재 안 된 카테고리는 질의 자체를 하지 않는다. */
        val loadedEntries: List<PlaceCategory> get() = entries.filter { it.loaded }
    }
}
