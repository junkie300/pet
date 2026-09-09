package io.github.junkie300.petapp.ui.map

import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.distanceMeters

/**
 * 「이 지역에서 다시 검색」이 뜰 때 (spec.md §5.2 · D-86).
 *
 * ⚠️ **경계를 모른다.** `regions` 에는 읍면동 **중심좌표만** 있고 다각형이 없다 — "지도가 지역
 * 밖으로 나갔는가"를 물을 방법이 애초에 없다. 그래서 **고른 지역 중심에서 얼마나 멀어졌는가**로
 * 바꿔 묻는다. 이 값이 곧 규칙이 되므로 한 곳에 두고 이름을 붙인다.
 */
object MapRescan {

    /**
     * 이만큼 멀어지면 버튼이 뜬다.
     *
     * 읍면동 하나가 대략 지름 2~4km 다. 3km 면 도시에서는 두세 동 건너간 거리이고, 시골에서는
     * 아직 같은 면 안일 수 있다 — 하나의 값으로 전국을 덮는 가장 무난한 선이다. 더 짧게 잡으면
     * (1.5km) 조금만 밀어도 떠서 성가시고, 길게 잡으면 딴 동네를 보면서도 안 뜬다.
     */
    const val TRIGGER_METERS = 3_000.0

    /**
     * @param regionCenter 고른 읍면동의 중심. 중심좌표가 없는 지역이 남아 있어 null 일 수 있고,
     *   그때는 잴 기준이 없으므로 묻지 않는다.
     * @param mapCenter 지금 지도 한가운데. 카메라가 멈출 때마다 갱신된다.
     */
    fun shouldOffer(regionCenter: GeoPoint?, mapCenter: GeoPoint?): Boolean {
        if (regionCenter == null || mapCenter == null) return false
        return distanceMeters(regionCenter, mapCenter.latitude, mapCenter.longitude) > TRIGGER_METERS
    }
}
