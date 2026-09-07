package io.github.junkie300.petapp

import android.content.Context
import io.github.junkie300.petapp.data.DeviceLocation
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.data.SupabaseProvider
import io.github.junkie300.petapp.data.cache.CacheDao
import io.github.junkie300.petapp.data.favorite.FavoriteDao
import io.github.junkie300.petapp.data.local.PetDatabase

/**
 * 수동 DI 컨테이너.
 *
 * Hilt 를 쓰지 않는다 (spec.md §1.1) — 첫 안드로이드 프로젝트에서 배울 것을
 * Kotlin/Compose 로 한정하기 위해서다. 화면이 10개를 넘으면 재검토한다.
 */
class AppContainer(context: Context) {
    val isConfigured: Boolean = SupabaseProvider.isConfigured

    // 로컬 DB 하나에 **사용자가 만든 것**(즐겨찾기)과 **받아 둔 사본**(오프라인 캐시)이 함께 있다.
    // 즐겨찾기가 Supabase 가 아니라 여기 있는 것은 로그인이 없기 때문이다 (spec.md §5.2 S-07).
    private val database: PetDatabase by lazy { PetDatabase.create(context.applicationContext) }
    val favoriteDao: FavoriteDao by lazy { database.favorites() }
    private val cacheDao: CacheDao by lazy { database.cache() }

    val regionRepository: RegionRepository by lazy { RegionRepository(SupabaseProvider.client, cacheDao) }
    val placeRepository: PlaceRepository by lazy { PlaceRepository(SupabaseProvider.client, cacheDao) }
    val recentRegionStore: RecentRegionStore = RecentRegionStore(context.applicationContext)

    /** 거리 표기용 현재 위치. 권한이 없으면 조용히 null 을 준다 (D-81). */
    val deviceLocation: DeviceLocation = DeviceLocation(context.applicationContext)
}
