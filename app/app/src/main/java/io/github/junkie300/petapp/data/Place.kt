package io.github.junkie300.petapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * places 테이블의 앱 쪽 표현 (spec.md §3.2).
 *
 * 카테고리가 달라도 구조가 같으므로 **목록·상세 화면 코드를 1벌만 만든다** (D-26).
 * 필요한 컬럼만 select 한다 — [PlaceRepository.COLUMNS]. `geom` 은 받지 않는다(바이너리다).
 */
@Serializable
data class Place(
    val id: Long,
    /** place_category ENUM 의 원문. [placeCategory] 로 풀어 쓴다. */
    val category: String,
    /** 출처 표기 의무의 근거 (spec.md §8). localdata | mfds | tourapi | ... */
    val source: String,
    val name: String,
    val tel: String? = null,
    @SerialName("address_road") val addressRoad: String? = null,
    @SerialName("address_jibun") val addressJibun: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val status: String,
    /** 카테고리별 추가 필드. 소스마다 키가 달라 타입을 고정하지 않는다. */
    val extra: JsonObject? = null,
    @SerialName("source_updated_at") val sourceUpdatedAt: String? = null,
) {
    /** 모르는 카테고리면 null. 나중에 ENUM 이 늘어도 앱이 죽지 않는다. */
    val placeCategory: PlaceCategory? get() = PlaceCategory.fromDbValue(category)

    /** 목록·상세에 한 줄로 쓰는 주소. 도로명이 없으면 지번으로 떨어진다. */
    val address: String? get() = addressRoad ?: addressJibun

    val hasCoordinates: Boolean get() = lat != null && lng != null

    /** 원본이 쓰는 영업상태 문구("영업/정상"). 없으면 null — 우리가 지어내지 않는다. */
    val salesStatus: String? get() = extraText("sales_status")

    /**
     * 원본 기준일. `2026-03-20T22:02:03+00:00` 에서 **날짜 부분만** 그대로 떼어 쓴다.
     *
     * ⚠️ 시간대 변환을 하면 안 된다. 원본(`DAT_UPDT_PNT`)은 시간대가 없는 값인데 ETL 이
     * UTC 로 못박아 저장했다. KST 로 바꾸면 22:02 가 다음 날로 넘어가 **기준일이 하루 밀린다.**
     */
    val sourceUpdatedDate: String? get() = sourceUpdatedAt?.substringBefore('T')?.ifBlank { null }

    private fun extraText(key: String): String? =
        runCatching { extra?.get(key)?.jsonPrimitive?.content }.getOrNull()?.ifBlank { null }
}
