package io.github.junkie300.petapp.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.junkie300.petapp.data.Region

/**
 * 한 번이라도 불러온 `regions` 행 (spec.md §5.3 오프라인).
 *
 * 지역 이름이 없으면 오프라인에서 **화면 머리말부터 비어** 홈도 목록도 그릴 수 없다.
 * 그래서 지역은 장소보다 먼저 캐시한다.
 *
 * 이 표는 **비우지 않는다.** 전국을 다 훑어도 5,339행(시도 16 · 시군구 256 · 읍면동 5,067)이고
 * 컬럼도 짧다. 오래된 행을 지우는 규칙을 두면 하필 시도 16개가 밀려 나가 3단 드롭다운의
 * 첫 칸이 비는 쪽이 더 나쁘다.
 */
@Entity(tableName = "cached_regions")
data class CachedRegion(
    @PrimaryKey val code: String,
    val level: Int,
    @ColumnInfo(name = "parent_code", index = true) val parentCode: String?,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "sido_name") val sidoName: String,
    @ColumnInfo(name = "sigungu_name") val sigunguName: String?,
    @ColumnInfo(name = "dong_name") val dongName: String?,
    @ColumnInfo(name = "center_lat") val centerLat: Double?,
    @ColumnInfo(name = "center_lng") val centerLng: Double?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
) {
    fun toRegion() = Region(
        code = code,
        level = level,
        parentCode = parentCode,
        fullName = fullName,
        sidoName = sidoName,
        sigunguName = sigunguName,
        dongName = dongName,
        centerLat = centerLat,
        centerLng = centerLng,
    )

    companion object {
        fun from(region: Region, cachedAt: Long) = CachedRegion(
            code = region.code,
            level = region.level,
            parentCode = region.parentCode,
            fullName = region.fullName,
            sidoName = region.sidoName,
            sigunguName = region.sigunguName,
            dongName = region.dongName,
            centerLat = region.centerLat,
            centerLng = region.centerLng,
            cachedAt = cachedAt,
        )
    }
}
