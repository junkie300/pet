package io.github.junkie300.petapp.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceExtra

/**
 * 마지막으로 불러온 `places` 행 (spec.md §5.3 오프라인).
 *
 * ⚠️ **즐겨찾기와 역할이 다르다.** 즐겨찾기는 사용자가 만든 것이라 영구히 남지만,
 * 이 표는 언제 지워도 되는 사본이다([CacheDao.trimPlaces]). 그래서 즐겨찾기가 이 표를
 * 참조하지 않고 자기 스냅샷을 따로 갖는다 (D-60).
 *
 * 상세만 열어 캐시된 행은 어느 읍면동인지 모른다 — 상세 조회는 `region_code` 를 받지 않기
 * 때문이다. 그런 행은 [regionCode] 가 null 이고 목록 캐시에는 잡히지 않는다.
 */
@Entity(tableName = "cached_places")
data class CachedPlace(
    @PrimaryKey @ColumnInfo(name = "place_id") val placeId: Long,
    @ColumnInfo(name = "region_code", index = true) val regionCode: String?,
    val category: String,
    val source: String,
    val name: String,
    val tel: String?,
    @ColumnInfo(name = "address_road") val addressRoad: String?,
    @ColumnInfo(name = "address_jibun") val addressJibun: String?,
    val lat: Double?,
    val lng: Double?,
    val status: String,
    /** `places.extra` 원문 JSON. 영업상태가 여기 들어 있다. */
    val extra: String?,
    @ColumnInfo(name = "source_updated_at") val sourceUpdatedAt: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
) {
    fun toPlace() = Place(
        id = placeId,
        category = category,
        source = source,
        name = name,
        tel = tel,
        addressRoad = addressRoad,
        addressJibun = addressJibun,
        lat = lat,
        lng = lng,
        status = status,
        extra = PlaceExtra.decode(extra),
        sourceUpdatedAt = sourceUpdatedAt,
    )

    companion object {
        fun from(place: Place, regionCode: String?, cachedAt: Long) = CachedPlace(
            placeId = place.id,
            regionCode = regionCode,
            category = place.category,
            source = place.source,
            name = place.name,
            tel = place.tel,
            addressRoad = place.addressRoad,
            addressJibun = place.addressJibun,
            lat = place.lat,
            lng = place.lng,
            status = place.status,
            extra = PlaceExtra.encode(place.extra),
            sourceUpdatedAt = place.sourceUpdatedAt,
            cachedAt = cachedAt,
        )
    }
}
