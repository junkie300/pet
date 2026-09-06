package io.github.junkie300.petapp.data.favorite

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite_places ORDER BY saved_at DESC")
    fun observeAll(): Flow<List<FavoritePlace>>

    /** 상세 화면의 별 모양이 이걸 본다. 다른 화면에서 담고 지워도 따라온다. */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_places WHERE place_id = :placeId)")
    fun observeIsFavorite(placeId: Long): Flow<Boolean>

    /** 같은 장소를 다시 담으면 최신 이름·주소로 덮는다. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoritePlace)

    @Query("DELETE FROM favorite_places WHERE place_id = :placeId")
    suspend fun remove(placeId: Long)

    /**
     * 담아 둔 스냅샷 한 줄. **오프라인에서 상세를 여는 마지막 수단**이다 —
     * 서버도 캐시도 못 주면 여기서 꺼낸다 ([FavoritePlace.toPlace]).
     */
    @Query("SELECT * FROM favorite_places WHERE place_id = :placeId")
    suspend fun byId(placeId: Long): FavoritePlace?
}
