package io.github.junkie300.petapp

import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.distanceMeters
import io.github.junkie300.petapp.data.formatDistance
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 카드의 거리 (`spec.md §6.4`, D-81).
 *
 * 위치 자체는 기기가 있어야 얻지만, **재는 법과 적는 법**은 여기서 다 잡힌다.
 */
class DistanceTest {

    /** 강남역 · 삼성역 — 지하철 두 정거장, 실제로 약 3.4km 떨어져 있다. */
    private val gangnam = GeoPoint(37.4979, 127.0276)
    private val samsung = GeoPoint(37.5088, 127.0631)

    @Test
    fun `아는 두 점 사이 거리가 맞다`() {
        val meters = distanceMeters(gangnam, samsung.latitude, samsung.longitude)

        // 하버사인은 지구를 공으로 보므로 오차가 있다. 100m 안쪽이면 카드에 적기 충분하다.
        assertEquals(3_360.0, meters, 100.0)
    }

    @Test
    fun `같은 점은 0 이다`() {
        assertEquals(0.0, distanceMeters(gangnam, gangnam.latitude, gangnam.longitude), 0.001)
    }

    /** 방향이 바뀌어도 같은 값이어야 한다. 부호나 위경도 순서를 바꿔 쓰면 여기서 걸린다. */
    @Test
    fun `거리는 양쪽이 같다`() {
        assertEquals(
            distanceMeters(gangnam, samsung.latitude, samsung.longitude),
            distanceMeters(samsung, gangnam.latitude, gangnam.longitude),
            0.001,
        )
    }

    /**
     * ⚠️ **잴 수 있는 것보다 정확한 척하지 않는다.** 기기 위치 오차만 수십 m 인데
     * `327m` 라고 적으면 그 자리에 서 있는 것처럼 읽힌다.
     */
    @Test
    fun `1km 미만은 10m 단위로 적는다`() {
        assertEquals("0m", formatDistance(0.0))
        assertEquals("50m", formatDistance(45.0))
        assertEquals("320m", formatDistance(324.0))
        assertEquals("990m", formatDistance(987.0))
    }

    /** ⚠️ 반올림해서 1000m 가 되는 자리는 `1000m` 가 아니라 `1.0km` 다. */
    @Test
    fun `1km 언저리에서 단위가 바뀐다`() {
        assertEquals("1.0km", formatDistance(999.0))
        assertEquals("1.0km", formatDistance(1_000.0))
        assertEquals("1.2km", formatDistance(1_240.0))
    }

    @Test
    fun `10km 부터는 정수로 적는다`() {
        assertEquals("9.9km", formatDistance(9_900.0))
        assertEquals("10km", formatDistance(10_000.0))
        assertEquals("42km", formatDistance(42_195.0))
    }
}
