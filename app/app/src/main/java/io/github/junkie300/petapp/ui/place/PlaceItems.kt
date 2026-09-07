package io.github.junkie300.petapp.ui.place

import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.distanceMeters
import io.github.junkie300.petapp.ui.theme.CardShape

/**
 * 장소 목록의 항목들 — **목록 화면과 지도 바텀시트가 이걸 같이 쓴다.**
 *
 * 지도에서 핀을 누르면 시트에서 그 카드가 강조된다. 목록 화면에는 강조가 없다(항상 null).
 * 카드가 두 벌이 되면 한쪽만 고치게 되고, 사용자에게는 같은 것이 다르게 보인다 (D-26).
 */
fun LazyListScope.placeItems(
    places: List<Place>,
    onPlaceClick: (Place) -> Unit,
    selectedId: Long? = null,
    /** 현재 위치. null 이면 거리를 적지 않는다 (D-81). */
    origin: GeoPoint? = null,
) = items(places, key = { it.id }) { place ->
    PlaceCard(
        name = place.name,
        address = place.address,
        category = place.placeCategory,
        tel = place.tel,
        onClick = { onPlaceClick(place) },
        // 좌표가 없는 장소가 0.5% 남아 있다 (D-51). 그건 거리도 없다.
        distanceMeters = origin?.let { from ->
            val lat = place.lat
            val lng = place.lng
            if (lat != null && lng != null) distanceMeters(from, lat, lng) else null
        },
        // 색만으로 구분하지 않는다는 규칙(spec.md §6.5)은 지켜진다 — 이건 위치를 가리키는
        // 테두리이고, 어느 카드인지는 지도의 핀과 카드 자체가 함께 말해 준다.
        modifier = if (place.id == selectedId) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape)
        } else {
            Modifier
        },
    )
}
