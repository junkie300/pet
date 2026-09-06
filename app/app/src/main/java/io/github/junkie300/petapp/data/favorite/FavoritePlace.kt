package io.github.junkie300.petapp.data.favorite

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceExtra

/**
 * 즐겨찾기 한 줄 (spec.md §5.2 S-07). **로컬 저장이며 로그인이 필요 없다.**
 *
 * id 만 저장하지 않고 **장소 한 건을 통째로 베껴 둔다.** 즐겨찾기는 "이따 다시 볼 곳"이라
 * 지하철·산속처럼 網이 없는 곳에서 열릴 확률이 높다. id 만 있으면 그때 빈 목록이 뜬다.
 * 원본이 바뀌면 값이 낡을 수 있지만, **낡은 이름이 빈 화면보다 낫다** — 상세를 열면 최신으로 덮인다.
 *
 * 오프라인 캐시(`data/cache/`)와 겹쳐 보이지만 역할이 다르다. 캐시는 언제 지워도 되는 사본이고,
 * 이 표는 **사용자가 만든 것**이라 지우지 않는다. 그래서 캐시를 참조하지 않고 자기 사본을 갖는다.
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

    // ↓ 여기부터는 DB 버전 2 에서 더한 칸이다 (D-61 의 한계를 푸는 것).
    //
    // 상세 화면에는 **출처와 기준일이 의무 표기**다 (spec.md §5.2 · §8). 버전 1 의 스냅샷에는
    // 그게 없어서 오프라인에서 즐겨찾기를 눌러도 상세를 그릴 수 없었다 — 지어내면 거짓 표기가 된다.
    //
    // ⚠️ 새 칸은 **반드시 뒤에 붙인다.** 마이그레이션의 ALTER TABLE ADD COLUMN 이 열을
    // 뒤에 붙이므로, 여기 순서도 같아야 새로 깐 기기와 올려 깐 기기의 표가 같아진다.
    val source: String? = null,
    @ColumnInfo(name = "address_road") val addressRoad: String? = null,
    @ColumnInfo(name = "address_jibun") val addressJibun: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val status: String? = null,
    val extra: String? = null,
    @ColumnInfo(name = "source_updated_at") val sourceUpdatedAt: String? = null,
) {
    /**
     * 스냅샷만으로 상세 화면을 그릴 수 있으면 [Place] 로, 아니면 null.
     *
     * **버전 1 때 담아 둔 즐겨찾기는 null 이 된다** — 출처가 없기 때문이다. 그 경우 상세는
     * 종전대로 실패 화면을 낸다. 출처를 "공공데이터" 같은 말로 메우면 화면은 채워지지만
     * 그건 **없는 사실을 적는 것**이고, 이 앱이 신뢰를 얻는 유일한 근거를 깎는다.
     */
    fun toPlace(): Place? {
        val source = source ?: return null
        val status = status ?: return null
        return Place(
            id = placeId,
            category = category,
            source = source,
            name = name,
            tel = tel,
            // 버전 1 스냅샷은 도로명/지번이 하나로 합쳐져 있었다. 그건 위에서 이미 걸러진다.
            addressRoad = addressRoad,
            addressJibun = addressJibun,
            lat = lat,
            lng = lng,
            status = status,
            extra = PlaceExtra.decode(extra),
            sourceUpdatedAt = sourceUpdatedAt,
        )
    }

    companion object {
        fun from(place: Place, savedAt: Long = System.currentTimeMillis()) = FavoritePlace(
            placeId = place.id,
            category = place.category,
            name = place.name,
            address = place.address,
            tel = place.tel,
            savedAt = savedAt,
            source = place.source,
            addressRoad = place.addressRoad,
            addressJibun = place.addressJibun,
            lat = place.lat,
            lng = place.lng,
            status = place.status,
            extra = PlaceExtra.encode(place.extra),
            sourceUpdatedAt = place.sourceUpdatedAt,
        )
    }
}
