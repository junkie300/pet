package io.github.junkie300.petapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * regions 테이블의 앱 쪽 표현 (spec.md §3.1).
 *
 * 앱은 외부 코드 컬럼(apms_*, tour_*, localdata_cd)을 쓰지 않는다. 그건 ETL 의 몫이다.
 * 필요한 것만 select 해서 전송량을 줄인다 — [RegionRepository.COLUMNS].
 */
@Serializable
data class Region(
    val code: String,
    val level: Int,
    @SerialName("parent_code") val parentCode: String? = null,
    @SerialName("full_name") val fullName: String,
    @SerialName("sido_name") val sidoName: String,
    @SerialName("sigungu_name") val sigunguName: String? = null,
    @SerialName("dong_name") val dongName: String? = null,
    @SerialName("center_lat") val centerLat: Double? = null,
    @SerialName("center_lng") val centerLng: Double? = null,
) {
    /** 드롭다운 한 줄에 보이는 이름. 전체 이름이 아니라 그 단계의 이름만 보여준다. */
    val shortName: String
        get() = when (level) {
            1 -> sidoName
            2 -> sigunguName ?: fullName
            else -> dongName ?: fullName
        }

    /**
     * 중심좌표. 지도 초기 위치와 「이 지역에서 다시 검색」의 기준점이다 (D-86).
     *
     * **null 일 수 있다** — 중심좌표가 아직 채워지지 않은 지역이 남아 있다 (0단계 잔여분).
     */
    val center: GeoPoint? get() {
        val lat = centerLat ?: return null
        val lng = centerLng ?: return null
        return GeoPoint(lat, lng)
    }

    companion object {
        const val LEVEL_SIDO = 1
        const val LEVEL_SIGUNGU = 2
        const val LEVEL_DONG = 3
    }
}

/**
 * [point] 에 가장 가까운 지역. 중심좌표가 없는 지역은 잴 수 없으므로 셈에서 빠진다.
 *
 * ⚠️ **"가장 가까운 중심"이지 "그 점을 품는 지역"이 아니다.** 경계 다각형이 없으므로 (D-86)
 * 여기가 우리가 답할 수 있는 최선이다 — 동네 경계 근처에서는 옆 동이 나올 수 있다.
 */
fun List<Region>.nearestTo(point: GeoPoint): Region? = minByOrNull { region ->
    val center = region.center ?: return@minByOrNull Double.MAX_VALUE
    distanceMeters(point, center.latitude, center.longitude)
}?.takeIf { it.center != null }
