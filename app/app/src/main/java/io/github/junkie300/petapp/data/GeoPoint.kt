package io.github.junkie300.petapp.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 위경도 한 점. 현재 위치와 장소 좌표가 같은 타입을 쓴다. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 두 점 사이 거리(m) — 하버사인.
 *
 * 지구를 공으로 보고 재므로 오차가 0.5% 안쪽이다. 카드에 `320m` 라고 적는 데에는 넘치고,
 * 길을 따라가는 거리(도보·차량)와는 애초에 다른 값이다 — **직선 거리임을 화면에서 숨기지 않는다.**
 */
fun distanceMeters(from: GeoPoint, toLatitude: Double, toLongitude: Double): Double {
    val dLat = Math.toRadians(toLatitude - from.latitude)
    val dLng = Math.toRadians(toLongitude - from.longitude)
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(toLatitude)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a).coerceAtMost(1.0))
}

/**
 * 카드에 적을 거리 문구 (`spec.md §6.4`).
 *
 * **잴 수 있는 것보다 더 정확한 척하지 않는다.** 하버사인 오차와 기기 위치 오차가 수십 m 인데
 * `327m` 라고 적으면 그 자리에 서 있는 것처럼 읽힌다.
 * - 1km 미만은 10m 단위 — `320m`
 * - 10km 미만은 소수 한 자리 — `1.2km`
 * - 그 위는 정수 — `12km`
 */
fun formatDistance(meters: Double): String {
    // ⚠️ 반올림을 **먼저** 한다. 나중에 하면 999m 가 `1000m` 로 나온다 — 그 자리는 `1.0km` 다.
    val tens = (meters / 10).roundToInt() * 10
    return when {
        tens < METERS_IN_KM -> "${tens}m"
        meters < TENS_OF_KM -> "${((meters / METERS_IN_KM) * 10).roundToInt() / 10.0}km"
        else -> "${(meters / METERS_IN_KM).roundToInt()}km"
    }
}

private const val EARTH_RADIUS_M = 6_371_000.0
private const val METERS_IN_KM = 1_000.0
private const val TENS_OF_KM = 10_000.0
