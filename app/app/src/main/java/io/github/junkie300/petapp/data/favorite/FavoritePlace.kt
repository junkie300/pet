package io.github.junkie300.petapp.data.favorite

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.junkie300.petapp.data.Place

/**
 * 즐겨찾기 한 줄 (spec.md §5.2 S-07). **로컬 저장이며 로그인이 필요 없다.**
 *
 * id 만 저장하지 않고 **이름·주소를 함께 베껴 둔다.** 즐겨찾기는 "이따 다시 볼 곳"이라
 * 지하철·산속처럼 網이 없는 곳에서 열릴 확률이 높다. id 만 있으면 그때 빈 목록이 뜬다.
 * 원본이 바뀌면 값이 낡을 수 있지만, **낡은 이름이 빈 화면보다 낫다** — 상세를 열면 최신으로 덮인다.
 */
@Entity(tableName = "favorite_places")
data class FavoritePlace(
    @PrimaryKey @ColumnInfo(name = "place_id") val placeId: Long,
    val category: String,
    val name: String,
    val address: String?,
    val tel: String?,
    /** 최근에 담은 것이 위로 온다. */
    @ColumnInfo(name = "saved_at") val savedAt: Long,
) {
    companion object {
        fun from(place: Place, savedAt: Long = System.currentTimeMillis()) = FavoritePlace(
            placeId = place.id,
            category = place.category,
            name = place.name,
            address = place.address,
            tel = place.tel,
            savedAt = savedAt,
        )
    }
}
