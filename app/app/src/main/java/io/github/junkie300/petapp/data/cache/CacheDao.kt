package io.github.junkie300.petapp.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * 오프라인 캐시의 유일한 출입구 (spec.md §5.3).
 *
 * 인터페이스가 아니라 abstract class 다 — `@Transaction` 이 붙은 **본문 있는 메서드**를
 * 두기 위해서다. 목록 캐시는 "지우고 다시 넣기"라 두 문장이 반드시 한 덩어리여야 한다.
 */
@Dao
abstract class CacheDao {

    // ---- regions --------------------------------------------------------
    // 이 표는 비우지 않는다. 이유는 CachedRegion 주석 참고.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putRegions(regions: List<CachedRegion>)

    @Query("SELECT * FROM cached_regions WHERE code IN (:codes)")
    abstract suspend fun regionsByCodes(codes: List<String>): List<CachedRegion>

    @Query("SELECT * FROM cached_regions WHERE level = :level ORDER BY full_name")
    abstract suspend fun regionsByLevel(level: Int): List<CachedRegion>

    @Query("SELECT * FROM cached_regions WHERE parent_code = :parentCode ORDER BY full_name")
    abstract suspend fun regionChildren(parentCode: String): List<CachedRegion>

    /** 오프라인 검색은 **이미 받아 둔 읍면동** 안에서만 찾는다. 전국이 다 들어 있지 않다. */
    @Query(
        "SELECT * FROM cached_regions WHERE level = :level AND full_name LIKE '%' || :keyword || '%' " +
            "ORDER BY full_name LIMIT :limit",
    )
    abstract suspend fun searchRegions(keyword: String, level: Int, limit: Int): List<CachedRegion>

    // ---- places ---------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPlaces(places: List<CachedPlace>)

    @Query("DELETE FROM cached_places WHERE region_code = :regionCode AND category = :category")
    abstract suspend fun clearPlacesIn(regionCode: String, category: String)

    @Query("SELECT * FROM cached_places WHERE region_code = :regionCode AND category = :category ORDER BY name")
    abstract suspend fun placesIn(regionCode: String, category: String): List<CachedPlace>

    @Query("SELECT * FROM cached_places WHERE place_id = :placeId")
    abstract suspend fun placeById(placeId: Long): CachedPlace?

    /** 서버가 "그런 장소 없다"고 답하면 사본도 지운다. 안 지우면 폐업한 곳이 캐시로 되살아난다. */
    @Query("DELETE FROM cached_places WHERE place_id = :placeId")
    abstract suspend fun deletePlace(placeId: Long)

    /** 오래된 것부터 밀어낸다. 사용자가 만든 것이 아니므로 지워도 잃는 게 없다. */
    @Query(
        "DELETE FROM cached_places WHERE place_id NOT IN " +
            "(SELECT place_id FROM cached_places ORDER BY cached_at DESC LIMIT :keep)",
    )
    abstract suspend fun trimPlaces(keep: Int)

    /**
     * 한 읍면동·한 카테고리의 목록을 **통째로 갈아 끼운다.**
     *
     * 덮어쓰기만 하면 폐업으로 목록에서 빠진 곳이 캐시에 남아 오프라인에서만 계속 보인다.
     * "마지막 조회 결과"가 캐시의 정의이므로(§5.3), 그때 없던 것은 캐시에도 없어야 한다.
     */
    @Transaction
    open suspend fun replacePlacesIn(regionCode: String, category: String, places: List<CachedPlace>) {
        clearPlacesIn(regionCode, category)
        putPlaces(places)
        trimPlaces(MAX_CACHED_PLACES)
    }

    // ---- counts ---------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCounts(counts: List<CachedCount>)

    @Query("SELECT * FROM cached_counts WHERE region_code = :regionCode")
    abstract suspend fun countsIn(regionCode: String): List<CachedCount>

    companion object {
        /**
         * 캐시에 둘 장소 수의 상한.
         *
         * 읍면동 하나의 한 카테고리가 수십 곳이므로 2,000이면 **여행 준비 중 오간 동네
         * 수십 곳**이 남는다. 전국 10,617건을 통째로 들고 있을 이유는 없다 — 그건 캐시가
         * 아니라 사본이고, 기준일이 낡는 순간 의무 표기(§5.2)가 거짓이 된다.
         */
        const val MAX_CACHED_PLACES = 2_000
    }
}
