package io.github.junkie300.petapp.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 홈(S-00)의 카테고리 건수 한 칸.
 *
 * 건수는 목록에서 세지 않는다. 목록을 한 번도 열지 않은 카테고리도 홈에는 숫자가 떠야 하고,
 * 반대로 목록 캐시가 비었다고 해서 **0곳이라고 적으면 안 되기 때문**이다 (D-53).
 * 여기 저장된 0 은 서버가 실제로 준 0 이다.
 */
@Entity(tableName = "cached_counts", primaryKeys = ["region_code", "category"])
data class CachedCount(
    @ColumnInfo(name = "region_code") val regionCode: String,
    val category: String,
    val count: Int,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)
